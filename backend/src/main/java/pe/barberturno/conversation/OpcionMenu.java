package pe.barberturno.conversation;

/**
 * Opciones del menú principal de la conversación guiada (V2-10; «Hablar con alguien» desde V2-11).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
public enum OpcionMenu {
    /** Reservar: servicio, profesional, día y franja; se puede recorrer sin ficha. */
    RESERVAR,
    /** Citas futuras de la ficha, con cancelar y reprogramar según los permisos del servidor. */
    MIS_CITAS,
    /** Volver al menú principal: vacía el estado y anula la operación vigente. */
    INICIO,
    /** «Hablar con alguien» ({@code m:PERSONA}): deriva a una persona; sin ficha, pide antes el consentimiento. */
    PERSONA
}
