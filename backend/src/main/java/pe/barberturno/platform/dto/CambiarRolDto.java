package pe.barberturno.platform.dto;

import jakarta.validation.constraints.NotNull;
import pe.barberturno.platform.RolNegocio;

/**
 * Permisos solicitados sin admitir cambios de negocio ni identidad.
 * @param rol nuevo rol local obligatorio
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record CambiarRolDto(@NotNull RolNegocio rol) { }
