package pe.barberturno.conversation;

import java.util.OptionalLong;

/**
 * Paso de una conversación entre el asistente y una persona del equipo (V2-11, 16-v2-11 §3.1 y §5).
 * Implementación: Codex (bloque X11). Las escrituras siguen el orden de bloqueos de V2-DA-65: la
 * conversación primero.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface DerivacionConversacion {
    /**
     * Deriva la conversación a una persona. Exige la transacción del llamante con la conversación ya
     * bloqueada ({@code Propagation.MANDATORY}) y una ficha enlazada. Si ya está en {@code HUMANO}, no cambia
     * nada. Si no: modo {@code HUMANO}, fecha y motivo de la derivación, operación vigente anulada, estado
     * vacío, acuse de origen {@code SISTEMA} en la outbox (si la ventana efectiva está abierta) y un aviso a
     * cada PROPIETARIO y ENCARGADO activo del negocio.
     * @param conversacionId conversación bloqueada del negocio actual, con ficha
     * @param motivo motivo que verá el personal
     * @return verdadero si la derivó; falso si ya estaba derivada
     * @throws IllegalStateException si la conversación no tiene ficha
     */
    boolean derivar(long conversacionId, MotivoDerivacion motivo);

    /**
     * Devuelve la conversación al asistente. Exige la transacción del llamante ({@code Propagation.MANDATORY});
     * bloquea la conversación si el llamante no lo hizo. Si no está en {@code HUMANO}, no cambia nada. Si no:
     * modo {@code ASISTENTE}, datos de la derivación a nulo y estado vacío; si la ventana efectiva está
     * abierta, encola el aviso de vuelta y el menú principal (origen {@code SISTEMA}).
     * @param conversacionId conversación del negocio actual
     * @param usuarioId miembro que la devuelve desde la bandeja, o vacío en el retorno automático
     * @return verdadero si la devolvió; falso si ya estaba con el asistente
     */
    boolean devolver(long conversacionId, OptionalLong usuarioId);

    /**
     * Retorno automático (16-v2-11 §5.3): devuelve al asistente las conversaciones en {@code HUMANO} sin
     * actividad del personal durante {@code barberturno.whatsapp.bandeja.retorno-automatico}, comprobándolo
     * de nuevo bajo el bloqueo. Se llama <strong>sin</strong> transacción exterior: recorre los negocios con
     * {@code EjecutorEnNegocio} y abre una transacción por negocio y lote.
     * @return número de conversaciones devueltas
     */
    int devolverInactivas();
}
