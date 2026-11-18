package pe.barberturno.conversation;

/**
 * Por qué una conversación pasó a modo humano (V2-11, 16-v2-11 §1). Se guarda en
 * {@code conversacion.motivo_derivacion} y se muestra en la bandeja.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum MotivoDerivacion {
    /** El cliente lo pidió: botón «Hablar con alguien» o palabra clave {@link PalabraClave#PERSONA}. */
    PETICION,
    /** Dos textos seguidos que el asistente no reconoció. */
    SIN_PROGRESO,
    /** Límite de uso de la IA superado (V2-RN-14; lo usa V2-12). */
    LIMITE,
    /** El modelo pidió la herramienta {@code derivar_a_persona} (lo usa V2-12). */
    HERRAMIENTA
}
