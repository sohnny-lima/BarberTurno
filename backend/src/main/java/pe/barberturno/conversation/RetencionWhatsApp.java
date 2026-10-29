package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.whatsapp.CanalWhatsAppRepository;

/**
 * Retención configurable de la conversación de WhatsApp (V2-DA-67; 13-v2-10 §8). Cada ejecución trabaja
 * por negocio y en lotes, con transacciones cortas y {@code SKIP LOCKED}:
 * <ol>
 *   <li>borra las conversaciones sin ficha cuya última entrada (o creación) supera
 *   {@code retencion.conversaciones-sin-ficha} y que no tienen mensajes pendientes, con sus mensajes;
 *   las políticas restrictivas de V115 impiden borrar cualquier otra fila aunque se intentara;</li>
 *   <li>si {@code retencion.cuerpo-eventos} está configurado (no lo está hasta P2-06), vacía el cuerpo de
 *   los eventos entrantes terminados más antiguos que ese plazo, conservando la fila para la idempotencia.</li>
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
     * Ejecuta una pasada completa: conversaciones sin ficha de cada negocio y, si está configurado, cuerpos
     * de eventos. Un fallo en un negocio no impide los demás.
     * @return conversaciones borradas más cuerpos de eventos vaciados
     */
    public int purgar() {
        int total = 0;
        List<Long> negocios = transaccion.execute(tx -> canales.listarNegociosConCanal());
        for (long negocioId : negocios) {
            try {
                int borradas = ejecutor.ejecutar(negocioId, () -> transaccion.execute(tx -> purgarConversaciones()));
                if (borradas > 0) LOG.info("Retención WhatsApp del negocio {}: {} conversaciones sin ficha.", negocioId, borradas);
                total += borradas;
            } catch (RuntimeException fallo) {
                LOG.warn("Retención WhatsApp del negocio {}: fallo; se reintenta en la siguiente pasada.", negocioId);
            }
        }
        if (propiedades.retencion().cuerpoEventos() != null) {
            int vaciados = transaccion.execute(tx -> vaciarCuerposEventos());
            if (vaciados > 0) LOG.info("Retención WhatsApp: {} cuerpos de eventos vaciados.", vaciados);
            total += vaciados;
        }
        return total;
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
