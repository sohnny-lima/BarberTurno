package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Acceso mínimo a la cola de plataforma, sin CRUD heredado. El servicio delimita cada transacción;
 * todas las terminaciones exigen el estado pendiente y la ficha del reclamo (V2-DA-52).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
class ColaEventosWhatsApp {
    private final EntityManager entidades;

    ColaEventosWhatsApp(EntityManager entidades) { this.entidades = entidades; }

    List<EventoEntrante> reclamar(Instant ahora, Instant vencimiento, int lote) {
        return entidades.createNativeQuery("""
                UPDATE evento_whatsapp_entrante SET intentos = intentos + 1, proximo_intento = :vencimiento
                WHERE id IN (SELECT id FROM evento_whatsapp_entrante
                    WHERE estado = 'PENDIENTE' AND proximo_intento <= :ahora
                    ORDER BY proximo_intento, id LIMIT :lote FOR UPDATE SKIP LOCKED)
                RETURNING *
                """, EventoEntrante.class)
                .setParameter("ahora", ahora).setParameter("vencimiento", vencimiento)
                .setParameter("lote", lote).getResultList();
    }

    Optional<EventoEntrante> bloquear(long eventoId, int ficha) {
        var filas = entidades.createNativeQuery("""
                SELECT * FROM evento_whatsapp_entrante
                WHERE id = :id AND estado = 'PENDIENTE' AND intentos = :ficha FOR UPDATE
                """, EventoEntrante.class).setParameter("id", eventoId).setParameter("ficha", ficha)
                .getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of((EventoEntrante) filas.getFirst());
    }

    boolean descartar(long eventoId, int ficha, Long negocioId, MotivoEvento motivo, Instant ahora) {
        return terminar(eventoId, ficha, negocioId, "DESCARTADO", motivo.name(), ahora);
    }

    boolean procesar(long eventoId, int ficha, long negocioId, Instant ahora) {
        return terminar(eventoId, ficha, negocioId, "PROCESADO", null, ahora);
    }

    private boolean terminar(long eventoId, int ficha, Long negocioId, String estado, String motivo, Instant ahora) {
        return entidades.createNativeQuery("""
                UPDATE evento_whatsapp_entrante
                SET estado = :estado, motivo = :motivo, negocio_id = :negocio, procesado_en = :ahora
                WHERE id = :id AND estado = 'PENDIENTE' AND intentos = :ficha
                """).setParameter("estado", estado).setParameter("motivo", motivo)
                .setParameter("negocio", negocioId).setParameter("ahora", ahora)
                .setParameter("id", eventoId).setParameter("ficha", ficha).executeUpdate() == 1;
    }

    boolean fallar(long eventoId, int ficha, Instant siguiente, Instant ahora, boolean agotado) {
        return entidades.createNativeQuery("""
                UPDATE evento_whatsapp_entrante SET proximo_intento = :siguiente,
                    estado = :estado, motivo = :motivo, procesado_en = :procesado
                WHERE id = :id AND estado = 'PENDIENTE' AND intentos = :ficha
                """).setParameter("siguiente", siguiente).setParameter("estado", agotado ? "ERROR" : "PENDIENTE")
                .setParameter("motivo", agotado ? MotivoEvento.FALLO_PROCESAMIENTO.name() : null)
                .setParameter("procesado", agotado ? ahora : null)
                .setParameter("id", eventoId).setParameter("ficha", ficha).executeUpdate() == 1;
    }
}
