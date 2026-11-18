package pe.barberturno.platform.dto;

import jakarta.validation.constraints.*;

/**
 * Datos de la cuenta acreditada por una invitación; el correo y el rol salen de la capacidad (V2-DA-47).
 * @param nombre nombre visible obligatorio de hasta cien caracteres
 * @param telefono teléfono obligatorio de nueve dígitos
 * @param password credencial obligatoria sujeta a RN-25 en el servicio
 * @param aceptaPrivacidad consentimiento obligatorio y afirmativo
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record RegistroInvitadoDto(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Pattern(regexp = "[0-9]{9}", message = "Debe tener nueve dígitos.") String telefono,
        @NotBlank String password,
        @NotNull @AssertTrue(message = "Debe aceptar el aviso de privacidad.") Boolean aceptaPrivacidad) {
    /** @return descripción sin identidad ni credenciales para impedir su registro accidental */
    @Override public String toString() { return "RegistroInvitadoDto[datos omitidos]"; }
}
