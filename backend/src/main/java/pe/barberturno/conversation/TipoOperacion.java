package pe.barberturno.conversation;

/**
 * Tipo de una operación pendiente ({@code operacion_pendiente.tipo}, V2-RN-10).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum TipoOperacion {
    /** Crear una reserva para la ficha de la conversación. */
    CREAR,
    /** Cambiar el inicio de una reserva de la ficha, con el mismo profesional y servicio. */
    REPROGRAMAR,
    /** Cancelar una reserva de la ficha. */
    CANCELAR
}
