package pe.barberturno.conversation.ia;

import jakarta.persistence.EntityManager;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.AvisosLimiteIA;

/**
 * Presupuesto serializado por negocio y reservas idempotentes por llamada (V2-DA-79).
 * uso_ia contabiliza desde la reserva el máximo conservador y sobrevive al borrado del detalle.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class LimitesIAService implements LimitesIA {
    private final EntityManager em;
    private final IaPropiedades propiedades;
    private final Clock reloj;
    private final AvisosLimiteIA avisos;
    private final TransactionTemplate tx;

    LimitesIAService(EntityManager em, IaPropiedades propiedades, Clock reloj, AvisosLimiteIA avisos,
            PlatformTransactionManager gestor) {
        this.em = em; this.propiedades = propiedades; this.reloj = reloj; this.avisos = avisos;
        this.tx = new TransactionTemplate(gestor);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DisponibilidadIA disponibilidad(long conversacionId) {
        if (!propiedades.configurada() || !precioValido()) return DisponibilidadIA.DESACTIVADA;
        var filas = em.createNativeQuery("""
                SELECT n.ia_habilitada, n.ia_entradas_minuto, n.ia_llamadas_dia_conversacion,
                    n.ia_presupuesto_mensual_usd, c.minuto_entradas, c.entradas_minuto
                FROM negocio n JOIN conversacion c ON c.negocio_id=n.id
                WHERE c.id=:id AND n.id=app_negocio_actual()
                """, Object[].class).setParameter("id", conversacionId).getResultList();
        if (filas.isEmpty()) return DisponibilidadIA.DESACTIVADA;
        Object[] fila = (Object[]) filas.getFirst();
        if (!Boolean.TRUE.equals(fila[0])) return DisponibilidadIA.DESACTIVADA;
        Instant minuto = fila[4] instanceof java.time.OffsetDateTime fecha ? fecha.toInstant()
                : fila[4] instanceof Instant instante ? instante
                : fila[4] instanceof java.sql.Timestamp sello ? sello.toInstant() : null;
        if (reloj.instant().truncatedTo(ChronoUnit.MINUTES).equals(minuto)
                && ((Number) fila[5]).intValue() > entero(fila[1], propiedades.limites().entradasMinuto()))
            return DisponibilidadIA.LIMITE_MINUTO;
        long llamadas = ((Number) em.createNativeQuery("""
                SELECT count(*) FROM llamada_ia WHERE conversacion_id=:id AND dia=:dia
                """).setParameter("id", conversacionId).setParameter("dia", dia()).getSingleResult()).longValue();
        if (llamadas >= entero(fila[2], propiedades.limites().llamadasDia())) return DisponibilidadIA.LIMITE_DIA;
        if (gastoMensual() >= presupuesto(fila[3])) return DisponibilidadIA.PRESUPUESTO;
        return DisponibilidadIA.DISPONIBLE;
    }

    Optional<ReservaLlamada> reservar(long turnoId, int numero, PeticionModelo peticion) {
        return tx.execute(estado -> {
            bloquearPresupuesto();
            var turnos = em.createNativeQuery("SELECT conversacion_id FROM turno_ia WHERE id=:id AND estado='PENDIENTE'", Long.class)
                    .setParameter("id", turnoId).getResultList();
            if (turnos.isEmpty()) return Optional.empty();
            long conversacionId = ((Number) turnos.getFirst()).longValue();
            if (disponibilidad(conversacionId) != DisponibilidadIA.DISPONIBLE) {
                if (disponibilidad(conversacionId) == DisponibilidadIA.PRESUPUESTO) avisos.presupuesto(conversacionId);
                return Optional.empty();
            }
            // Un token puede representar un solo byte. Se añade margen para el formato del proveedor;
            // la caché se reserva al mayor precio de entrada, nunca se presume un acierto de caché.
            long bytes = entradaMaxima(peticion);
            var precio = propiedades.precios().get(propiedades.modelo());
            long coste = micro(precio.entrada().max(precio.cacheEscritura()).max(precio.cacheLectura())
                    .multiply(BigDecimal.valueOf(bytes)).add(precio.salida().multiply(BigDecimal.valueOf(propiedades.maxTokens()))));
            Object limite = em.createNativeQuery("SELECT ia_presupuesto_mensual_usd FROM negocio WHERE id=app_negocio_actual()")
                    .getSingleResult();
            if (BigDecimal.valueOf(gastoMensual()).add(BigDecimal.valueOf(coste)).compareTo(BigDecimal.valueOf(presupuesto(limite))) > 0) {
                avisos.presupuesto(conversacionId);
                return Optional.empty();
            }
            int insertadas = em.createNativeQuery("""
                    INSERT INTO llamada_ia(turno_id, numero, conversacion_id, dia, modelo, coste_reservado_micro_usd)
                    VALUES (:turno, :numero, :conversacion, :dia, :modelo, :coste)
                    ON CONFLICT (turno_id, numero) DO NOTHING
                    """).setParameter("turno", turnoId).setParameter("numero", numero)
                    .setParameter("conversacion", conversacionId).setParameter("dia", dia())
                    .setParameter("modelo", propiedades.modelo()).setParameter("coste", coste).executeUpdate();
            if (insertadas == 0) return Optional.empty(); // Nunca repite un HTTP tras una reserva confirmada.
            em.createNativeQuery("""
                    INSERT INTO uso_ia(negocio_id, dia, llamadas, coste_micro_usd)
                    VALUES (app_negocio_actual(), :dia, 1, :coste)
                    ON CONFLICT (negocio_id, dia) DO UPDATE SET llamadas=uso_ia.llamadas+1,
                        coste_micro_usd=uso_ia.coste_micro_usd+EXCLUDED.coste_micro_usd
                    """).setParameter("dia", dia()).setParameter("coste", coste).executeUpdate();
            if (gastoMensual() >= presupuesto(limite)) avisos.presupuesto(conversacionId);
            return Optional.of(new ReservaLlamada(turnoId, numero));
        });
    }

    void liquidar(ReservaLlamada reserva, UsoModelo uso) {
        tx.executeWithoutResult(estado -> {
            bloquearPresupuesto();
            var filas = em.createNativeQuery("""
                    SELECT dia, modelo, coste_reservado_micro_usd FROM llamada_ia
                    WHERE turno_id=:id AND numero=:numero AND estado='RESERVADA' FOR UPDATE
                    """, Object[].class).setParameter("id", reserva.turnoId()).setParameter("numero", reserva.numero()).getResultList();
            if (filas.isEmpty()) return; // Purga o liquidación previa: no recrear filas.
            Object[] fila = (Object[]) filas.getFirst();
            var precio = propiedades.precios().get(fila[1].toString());
            if (precio == null || uso.entrada() < 0 || uso.salida() < 0 || uso.cacheLeida() < 0 || uso.cacheEscrita() < 0) return;
            // Anthropic informa input_tokens SIN los de caché. Cada categoría se suma exactamente una vez.
            long coste = micro(precio.entrada().multiply(BigDecimal.valueOf(uso.entrada()))
                    .add(precio.salida().multiply(BigDecimal.valueOf(uso.salida())))
                    .add(precio.cacheLectura().multiply(BigDecimal.valueOf(uso.cacheLeida())))
                    .add(precio.cacheEscritura().multiply(BigDecimal.valueOf(uso.cacheEscrita()))));
            em.createNativeQuery("""
                    UPDATE llamada_ia SET estado='LIQUIDADA', coste_micro_usd=:coste, entrada=:entrada,
                        salida=:salida, cache_leida=:lectura, cache_escrita=:escritura
                    WHERE turno_id=:id AND numero=:numero AND estado='RESERVADA'
                    """).setParameter("id", reserva.turnoId()).setParameter("numero", reserva.numero())
                    .setParameter("coste", coste).setParameter("entrada", uso.entrada()).setParameter("salida", uso.salida())
                    .setParameter("lectura", uso.cacheLeida()).setParameter("escritura", uso.cacheEscrita()).executeUpdate();
            em.createNativeQuery("""
                    UPDATE uso_ia SET coste_micro_usd=coste_micro_usd+:delta, entrada=entrada+:entrada,
                        salida=salida+:salida, cache_leida=cache_leida+:lectura, cache_escrita=cache_escrita+:escritura
                    WHERE negocio_id=app_negocio_actual() AND dia=:dia
                    """).setParameter("dia", fila[0]).setParameter("delta", coste - ((Number) fila[2]).longValue())
                    .setParameter("entrada", uso.entrada()).setParameter("salida", uso.salida())
                    .setParameter("lectura", uso.cacheLeida()).setParameter("escritura", uso.cacheEscrita()).executeUpdate();
        });
    }

    // Solo para una llamada que se sabe NO iniciada. Una caída conserva siempre el coste reservado.
    void liberar(ReservaLlamada reserva) {
        liquidar(reserva, new UsoModelo(propiedades.modelo(), 0, 0, 0, 0, 0));
    }

    private void bloquearPresupuesto() {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended('barberturno.ia:' || app_negocio_actual(), 0))")
                .getSingleResult();
    }

    private boolean precioValido() {
        var p = propiedades.precios().get(propiedades.modelo());
        return p != null && p.entrada().signum() >= 0 && p.salida().signum() >= 0
                && p.cacheLectura().signum() >= 0 && p.cacheEscritura().signum() >= 0;
    }

    private LocalDate dia() { return TiempoNegocio.fechaLima(reloj.instant()); }
    private long entradaMaxima(PeticionModelo p) {
        long bytes = bytes(p.instrucciones()) + bytes(p.contexto()) + 8192;
        for (var h : p.herramientas()) bytes += bytes(h.esquemaJson()) + bytes(h.descripcion());
        for (var mensaje : p.mensajes()) bytes += switch (mensaje) {
            case MensajeModelo.DelCliente(var texto) -> bytes(texto);
            case MensajeModelo.DelAsistente asistente -> bytes(asistente.texto().orElse(""))
                    + asistente.llamadas().stream().mapToLong(l -> bytes(l.argumentosJson()) + bytes(l.id()) + 256).sum()
                    // La continuación es opaca: reserva conservadora para pensamiento y firmas por respuesta.
                    + propiedades.maxTokens() * 32L;
            case MensajeModelo.ResultadosHerramientas(var resultados) -> resultados.stream()
                    .mapToLong(r -> bytes(r.json()) + bytes(r.llamadaId()) + 256).sum();
        };
        return bytes;
    }
    private static long bytes(String valor) { return valor.getBytes(StandardCharsets.UTF_8).length; }
    private static int entero(Object valor, int defecto) { return valor == null ? defecto : ((Number) valor).intValue(); }
    private long presupuesto(Object valor) {
        return ((BigDecimal) (valor == null ? propiedades.limites().presupuestoMensualUsd() : valor))
                .movePointRight(6).setScale(0, RoundingMode.CEILING).longValueExact();
    }
    private long gastoMensual() {
        return ((Number) em.createNativeQuery("""
                SELECT COALESCE(sum(coste_micro_usd),0) FROM uso_ia
                WHERE negocio_id=app_negocio_actual() AND dia >= :desde AND dia < :hasta
                """).setParameter("desde", dia().withDayOfMonth(1)).setParameter("hasta", dia().withDayOfMonth(1).plusMonths(1))
                .getSingleResult()).longValue();
    }
    // USD/millón de tokens × tokens = micro-USD; no se usa double ni se redondea a la baja.
    private static long micro(BigDecimal valor) { return valor.setScale(0, RoundingMode.CEILING).longValueExact(); }

    record ReservaLlamada(long turnoId, int numero) { }
}
