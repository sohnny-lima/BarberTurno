package pe.barberturno.auth.dto;

import jakarta.validation.constraints.*;

/**
 * Credenciales de acceso RF-02; su contenido nunca debe aparecer en logs ni respuestas.
 * @param correo correo obligatorio válido de hasta 254 caracteres
 * @param password contraseña no vacía, verificada sin registrar su valor
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record LoginDto(
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank String password) { }