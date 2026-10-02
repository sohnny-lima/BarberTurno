package pe.barberturno.scheduling.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Solicitud atómica RF-20 para perfiles distintos; no limita la selección a los activos.
 * @param barberoIds perfiles positivos, obligatorios y sin duplicados
 * @param inicio inicio inclusivo con desfase obligatorio
 * @param fin fin exclusivo con desfase obligatorio
 * @param motivo explicación obligatoria de 3 a 200 caracteres
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CrearBloqueosLoteDto(
        @NotEmpty List<@NotNull @Positive Long> barberoIds,
        @NotNull OffsetDateTime inicio,
        @NotNull OffsetDateTime fin,
        @NotBlank @Size(min = 3, max = 200) String motivo) { }
