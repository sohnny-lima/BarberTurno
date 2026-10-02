package pe.barberturno.users.dto;

import jakarta.validation.constraints.*;

/**
 * Campos editables RF-03; el correo permanece inmutable y el teléfono es obligatorio para CLIENTE en el
 * servicio.
 * @param nombre nombre no vacío de 2 a 100 caracteres sin espacios sobrantes
 * @param telefono nueve dígitos; nulo para personal, exigido para CLIENTE por el servicio
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ActualizarPerfilDto(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
        @Pattern(regexp = "[^\\s\\p{Z}]+(?: [^\\s\\p{Z}]+)*",
                message = "El nombre no debe tener espacios sobrantes.") String nombre,
        @Pattern(regexp = "[0-9]{9}", message = "Debe tener nueve dígitos.") String telefono) { }