package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.whatsapp.CanalWhatsAppRepository;

/**
 * Retención configurable de la conversación de WhatsApp (V2-DA-67; 13-v2-10 §8). Cada ejecución trabaja
 * por negocio y en lotes, con transacciones cortas y {@code SKIP LOCKED}:
 * <ol>
 *   <li>borra las conversaciones sin ficha cuya última entrada (o creación) supera
 *   {@code retencion.conversaciones-sin-ficha} y que no tienen mensajes pendientes, con sus mensajes;
 *   las políticas de V116 protegen las fichas identificables y los mensajes pendientes; la excepción de
 *   anonimización no se usa en esta fase;</li>
 *   <li>si {@code retencion.cuerpo-eventos} está configurado (no lo está hasta P2-06), vacía el cuerpo de
 *   los eventos entrantes terminados más antiguos que ese plazo, conservando la fila para la idempotencia.</li>
 *   <li>retira el contenido terminado de conversaciones con ficha cuando se configura su plazo;</li>
 *   <li>caduca operaciones vencidas y purga las terminadas con plazo configurado, tomando primero la conversación.</li>
 *   <li>termina los turnos de IA abandonados a los 15 minutos incluso con la IA desactivada;
 *   elimina llamadas y turnos al purgar su conversación. El agregado de costes sobrevive.</li>
 * </ol>
 * No registra identificadores de WhatsApp ni contenido: solo recuentos por negocio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class RetencionWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(RetencionWhatsApp.class);

    private final EntityManager entidades;
    private final CanalWhatsAppRepository canales;
    private final EjecutorEnNegocio ejecutor;
    private final TransactionTemplate transaccion;
    private final ConversacionPropiedades propiedades;
    private final Clock reloj;

    /**
     * Recibe sus dependencias de Spring.
     * @param entidades contexto de persistencia compartido
     * @param canales negocios con canal de WhatsApp (los únicos que pueden tener conversaciones)
     * @param ejecutor abre el contexto de cada negocio
     * @param gestor gestor de transacciones de la aplicación
     * @param propiedades plazos y tamaño de lote
     * @param reloj reloj de la aplicación
     */
    public RetencionWhatsApp(EntityManager entidades, CanalWhatsAppRepository canales, EjecutorEnNegocio ejecutor,
            PlatformTransactionManager gestor, ConversacionPropiedades propiedades, Clock reloj) {
        this.entidades = entidades;
        this.canales = canales;
        this.ejecutor = ejecutor;
        this.transaccion = new TransactionTemplate(gestor);
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * Ejecuta las fases configuradas con una transacción por negocio y fase. Un fallo de una fase local
     * no impide las restantes ni los demás negocios; el cuerpo de eventos se procesa aparte, sin RLS.
     * @return conversaciones borradas, contenidos retirados, operaciones borradas, cuerpos vaciados y turnos abandonados
     * @throws IllegalStateException si se invoca dentro de una transacción exterior
     */
    public int purgar() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La retención exige invocarse fuera de una transacción.");
        }
        int total = 0;
        List<Long> negocios = transaccion.execute(tx -> canales.listarNegociosConCanal());
        for (long negocioId : negocios) {
            total += fase(negocioId, this::terminarTurnosIAAbandonados);
            try {
                int borradas = ejecutor.ejecutar(negocioId, () -> transaccion.execute(tx -> purgarConversaciones()));
                if (borradas > 0) LOG.info("Retención WhatsApp del negocio {}: {} conversaciones sin ficha.", negocioId, borradas);
                total += borradas;
            } catch (RuntimeException fallo) {
                LOG.warn("Retención WhatsApp del negocio {}: fallo; se reintenta en la siguiente pasada.", negocioId);
            }
            if (propiedades.retencion().contenidoMensajes() != null) {
                total += fase(negocioId, this::retirarContenido);
            }
            if (propiedades.retencion().operacionesTerminadas() != null) {
                total += fase(negocioId, this::purgarOperaciones);
            }
        }
        if (propiedades.retencion().cuerpoEventos() != null) {
            int vaciados = transaccion.execute(tx -> vaciarCuerposEventos());
            if (vaciados > 0) LOG.info("Retención WhatsApp: {} cuerpos de eventos vaciados.", vaciados);
            total += vaciados;
        }
        return total;
    }

    private int fase(long negocioId, java.util.function.IntSupplier accion) {
        try {
            return ejecutor.ejecutar(negocioId, () -> transaccion.execute(tx -> accion.getAsInt()));
        } catch (RuntimeException fallo) {
            LOG.warn("Retención WhatsApp del negocio {}: fallo de fase; se reintentará.", negocioId);
            return 0;
        }
    }

    /**
     * Termina únicamente turnos abandonados, con una cadencia independiente de la purga general.
     * Se ejecuta también con IA desactivada y no emite ninguna respuesta al cliente.
     * @return turnos terminados en los lotes de esta pasada
     * @throws IllegalStateException si existe una transacción exterior
     */
    public int abandonarTurnosIA() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("La retención exige invocarse fuera de una transacción.");
        int total = 0;
        for (long negocioId : transaccion.execute(tx -> canales.listarNegociosConCanal()))
            total += fase(negocioId, this::terminarTurnosIAAbandonados);
        return total;
    }

    private int terminarTurnosIAAbandonados() {
        var limite = reloj.instant().minus(java.time.Duration.ofMinutes(15));
        var ids = entidades.createNativeQuery("""
                SELECT c.id FROM conversacion c WHERE EXISTS (
                    SELECT 1 FROM turno_ia t WHERE t.negocio_id=c.negocio_id AND t.conversacion_id=c.id
                        AND t.estado='PENDIENTE' AND t.creado_en < :limite)
                ORDER BY c.id LIMIT :lote FOR UPDATE OF c SKIP LOCKED
                """, Long.class).setParameter("limite", limite).setParameter("lote", propiedades.retencion().lote()).getResultList();
        if (ids.isEmpty()) return 0;
        return entidades.createNativeQuery("""
                UPDATE turno_ia SET estado='FALLIDO', motivo='ABANDONADO', texto=NULL, actualizado_en=:ahora
                WHERE conversacion_id IN (:ids) AND estado='PENDIENTE' AND creado_en < :limite
                """).setParameter("ids", ids).setParameter("limite", limite).setParameter("ahora", reloj.instant()).executeUpdate();
    }

    int retirarContenido() {
        var limite = reloj.instant().minus(propiedades.retencion().contenidoMensajes());
        // Solo se bloquea el mensaje: no se escribe ninguna FK ni se toma después la conversación.
        return entidades.createNativeQuery("""
                WITH candidatos AS (
                    SELECT m.id FROM mensaje_whatsapp m JOIN conversacion c
                        ON c.negocio_id=m.negocio_id AND c.id=m.conversacion_id
                    WHERE c.cliente_id IS NOT NULL AND m.estado <> 'PENDIENTE'
                        AND m.contenido <> '{"retirado":true}'::jsonb AND m.creado_en < :limite
                    ORDER BY m.id LIMIT :lote FOR UPDATE OF m SKIP LOCKED
                )
                UPDATE mensaje_whatsapp m SET contenido='{"retirado":true}'::jsonb
                FROM candidatos c WHERE m.id=c.id AND m.estado <> 'PENDIENTE'
                    AND m.contenido <> '{"retirado":true}'::jsonb
                """).setParameter("limite", limite).setParameter("lote", propiedades.retencion().lote()).executeUpdate();
    }

    int purgarOperaciones() {
        var ahora = reloj.instant();
        var limite = ahora.minus(propiedades.retencion().operacionesTerminadas());
        var conversaciones = entidades.createNativeQuery("""
                SELECT c.id FROM conversacion c WHERE EXISTS (
                    SELECT 1 FROM operacion_pendiente o WHERE o.negocio_id=c.negocio_id AND o.conversacion_id=c.id
                        AND ((o.estado='VIGENTE' AND o.expira_en <= :ahora)
                            OR (o.estado <> 'VIGENTE' AND o.actualizado_en < :limite)))
                ORDER BY c.id LIMIT :lote FOR UPDATE OF c SKIP LOCKED
                """, Long.class).setParameter("ahora", ahora).setParameter("limite", limite)
                .setParameter("lote", propiedades.retencion().lote()).getResultList();
        if (conversaciones.isEmpty()) return 0;
        // Orden conversación → operación, compartido con la entrega y la anonimización.
        var operaciones = entidades.createNativeQuery("""
                SELECT id FROM operacion_pendiente WHERE conversacion_id IN (:ids)
                    AND ((estado='VIGENTE' AND expira_en <= :ahora) OR (estado <> 'VIGENTE' AND actualizado_en < :limite))
                ORDER BY id LIMIT :lote FOR UPDATE SKIP LOCKED
                """, Long.class).setParameter("ids", conversaciones).setParameter("ahora", ahora)
                .setParameter("limite", limite).setParameter("lote", propiedades.retencion().lote()).getResultList();
        if (operaciones.isEmpty()) return 0;
        entidades.createNativeQuery("""
                UPDATE operacion_pendiente SET estado='CADUCADA', actualizado_en=:ahora
                WHERE id IN (:ids) AND estado='VIGENTE' AND expira_en <= :ahora
                """).setParameter("ids", operaciones).setParameter("ahora", ahora).executeUpdate();
        return entidades.createNativeQuery("""
                DELETE FROM operacion_pendiente WHERE id IN (:ids) AND estado <> 'VIGENTE' AND actualizado_en < :limite
                """).setParameter("ids", operaciones).setParameter("limite", limite).executeUpdate();
    }

    /**
     * Borra un lote de conversaciones sin ficha vencidas del negocio actual, con sus mensajes.
     * @return conversaciones borradas
     */
    int purgarConversaciones() {
        var limite = reloj.instant().minus(propiedades.retencion().conversacionesSinFicha());
        @SuppressWarnings("unchecked")
        List<Number> ids = entidades.createNativeQuery("""
                SELECT c.id FROM conversacion c
                WHERE c.cliente_id IS NULL AND COALESCE(c.ultima_entrada_en, c.creado_en) < :limite
                  AND NOT EXISTS (SELECT 1 FROM mensaje_whatsapp m
                                  WHERE m.conversacion_id = c.id AND m.negocio_id = c.negocio_id
                                    AND (m.estado = 'PENDIENTE' OR m.reserva_id IS NOT NULL))
                ORDER BY c.id LIMIT :lote FOR UPDATE OF c SKIP LOCKED
                """).setParameter("limite", limite).setParameter("lote", propiedades.retencion().lote()).getResultList();
        if (ids.isEmpty()) return 0;
        var lista = ids.stream().map(Number::longValue).toList();
        entidades.createNativeQuery("""
                UPDATE turno_ia SET estado='DESCARTADO', texto=NULL, motivo='RETENCION', actualizado_en=:ahora
                WHERE conversacion_id IN (:ids) AND estado='PENDIENTE'
                """).setParameter("ids", lista).setParameter("ahora", reloj.instant()).executeUpdate();
        entidades.createNativeQuery("DELETE FROM llamada_ia WHERE conversacion_id IN (:ids)")
                .setParameter("ids", lista).executeUpdate();
        entidades.createNativeQuery("DELETE FROM turno_ia WHERE conversacion_id IN (:ids)")
                .setParameter("ids", lista).executeUpdate();
        entidades.createNativeQuery("DELETE FROM notificacion WHERE conversacion_id IN (:ids)")
                .setParameter("ids", lista).executeUpdate();
        entidades.createNativeQuery("DELETE FROM mensaje_whatsapp WHERE conversacion_id IN (:ids)")
                .setParameter("ids", lista).executeUpdate();
        return entidades.createNativeQuery("DELETE FROM conversacion WHERE id IN (:ids) AND cliente_id IS NULL")
                .setParameter("ids", lista).executeUpdate();
    }

    /**
     * Vacía el cuerpo de un lote de eventos entrantes terminados más antiguos que el plazo configurado.
     * {@code evento_whatsapp_entrante} es de plataforma (sin RLS): no necesita contexto de negocio.
     * @return cuerpos vaciados
     */
    int vaciarCuerposEventos() {
        var limite = reloj.instant().minus(propiedades.retencion().cuerpoEventos());
        return entidades.createNativeQuery("""
                UPDATE evento_whatsapp_entrante SET cuerpo = '{}'::jsonb
                WHERE id IN (SELECT id FROM evento_whatsapp_entrante
                             WHERE estado <> 'PENDIENTE' AND cuerpo <> '{}'::jsonb
                               AND COALESCE(procesado_en, recibido_en) < :limite
                             ORDER BY id LIMIT :lote FOR UPDATE SKIP LOCKED)
                """).setParameter("limite", limite).setParameter("lote", propiedades.retencion().lote()).executeUpdate();
    }
}
