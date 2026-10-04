package pe.barberturno.notifications.dto;

/**
 * Contador RF-16 calculado en la base de datos para el usuario autenticado.
 * @param noLeidas cantidad de avisos propios pendientes de lectura
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ConteoNotificacionesDto(long noLeidas) { }
