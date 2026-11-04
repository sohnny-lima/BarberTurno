package pe.barberturno.conversation;

/**
 * Quién atiende una conversación (V2-RN-15): el asistente o una persona del equipo desde la bandeja.
 * Coincide con el {@code CHECK} de {@code conversacion.modo}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum ModoConversacion {
    /** El asistente responde (menús o, con V2-12, IA). */
    ASISTENTE,
    /** La conversación está derivada: el asistente no responde y el personal contesta desde la bandeja. */
    HUMANO
}
