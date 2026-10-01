package pe.barberturno.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Datos editables; el servicio valida también el múltiplo de diez de la duración. */
public record GuardarServicioDto(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 2, max = 80, message = "El nombre debe tener entre 2 y 80 caracteres.")
        @Pattern(regexp = "[^\\s\\p{Z}]+(?: [^\\s\\p{Z}]+)*",
                message = "El nombre no debe tener espacios sobrantes.") String nombre,
        @NotNull(message = "La descripción es obligatoria.")
        @Size(max = 300, message = "La descripción admite hasta 300 caracteres.") String descripcion,
        @NotNull(message = "La duración es obligatoria.")
        @Min(value = 10, message = "La duración mínima es de 10 minutos.")
        @Max(value = 180, message = "La duración máxima es de 180 minutos.") Integer duracionMin,
        @NotNull(message = "El precio es obligatorio.")
        @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
        @Digits(integer = 6, fraction = 2,
                message = "El precio admite hasta seis cifras enteras y dos decimales.") BigDecimal precio) { }
