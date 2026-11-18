package pe.barberturno.platform.dto;

import jakarta.validation.constraints.*;

/**
 * Alta global, sin parámetros de configuración ni permisos controlados por el navegador.
 * @param nombre denominación pública de hasta cien caracteres
 * @param slug identificador canónico único, de hasta cuarenta caracteres
 * @param correoPropietario destinatario de la invitación inicial, normalizado al emitir
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record CrearNegocioDto(@NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 40) @Pattern(regexp = "[a-z0-9](-?[a-z0-9])*") String slug,
        @NotBlank @Email @Size(max = 254) String correoPropietario) {
    /** @return descripción que omite el correo recibido */
    @Override public String toString() { return "CrearNegocioDto[datos omitidos]"; }
}
