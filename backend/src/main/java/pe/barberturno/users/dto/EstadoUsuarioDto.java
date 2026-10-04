package pe.barberturno.users.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Estado de acceso solicitado sin borrar el historial RN-16.
 * @param activo habilitación obligatoria; desactivar revoca sesiones
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record EstadoUsuarioDto(@NotNull Boolean activo) { }
