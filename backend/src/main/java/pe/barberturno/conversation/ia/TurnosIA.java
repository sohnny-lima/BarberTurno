package pe.barberturno.conversation.ia;

/**
 * Turnos de IA fuera de la transacción de entrega (17-v2-12 §3; V2-DA-77). Implementación: Codex (XI).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface TurnosIA {
    /**
     * Encola un turno para el texto libre recibido. Exige la transacción de entrega con la conversación bloqueada
     * ({@code Propagation.MANDATORY}). Marca {@code DESCARTADO} el pendiente anterior de la conversación.
     * @param conversacionId conversación bloqueada del negocio actual
     * @param revision {@code conversacion.revision} leída tras las escrituras de la entrega
     * @param texto texto del cliente, ya saneado y recortado
     * @return id del turno
     */
    long encolar(long conversacionId, long revision, String texto);

    /**
     * Resuelve los turnos pendientes de todos los negocios, de uno en uno (trabajador programado). Se llama sin
     * transacción exterior.
     * @return turnos terminados en esta pasada
     */
    int procesarPendientes();
}
