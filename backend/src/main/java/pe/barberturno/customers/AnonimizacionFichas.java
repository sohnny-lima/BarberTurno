package pe.barberturno.customers;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.AutorizacionGestionLocal;
import pe.barberturno.common.security.UsuarioAutenticado;

/**
 * Anonimiza fichas sin cuenta conservando reservas y auditoría (V2-DA-75).
 * Bloquea conversación, operaciones y ficha, revalida y marca antes de borrar los datos asociados.
 * Los cuerpos crudos de eventos mantienen su retención propia; nunca se toman sus bloqueos aquí.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class AnonimizacionFichas {
    private final EntityManager entidades;
    private final AutorizacionGestionLocal autorizacion;
    private final Clock reloj;

    AnonimizacionFichas(EntityManager entidades, AutorizacionGestionLocal autorizacion, Clock reloj) {
        this.entidades = entidades; this.autorizacion = autorizacion; this.reloj = reloj;
    }

    /**
     * Retira la identidad de una ficha local sin reservas activas ni mensajes pendientes, idempotentemente.
     * @param fichaId ficha del negocio de la sesión
     * @throws NegocioException si falta propiedad, no es local, tiene cuenta, reservas activas o mensajes pendientes
     */
    @Transactional
    public void anonimizar(long fichaId) {
        var sesion = SecurityContextHolder.getContext().getAuthentication();
        if (sesion == null || !(sesion.getPrincipal() instanceof UsuarioAutenticado actor)) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Solo el propietario puede anonimizar fichas.");
        }
        long negocioId = autorizacion.exigir(actor.id(), true);
        var conversaciones = entidades.createNativeQuery("""
                SELECT id FROM conversacion WHERE cliente_id=:ficha AND negocio_id=:negocio FOR UPDATE
                """, Long.class).setParameter("ficha", fichaId).setParameter("negocio", negocioId).getResultList();
        // Es posible que una entrega cree/enlace una conversación mientras se esperaba la ficha.
        // La ficha se relee después de bloquearla; si apareció una conversación, se revierte para reintentar
        // sin invertir el orden de bloqueos.
        if (!conversaciones.isEmpty()) {
            entidades.createNativeQuery("""
                    SELECT id FROM operacion_pendiente WHERE conversacion_id IN (:ids) ORDER BY id FOR UPDATE
                    """, Long.class).setParameter("ids", conversaciones).getResultList();
        }
        var fichas = entidades.createNativeQuery("""
                SELECT usuario_id, anonimizada_en FROM cliente WHERE id=:id AND negocio_id=:negocio FOR UPDATE
                """, Object[].class).setParameter("id", fichaId).setParameter("negocio", negocioId).getResultList();
        autorizacion.exigir(actor.id(), true);
        if (fichas.isEmpty()) throw new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró la ficha.");
        Object[] ficha = (Object[]) fichas.getFirst();
        if (ficha[1] != null) return;
        if (ficha[0] != null) throw new NegocioException(ErrorCodigo.CONFLICTO, "La ficha tiene una cuenta web vinculada.");
        var actuales = entidades.createNativeQuery(
                "SELECT id FROM conversacion WHERE cliente_id=:id AND negocio_id=:negocio", Long.class)
                .setParameter("id", fichaId).setParameter("negocio", negocioId).getResultList();
        if (!actuales.equals(conversaciones)) throw new NegocioException(ErrorCodigo.CONFLICTO,
                "La conversación cambió mientras se anonimizaba. Inténtelo de nuevo.");
        if (!entidades.createNativeQuery("""
                SELECT id FROM reserva WHERE cliente_id=:id AND negocio_id=:negocio
                    AND estado IN ('PENDIENTE','CONFIRMADA','EN_ATENCION') LIMIT 1
                """, Long.class).setParameter("id", fichaId).setParameter("negocio", negocioId).getResultList().isEmpty()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO_CON_RESERVAS, "La ficha tiene reservas activas.");
        }
        if (!conversaciones.isEmpty() && !entidades.createNativeQuery("""
                SELECT id FROM mensaje_whatsapp WHERE conversacion_id IN (:ids) AND estado='PENDIENTE' LIMIT 1
                """, Long.class).setParameter("ids", conversaciones).getResultList().isEmpty()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "Hay mensajes pendientes; reintente más tarde.");
        }
        entidades.createNativeQuery("""
                UPDATE cliente SET nombre='Cliente eliminado', wa_id=NULL, telefono=NULL, activo=false,
                    anonimizada_en=:ahora, actualizado_en=:ahora WHERE id=:id AND negocio_id=:negocio
                """).setParameter("id", fichaId).setParameter("negocio", negocioId)
                .setParameter("ahora", reloj.instant()).executeUpdate();
        if (!conversaciones.isEmpty()) {
            for (String tabla : java.util.List.of("notificacion", "operacion_pendiente", "mensaje_whatsapp")) {
                entidades.createNativeQuery("DELETE FROM " + tabla + " WHERE conversacion_id IN (:ids)")
                        .setParameter("ids", conversaciones).executeUpdate();
            }
            entidades.createNativeQuery("DELETE FROM conversacion WHERE id IN (:ids)")
                    .setParameter("ids", conversaciones).executeUpdate();
        }
    }
}
