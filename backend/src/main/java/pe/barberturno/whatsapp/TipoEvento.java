package pe.barberturno.whatsapp;

/**
 * Clase de notificación guardada en la cola de entrada.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum TipoEvento {
    /** Mensaje de un cliente ({@code value.messages[]}); clave {@code "m:" + id}. */
    MENSAJE,
    /** Aviso de estado de un mensaje saliente ({@code value.statuses[]}); clave {@code "s:" + id + ":" + status}. */
    ESTADO
}
