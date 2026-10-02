package pe.barberturno.catalog.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud de activación explícita del catálogo RF-04; preserva el historial mediante RN-16.
 * @param activo estado obligatorio no nulo; false desactiva sin borrar (RN-16)
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record EstadoServicioDto(@NotNull(message = "El estado es obligatorio.") Boolean activo) { }
