package pe.barberturno.reservations.dto;

import jakarta.validation.constraints.*;
import pe.barberturno.reservations.EstadoReserva;

/**
 * Solicitud RF-12 con destino operativo y versión para detectar acciones desactualizadas.
 * El servicio rechaza CANCELADA y PENDIENTE: cancelar tiene un endpoint propio.
 * @param estado destino obligatorio, sujeto a RN-10/11/12 en el servidor
 * @param version versión optimista leída por el actor, obligatoria y no negativa
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record TransicionarReservaDto(@NotNull EstadoReserva estado,
        @NotNull @PositiveOrZero Integer version) { }
