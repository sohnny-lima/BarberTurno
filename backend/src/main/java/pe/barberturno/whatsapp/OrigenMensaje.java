package pe.barberturno.whatsapp;

/**
 * Quién origina un mensaje de WhatsApp guardado en {@code mensaje_whatsapp.origen}
 * (12-v2-09-whatsapp-salida.md §4.1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum OrigenMensaje {
    /** El cliente; solo en mensajes entrantes, nunca en el encolado de salida. */
    CLIENTE,
    /** El asistente conversacional (menús guiados o IA). */
    ASISTENTE,
    /** El servidor: resultados de operaciones, avisos y plantillas (V2-RN-11). */
    SISTEMA,
    /** Una persona del equipo desde la bandeja (V2-11). */
    PERSONA
}
