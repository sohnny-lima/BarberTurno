package pe.barberturno.auth.dto;

import jakarta.validation.constraints.*;

/** Datos del registro público de clientes; nunca admite un rol. */
public record RegistroDto(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Pattern(regexp = "[0-9]{9}", message = "Debe tener nueve dígitos.") String telefono,
        @NotBlank String password,
        @NotNull @AssertTrue(message = "Debe aceptar el aviso de privacidad.") Boolean aceptaPrivacidad) { }