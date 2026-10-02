package pe.barberturno.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Solicitud RF-06 con desfase explícito; RN-18 se comprueba dentro de la transacción.
 * @param inicio inicio inclusivo con desfase obligatorio
 * @param fin fin exclusivo con desfase obligatorio
 * @param motivo explicación obligatoria de 3 a 200 caracteres
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CrearBloqueoDto(
        @NotNull OffsetDateTime inicio,
        @NotNull OffsetDateTime fin,
        @NotBlank @Size(min = 3, max = 200) String motivo) { }
