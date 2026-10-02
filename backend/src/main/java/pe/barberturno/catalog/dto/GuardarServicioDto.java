package pe.barberturno.catalog.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Entrada de mantenimiento RF-04; Bean Validation comprueba formato y el servicio exige duración múltiplo de
 * diez.
 * @param nombre nombre no vacío de 2 a 80 caracteres sin espacios sobrantes
 * @param descripcion descripción no nula de hasta 300 caracteres; admite vacío
 * @param duracionMin duración obligatoria de 10 a 180 minutos, múltiplo de diez exigido por el servicio
 * @param precio precio obligatorio no negativo en soles, hasta seis enteros y dos decimales
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
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
