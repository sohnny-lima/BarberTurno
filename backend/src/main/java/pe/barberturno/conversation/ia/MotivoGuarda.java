package pe.barberturno.conversation.ia;

/**
 * Por qué las guardas sustituyen el texto del modelo por la composición del servidor (17-v2-12 §5).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum MotivoGuarda {
    /** Menciona una hora o fecha que no es una franja ofrecida en el turno (V2-RN-12). */
    HORARIO_NO_OFRECIDO,
    /** Afirma que una operación se hizo (V2-RN-11). */
    AFIRMA_OPERACION,
    /** Contiene teléfonos, correos, códigos internos, alias o enlaces no permitidos. */
    DATOS_PROHIBIDOS,
    /** Vacío o solo espacios. */
    VACIO,
    /** Supera 1024 caracteres. */
    DEMASIADO_LARGO
}
