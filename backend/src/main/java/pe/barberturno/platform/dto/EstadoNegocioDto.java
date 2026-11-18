package pe.barberturno.platform.dto;

import jakarta.validation.constraints.NotNull;
import pe.barberturno.platform.EstadoNegocio;

/**
 * Cambio de estado exclusivo de plataforma.
 * @param estado nueva disponibilidad administrativa, obligatoria
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record EstadoNegocioDto(@NotNull EstadoNegocio estado) { }
