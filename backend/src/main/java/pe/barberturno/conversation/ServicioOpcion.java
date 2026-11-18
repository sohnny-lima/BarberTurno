package pe.barberturno.conversation;

import java.math.BigDecimal;

/**
 * Servicio activo del negocio tal como se ofrece en la conversación.
 * @param id identidad del servicio en el negocio
 * @param nombre nombre visible
 * @param duracionMin duración en minutos
 * @param precio precio en soles
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ServicioOpcion(long id, String nombre, int duracionMin, BigDecimal precio) { }
