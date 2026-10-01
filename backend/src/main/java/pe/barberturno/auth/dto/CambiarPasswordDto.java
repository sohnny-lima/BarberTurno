package pe.barberturno.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Contraseña actual y propuesta; la política completa la evalúa el servicio. */
public record CambiarPasswordDto(
        @NotBlank(message = "La contraseña actual es obligatoria.") String passwordActual,
        String passwordNueva) { }