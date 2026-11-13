package pe.barberturno.conversation;

import pe.barberturno.common.error.ErrorCodigo;

/**
 * Resultado de proponer una operación (13-v2-10 §3.3): la propuesta vigente o el motivo de rechazo.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface Propuesta {

    /**
     * Operación {@code VIGENTE} creada; la anterior de la conversación quedó {@code ANULADA}.
     * @param operacionId identidad de la operación
     * @param nonce nonce en base64url sin relleno (22 caracteres) para el identificador del botón
     * @param resumen datos del resumen que se muestra
     */
    record Creada(long operacionId, String nonce, ResumenOperacion resumen) implements Propuesta { }

    /**
     * La propuesta incumple una regla del negocio; no se creó ninguna operación ni se anuló la vigente.
     * @param codigo regla incumplida, traducida a texto por {@link TextosConversacion}
     */
    record Rechazada(ErrorCodigo codigo) implements Propuesta { }
}
