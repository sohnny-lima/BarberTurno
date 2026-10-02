package pe.barberturno.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Entrada de cambio RF-15; el servicio verifica la credencial actual y la política RN-25 de la propuesta.
 * @param passwordActual contraseña actual no vacía verificada bajo bloqueo de usuario
 * @param passwordNueva propuesta; puede ser nula en el DTO y se rechaza por RN-25
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CambiarPasswordDto(
        @NotBlank(message = "La contraseña actual es obligatoria.") String passwordActual,
        String passwordNueva) { }