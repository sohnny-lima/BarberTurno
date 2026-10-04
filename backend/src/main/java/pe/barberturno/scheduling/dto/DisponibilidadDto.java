package pe.barberturno.scheduling.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultado público RF-07/RF-21 para una fecha de negocio y la duración actual del catálogo.
 * @param fecha día consultado en Lima
 * @param servicioId identidad del servicio activo
 * @param duracionMin duración completa en minutos
 * @param franjas intervalos disponibles ordenados por inicio, sin duplicados
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record DisponibilidadDto(LocalDate fecha, long servicioId, int duracionMin,
        List<FranjaDisponibleDto> franjas) { }
