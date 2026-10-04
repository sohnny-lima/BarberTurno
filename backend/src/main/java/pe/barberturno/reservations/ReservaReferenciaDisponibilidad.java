package pe.barberturno.reservations;

/**
 * Fotografía escalar RN-09/13 para consultar disponibilidad de reprogramación sin bloqueo ni caché JPA.
 * Solo se lee después de autorizar la exclusión de la reserva (DA-21).
 * @param servicioId servicio inmutable de la reserva, que debe coincidir con el solicitado
 * @param duracionRefMin minutos conservados al crear la reserva, independientes del catálogo actual
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ReservaReferenciaDisponibilidad(long servicioId, short duracionRefMin) { }
