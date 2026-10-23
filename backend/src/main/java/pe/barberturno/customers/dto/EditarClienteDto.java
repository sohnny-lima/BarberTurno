package pe.barberturno.customers.dto;

import jakarta.validation.constraints.*;

/**
 * Edición local sin campos para alterar vínculos, estado, identidad o credenciales.
 * @param nombre nombre local obligatorio, hasta cien caracteres
 * @param telefono contacto local opcional, hasta dieciséis dígitos sin signo según el DDL
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record EditarClienteDto(@NotBlank @Size(max = 100) String nombre,
        @Pattern(regexp = "[0-9]{1,16}", message = "Use hasta dieciséis dígitos sin signo.") String telefono) { }
