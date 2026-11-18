package pe.barberturno.conversation;

import java.time.Instant;

/**
 * Operaciones pendientes con nonce y su ejecución con {@code ReservaService} (V2-RN-10, V2-DA-13;
 * 13-v2-10 §3.3 y §5). Todas exigen la transacción de la entrega y la conversación ya bloqueada.
 * Implementación: Codex (bloque X).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface OperacionesConversacion {

    /**
     * Propone crear una reserva tras revalidar servicio, profesional, franja, RN-04 y RN-20.
     * @param conversacionId conversación bloqueada
     * @param fichaId ficha enlazada a la conversación
     * @param servicioId servicio elegido
     * @param barberoId profesional de la franja
     * @param inicio inicio elegido
     * @return la propuesta vigente o la regla incumplida
     */
    Propuesta proponerCreacion(long conversacionId, long fichaId, long servicioId, long barberoId, Instant inicio);

    /**
     * Propone reprogramar una cita de la ficha con el mismo profesional y servicio (RN-07/09).
     * @param conversacionId conversación bloqueada
     * @param fichaId ficha enlazada a la conversación
     * @param reservaId reserva de la ficha
     * @param inicio nuevo inicio
     * @return la propuesta vigente o la regla incumplida ({@code NO_ENCONTRADO} si no es de la ficha)
     */
    Propuesta proponerReprogramacion(long conversacionId, long fichaId, long reservaId, Instant inicio);

    /**
     * Propone cancelar una cita de la ficha (RN-07).
     * @param conversacionId conversación bloqueada
     * @param fichaId ficha enlazada a la conversación
     * @param reservaId reserva de la ficha
     * @return la propuesta vigente o la regla incumplida ({@code NO_ENCONTRADO} si no es de la ficha)
     */
    Propuesta proponerCancelacion(long conversacionId, long fichaId, long reservaId);

    /**
     * Ejecuta la operación indicada por el botón: bloquea su fila, comprueba conversación, ficha,
     * nonce (en tiempo constante), estado y vigencia, y llama al servicio de reservas.
     * @param conversacionId conversación bloqueada
     * @param fichaId ficha enlazada a la conversación
     * @param operacionId operación del botón
     * @param nonce nonce del botón (base64url)
     * @return resultado confirmado en la base de datos
     */
    ResultadoOperacion confirmar(long conversacionId, long fichaId, long operacionId, String nonce);

    /**
     * Ejecuta la única operación vigente de la conversación («sí» escrito, V2-RN-10).
     * @param conversacionId conversación bloqueada
     * @param fichaId ficha enlazada a la conversación
     * @return resultado, o {@code NoVigente(NO_ENCONTRADA)} si no hay ninguna vigente
     */
    ResultadoOperacion confirmarVigente(long conversacionId, long fichaId);

    /**
     * Rechaza la operación indicada por el botón [Cambiar] con las mismas comprobaciones que confirmar.
     * @param conversacionId conversación bloqueada
     * @param fichaId ficha enlazada a la conversación
     * @param operacionId operación del botón
     * @param nonce nonce del botón
     * @return {@code Anulada}, o {@code NoVigente} si ya no estaba vigente o no coincide
     */
    ResultadoOperacion anular(long conversacionId, long fichaId, long operacionId, String nonce);

    /**
     * Anula la operación vigente de la conversación, si la hay (vuelta al menú, inactividad o «no»).
     * @param conversacionId conversación bloqueada
     * @return verdadero si había una vigente y quedó {@code ANULADA}
     */
    boolean anularVigente(long conversacionId);
}
