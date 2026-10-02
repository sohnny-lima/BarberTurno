package pe.barberturno.auth.dto;

import jakarta.validation.constraints.*;

/**
 * Entrada de registro público RF-01; solo crea clientes y exige consentimiento de privacidad (MJ-12).
 * @param nombre nombre obligatorio de hasta 100 caracteres
 * @param correo correo obligatorio válido de hasta 254 caracteres, normalizado según RN-24
 * @param telefono teléfono obligatorio de exactamente nueve dígitos
 * @param password contraseña no vacía; el servicio verifica RN-25 y límite UTF-8
 * @param aceptaPrivacidad consentimiento obligatorio no nulo y true
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record RegistroDto(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Pattern(regexp = "[0-9]{9}", message = "Debe tener nueve dígitos.") String telefono,
        @NotBlank String password,
        @NotNull @AssertTrue(message = "Debe aceptar el aviso de privacidad.") Boolean aceptaPrivacidad) { }