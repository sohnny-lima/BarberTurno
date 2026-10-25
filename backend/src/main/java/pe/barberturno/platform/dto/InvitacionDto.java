package pe.barberturno.platform.dto;

import java.time.Instant;
import pe.barberturno.platform.RolNegocio;

/**
 * Pendiente visible solo para la gestión del negocio, sin secreto ni hash.
 * @param id identificador para revocar
 * @param correo destinatario normalizado
 * @param rol permisos previstos
 * @param expiraEn límite absoluto de aceptación
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record InvitacionDto(long id, String correo, RolNegocio rol, Instant expiraEn) {
    /** @return descripción sin datos personales ni capacidades secretas */
    @Override public String toString() { return "InvitacionDto[datos omitidos]"; }
}
