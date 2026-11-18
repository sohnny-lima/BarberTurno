package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.time.Instant;
import pe.barberturno.reservations.EstadoReserva;

/**
 * Reserva de la ficha de la conversación con los permisos que calcula el servidor (DA-15).
 * @param reservaId identidad de la reserva
 * @param codigo código visible, por ejemplo {@code BT-245}
 * @param servicioId servicio reservado
 * @param servicio nombre del servicio
 * @param barberoId profesional asignado
 * @param profesional nombre visible del profesional
 * @param inicio inicio de la cita
 * @param fin fin de la cita
 * @param precio precio del servicio en soles
 * @param estado estado actual de la reserva
 * @param version versión leída, que la operación pendiente guarda para el control optimista
 * @param puedeCancelar si las reglas del negocio permiten cancelarla ahora (RN-07)
 * @param puedeReprogramar si las reglas del negocio permiten reprogramarla ahora (RN-07/09)
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CitaOpcion(long reservaId, String codigo, long servicioId, String servicio, long barberoId,
        String profesional, Instant inicio, Instant fin, BigDecimal precio, EstadoReserva estado, int version,
        boolean puedeCancelar, boolean puedeReprogramar) { }
