package pe.barberturno.scheduling.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Franja semiabierta RN-01 común a los perfiles disponibles RF-21; no expone datos personales.
 * @param inicio inicio inclusivo con desfase de Lima
 * @param fin fin exclusivo con desfase de Lima
 * @param barberoIds perfiles disponibles ordenados por identidad
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record FranjaDisponibleDto(OffsetDateTime inicio, OffsetDateTime fin, List<Long> barberoIds) { }
