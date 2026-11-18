package pe.barberturno.whatsapp;

/**
 * Estado del canal de WhatsApp de un negocio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum EstadoCanal {
    /** Recibe mensajes; el procesador los entrega al negocio. */
    ACTIVO,
    /** Desconectado por la plataforma; sus eventos se descartan con {@code CANAL_DESCONECTADO}. */
    DESCONECTADO
}
