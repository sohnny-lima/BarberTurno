package pe.barberturno.platform.dto;

import jakarta.validation.constraints.*;

/**
 * Configuración editable por el propietario; excluye identidad, estado, zona y rejilla.
 * Los mínimos conservan V2-04 y el máximo de los números corresponde al smallint de SQL.
 * @param nombre nombre público local, obligatorio y de hasta cien caracteres
 * @param anticipacionCambioClienteMin minutos no negativos para los cambios del cliente
 * @param horizonteDias días positivos de anticipación máxima de reservas
 * @param limiteReservasActivas máximo positivo de reservas futuras por ficha
 * @param limiteBarberosActivos cupo positivo de perfiles profesionales activos
 * @param confirmacionManual si el autoservicio requiere confirmación del personal
 * @param toleranciaInicioMin minutos no negativos de adelanto para iniciar la atención
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record ConfiguracionNegocioDto(
        @NotBlank @Size(max = 100) String nombre,
        @NotNull @Min(0) @Max(32767) Integer anticipacionCambioClienteMin,
        @NotNull @Min(1) @Max(32767) Integer horizonteDias,
        @NotNull @Min(1) @Max(32767) Integer limiteReservasActivas,
        @NotNull @Min(1) @Max(32767) Integer limiteBarberosActivos,
        @NotNull Boolean confirmacionManual,
        @NotNull @Min(0) @Max(32767) Integer toleranciaInicioMin) { }
