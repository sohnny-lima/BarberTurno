package pe.barberturno.users.dto;

import jakarta.validation.constraints.*;

/** Campos editables; el teléfono obligatorio del cliente se valida en el servicio. */
public record ActualizarPerfilDto(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
        @Pattern(regexp = "[^\\s\\p{Z}]+(?: [^\\s\\p{Z}]+)*",
                message = "El nombre no debe tener espacios sobrantes.") String nombre,
        @Pattern(regexp = "[0-9]{9}", message = "Debe tener nueve dígitos.") String telefono) { }