package pe.barberturno.reservations;

import java.time.Instant;

/**
 * Fotografía escalar sin bloqueo ni entidades JPA; decide los bloqueos RF-09 sin caché obsoleta.
 * @param clienteId propietario inmutable, fila del bloqueo ①
 * @param barberoId asignación inicial, incluida en el bloqueo ②
 * @param asignadoId cuenta vinculada al barbero para visibilidad
 * @param version versión observada antes de esperar
 * @param estado estado observado antes de esperar
 * @param inicio inicio original para RN-07/08
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ReservaLectura(long clienteId, long barberoId, long asignadoId, int version,
        EstadoReserva estado, Instant inicio) { }
