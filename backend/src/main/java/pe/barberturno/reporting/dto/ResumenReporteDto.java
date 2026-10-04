package pe.barberturno.reporting.dto;

import java.util.List;
import java.util.Map;
import pe.barberturno.reservations.EstadoReserva;

/**
 * Resumen RF-14 conciliable con el historial operativo bajo los mismos filtros RN-22.
 * @param total suma de las agrupaciones por estado
 * @param porEstado los seis estados, con cero para los ausentes
 * @param porServicio servicios con reservas, por total descendente y nombre ascendente
 * @param porBarbero profesionales con reservas, por total descendente y nombre ascendente
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ResumenReporteDto(long total, Map<EstadoReserva, Long> porEstado,
        List<ConteoReporteDto> porServicio, List<ConteoReporteDto> porBarbero) { }
