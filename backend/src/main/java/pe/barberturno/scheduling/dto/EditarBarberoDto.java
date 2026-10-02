package pe.barberturno.scheduling.dto;

import jakarta.validation.constraints.*;

/**
 * Edición RF-05 de identidad visible y especialidad; conserva correo, rol y acceso.
 * @param nombre nombre obligatorio de 2 a 100 caracteres sin espacios sobrantes
 * @param telefono nueve dígitos opcionales para personal
 * @param especialidad descripción obligatoria de hasta 100 caracteres
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record EditarBarberoDto(
        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = "[^\\s\\p{Z}]+(?: [^\\s\\p{Z}]+)*", message = "El nombre no debe tener espacios sobrantes.") String nombre,
        @Pattern(regexp = "[0-9]{9}", message = "Debe tener nueve dígitos.") String telefono,
        @NotBlank @Size(max = 100) String especialidad) { }
