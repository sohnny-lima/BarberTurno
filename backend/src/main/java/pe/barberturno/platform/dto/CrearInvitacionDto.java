package pe.barberturno.platform.dto;

import jakarta.validation.constraints.*;
import pe.barberturno.platform.RolNegocio;

/**
 * Emisión de una acreditación local por personal autorizado.
 * @param correo destinatario que deberá acreditar su propia cuenta
 * @param rol permisos concedidos al aceptar
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record CrearInvitacionDto(@NotBlank @Email @Size(max = 254) String correo, @NotNull RolNegocio rol) {
    /** @return descripción sin datos personales ni capacidades secretas */
    @Override public String toString() { return "CrearInvitacionDto[datos omitidos]"; }
}
