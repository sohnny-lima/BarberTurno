package pe.barberturno.scheduling.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cambio lógico RN-16 sin cancelación de reservas ni borrado de historial.
 * @param activo estado solicitado obligatorio
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record EstadoBarberoDto(@NotNull Boolean activo) { }
