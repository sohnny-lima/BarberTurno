package pe.barberturno.notifications.dto;

import java.time.OffsetDateTime;
import pe.barberturno.audit.AccionAuditoria;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.Notificacion;

/**
 * Aviso RF-16 del destinatario autenticado sin exponer su identidad ni datos de sesión.
 * @param id identidad del aviso para marcar su lectura
 * @param reservaId reserva que originó el aviso
 * @param tipo acción RN-15 que produjo el aviso
 * @param mensaje texto público del cambio
 * @param leida indica si el destinatario lo ha leído
 * @param creadoEn fecha del aviso con desfase de Lima
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record NotificacionDto(long id, long reservaId, AccionAuditoria tipo, String mensaje,
        boolean leida, OffsetDateTime creadoEn) {
    /**
     * Proyecta un aviso propio mientras su reserva permanece accesible en la transacción.
     * @param aviso entidad autorizada para el destinatario actual
     * @return representación pública con fecha de Lima
     */
    public static NotificacionDto desde(Notificacion aviso) {
        return new NotificacionDto(aviso.getId(), aviso.getReserva().getId(), aviso.getTipo(),
                aviso.getMensaje(), aviso.isLeida(), TiempoNegocio.aLima(aviso.getCreadoEn()));
    }
}
