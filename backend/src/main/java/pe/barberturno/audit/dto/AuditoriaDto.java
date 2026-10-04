package pe.barberturno.audit.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import pe.barberturno.audit.AccionAuditoria;
import pe.barberturno.audit.AuditoriaReserva;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.EstadoReserva;

/**
 * Cambio histórico RF-17 sin identidades internas del actor ni datos de sesión.
 * @param accion operación que produjo el cambio
 * @param actorNombre nombre público del usuario que ejecutó la operación
 * @param creadoEn fecha del cambio con desfase de Lima
 * @param estadoAnterior estado previo, nulo al crear
 * @param estadoNuevo estado resultante
 * @param datosAnteriores instantánea previa, nula al crear
 * @param datosNuevos instantánea resultante
 * @param motivo justificación opcional
 * @param excepcional indica una excepción administrativa RN-08
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record AuditoriaDto(AccionAuditoria accion, String actorNombre, OffsetDateTime creadoEn,
        EstadoReserva estadoAnterior, EstadoReserva estadoNuevo, Map<String, Object> datosAnteriores,
        Map<String, Object> datosNuevos, String motivo, boolean excepcional) {
    /**
     * Proyecta un cambio autorizado mientras su actor permanece accesible en la transacción.
     * @param auditoria entrada histórica con actor cargado
     * @return datos del contrato RF-17 con fecha de Lima
     */
    public static AuditoriaDto desde(AuditoriaReserva auditoria) {
        return new AuditoriaDto(auditoria.getAccion(), auditoria.getActor().getNombre(),
                TiempoNegocio.aLima(auditoria.getCreadoEn()), auditoria.getEstadoAnterior(),
                auditoria.getEstadoNuevo(), auditoria.getDatosAnteriores(), auditoria.getDatosNuevos(),
                auditoria.getMotivo(), auditoria.isExcepcional());
    }
}
