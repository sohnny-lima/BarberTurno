package pe.barberturno.reservations;

/**
 * Estados persistidos de una reserva; la política de transición se incorpora en T-09.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, EN_ATENCION, COMPLETADA, CANCELADA, NO_ASISTIO
}
