package pe.barberturno.reservations.dto;

import jakarta.validation.constraints.*;

/**
 * Solicitud RF-10 con versión obligatoria para detectar pestañas o acciones desactualizadas.
 * El motivo es opcional para CLIENTE; el servicio exige cinco caracteres no blancos al ADMIN.
 * @param version versión optimista que el actor leyó, no negativa
 * @param motivo justificación opcional de hasta 300 caracteres
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CancelarReservaDto(@NotNull @PositiveOrZero Integer version,
        @Size(max = 300) String motivo) { }
