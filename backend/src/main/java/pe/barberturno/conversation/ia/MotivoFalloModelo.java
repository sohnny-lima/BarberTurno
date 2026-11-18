package pe.barberturno.conversation.ia;

/**
 * Por qué una llamada al modelo no produjo una respuesta utilizable (17-v2-12 §3.3.6). En todos los casos el turno
 * responde con los menús.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum MotivoFalloModelo {
    /** Falta la clave o el modelo (P2-03). */
    NO_CONFIGURADO,
    /** Se agotó el tiempo de la llamada o del turno. */
    TIEMPO_AGOTADO,
    /** Error del proveedor (5xx, red). */
    PROVEEDOR,
    /** Límite de uso del proveedor (429). */
    LIMITE_PROVEEDOR,
    /** El proveedor rechazó la petición ({@code refusal}). */
    RECHAZO,
    /** Salida truncada ({@code max_tokens}), JSON inválido o herramienta desconocida. */
    SALIDA_INVALIDA
}
