package pe.barberturno.reporting.dto;

/**
 * Agrupación escalar de RF-14 por identidad y nombre de servicio o profesional.
 * @param id identidad persistente del recurso agrupado
 * @param nombre nombre actual del servicio o del usuario del barbero
 * @param total reservas cuyo inicio satisface los filtros comunes
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ConteoReporteDto(long id, String nombre, long total) { }
