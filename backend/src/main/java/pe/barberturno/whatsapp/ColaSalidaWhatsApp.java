package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * SQL de la outbox bajo RLS, sin CRUD heredado. La ficha y el estado observado condicionan cada cierre.
 * Los servicios delimitan las transacciones y bloquean la fila antes de modificarla.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Repository
class ColaSalidaWhatsApp {
    private final EntityManager entidades;

    ColaSalidaWhatsApp(EntityManager entidades) { this.entidades = entidades; }

    Optional<Reclamo> reclamar(Instant ahora, Instant vencimiento) {
        var filas = entidades.createNativeQuery("""
                UPDATE mensaje_whatsapp SET intentos = intentos + 1, proximo_intento = :vence,
                    actualizado_en = :ahora
                WHERE id = (
                    SELECT m.id FROM mensaje_whatsapp m
                    WHERE m.estado = 'PENDIENTE' AND m.direccion = 'SALIENTE'
                      AND m.proximo_intento <= :ahora
                      AND NOT EXISTS (SELECT 1 FROM mensaje_whatsapp anterior
                          WHERE anterior.negocio_id = m.negocio_id
                            AND anterior.conversacion_id = m.conversacion_id
                            AND anterior.estado = 'PENDIENTE' AND anterior.id < m.id)
                    ORDER BY m.proximo_intento, m.id LIMIT 1 FOR UPDATE OF m SKIP LOCKED
                ) RETURNING id, intentos
                """, Object[].class).setParameter("ahora", ahora).setParameter("vence", vencimiento).getResultList();
        if (filas.isEmpty()) return Optional.empty();
        Object[] fila = (Object[]) filas.getFirst();
        return Optional.of(new Reclamo(((Number) fila[0]).longValue(), ((Number) fila[1]).intValue()));
    }

    Optional<MensajeWhatsApp> bloquear(Reclamo reclamo) {
        return primero(entidades.createNativeQuery("""
                SELECT * FROM mensaje_whatsapp WHERE id = :id AND estado = 'PENDIENTE'
                    AND direccion = 'SALIENTE' AND intentos = :ficha FOR UPDATE
                """, MensajeWhatsApp.class).setParameter("id", reclamo.id())
                .setParameter("ficha", reclamo.ficha()).getResultList());
    }

    Optional<MensajeWhatsApp> bloquearPorWamid(String wamid) {
        return primero(entidades.createNativeQuery("""
                SELECT * FROM mensaje_whatsapp WHERE wamid = :wamid AND direccion = 'SALIENTE' FOR UPDATE
                """, MensajeWhatsApp.class).setParameter("wamid", wamid).getResultList());
    }

    Optional<MensajeWhatsApp> bloquearPorConciliacion(long id, String waId) {
        return primero(entidades.createNativeQuery("""
                SELECT m.* FROM mensaje_whatsapp m JOIN conversacion c
                    ON c.negocio_id = m.negocio_id AND c.id = m.conversacion_id
                WHERE m.id = :id AND m.direccion = 'SALIENTE' AND c.wa_id = :wa FOR UPDATE OF m
                """, MensajeWhatsApp.class).setParameter("id", id).setParameter("wa", waId).getResultList());
    }

    Optional<Conversacion> conversacion(long id, long negocioId) {
        var filas = entidades.createNativeQuery("SELECT * FROM conversacion WHERE id = :id AND negocio_id = :negocio",
                Conversacion.class).setParameter("id", id).setParameter("negocio", negocioId).getResultList();
        return filas.isEmpty() ? Optional.empty() : Optional.of((Conversacion) filas.getFirst());
    }

    long insertar(long conversacionId, Long reservaId, TipoMensaje tipo, OrigenMensaje origen,
            String contenido, Instant ahora) {
        return ((Number) entidades.createNativeQuery("""
                INSERT INTO mensaje_whatsapp(conversacion_id, reserva_id, direccion, origen, tipo, contenido,
                    estado, intentos, inciertos, proximo_intento, creado_en, actualizado_en)
                VALUES (:conversacion, :reserva, 'SALIENTE', :origen, :tipo, cast(:contenido as jsonb),
                    'PENDIENTE', 0, 0, :ahora, :ahora, :ahora) RETURNING id
                """, Long.class).setParameter("conversacion", conversacionId).setParameter("reserva", reservaId)
                .setParameter("origen", origen.name()).setParameter("tipo", tipo.name())
                .setParameter("contenido", contenido).setParameter("ahora", ahora).getSingleResult()).longValue();
    }

    boolean preparar(MensajeWhatsApp mensaje, TipoMensaje como, Instant ahora) {
        return entidades.createNativeQuery("""
                UPDATE mensaje_whatsapp SET enviado_como = :como, actualizado_en = :ahora
                WHERE id = :id AND estado = 'PENDIENTE' AND intentos = :ficha
                """).setParameter("como", como.name()).setParameter("ahora", ahora)
                .setParameter("id", mensaje.id()).setParameter("ficha", mensaje.intentos()).executeUpdate() == 1;
    }

    boolean cambiar(MensajeWhatsApp mensaje, EstadoMensaje estado, String wamid, MotivoFallo motivo,
            Integer codigo, int inciertos, Instant siguiente, Instant ahora) {
        return entidades.createNativeQuery("""
                UPDATE mensaje_whatsapp SET estado = :estado, wamid = :wamid, error_codigo = :motivo,
                    codigo_meta = :codigo, inciertos = :inciertos, proximo_intento = :siguiente,
                    actualizado_en = :ahora
                WHERE id = :id AND estado = :anterior AND intentos = :ficha
                """).setParameter("estado", estado.name()).setParameter("wamid", wamid)
                .setParameter("motivo", motivo == null ? null : motivo.name()).setParameter("codigo", codigo)
                .setParameter("inciertos", inciertos).setParameter("siguiente", siguiente).setParameter("ahora", ahora)
                .setParameter("id", mensaje.id()).setParameter("anterior", mensaje.estado().name())
                .setParameter("ficha", mensaje.intentos()).executeUpdate() == 1;
    }

    private static Optional<MensajeWhatsApp> primero(java.util.List<?> filas) {
        return filas.isEmpty() ? Optional.empty() : Optional.of((MensajeWhatsApp) filas.getFirst());
    }

    record Reclamo(long id, int ficha) { }
}
