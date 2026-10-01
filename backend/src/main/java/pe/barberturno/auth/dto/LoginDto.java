package pe.barberturno.auth.dto;

import jakarta.validation.constraints.*;

/** Credenciales recibidas; no deben registrarse en logs. */
public record LoginDto(
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank String password) { }