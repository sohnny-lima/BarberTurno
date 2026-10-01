package pe.barberturno.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** Estado explícito del servicio, sin borrado físico. */
public record EstadoServicioDto(@NotNull(message = "El estado es obligatorio.") Boolean activo) { }
