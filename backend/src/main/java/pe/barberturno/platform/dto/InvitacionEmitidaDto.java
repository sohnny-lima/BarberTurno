package pe.barberturno.platform.dto;



/**
 * Secreto entregado exclusivamente al emitir, sin conservación posterior.
 * @param invitacion metadatos de la pendiente
 * @param enlaceInvitacion ruta de aceptación que el emisor comparte una vez
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record InvitacionEmitidaDto(InvitacionDto invitacion, String enlaceInvitacion) {
    /** @return descripción sin datos personales ni capacidades secretas */
    @Override public String toString() { return "InvitacionEmitidaDto[datos omitidos]"; }
}
