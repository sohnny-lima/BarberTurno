package pe.barberturno.conversation;

/**
 * Opciones del menú principal de la conversación guiada. No hay «Hablar con alguien» hasta V2-11.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum OpcionMenu {
    /** Reservar: servicio, profesional, día y franja; se puede recorrer sin ficha. */
    RESERVAR,
    /** Citas futuras de la ficha, con cancelar y reprogramar según los permisos del servidor. */
    MIS_CITAS,
    /** Volver al menú principal: vacía el estado y anula la operación vigente. */
    INICIO
}
