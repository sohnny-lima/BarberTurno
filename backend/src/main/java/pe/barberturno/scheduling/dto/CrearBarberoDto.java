package pe.barberturno.scheduling.dto;

import jakarta.validation.constraints.*;

/**
 * Alta RF-05: cuenta BARBERO temporal o vínculo exclusivo a un ADMIN; el servicio valida la variante.
 * @param usuarioId ADMIN activo existente; excluye los campos de cuenta nueva
 * @param nombre nombre de la cuenta nueva de 2 a 100 caracteres sin espacios sobrantes
 * @param correo correo de la cuenta nueva, normalizado a minúsculas por el servicio
 * @param telefono nueve dígitos opcionales de la cuenta nueva
 * @param especialidad descripción obligatoria de hasta 100 caracteres
 * @param passwordTemporal contraseña de cuenta nueva que debe cumplir PoliticaPassword
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CrearBarberoDto(
        @Positive Long usuarioId,
        @Size(min = 2, max = 100)
        @Pattern(regexp = "[^\\s\\p{Z}]+(?: [^\\s\\p{Z}]+)*", message = "El nombre no debe tener espacios sobrantes.") String nombre,
        @Email @Size(max = 254) String correo,
        @Pattern(regexp = "[0-9]{9}", message = "Debe tener nueve dígitos.") String telefono,
        @NotBlank @Size(max = 100) String especialidad,
        String passwordTemporal) { }
