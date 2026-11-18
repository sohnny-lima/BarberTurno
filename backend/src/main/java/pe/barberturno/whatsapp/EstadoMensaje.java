package pe.barberturno.whatsapp;

/**
 * Estado de un mensaje de {@code mensaje_whatsapp}. Los salientes avanzan de {@link #PENDIENTE} a
 * {@link #ENVIADO}, {@link #ENTREGADO} y {@link #LEIDO}, o acaban en {@link #FALLIDO}; los avisos de
 * estado nunca hacen retroceder un mensaje (12-v2-09-whatsapp-salida.md §4.4).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum EstadoMensaje {
    /** Mensaje entrante guardado (V2-10); nunca en uno saliente. */
    RECIBIDO,
    /** En el outbox, esperando su envío o su reintento. */
    PENDIENTE,
    /** Aceptado por la Graph API o confirmado por un aviso {@code sent}. */
    ENVIADO,
    /** Aviso {@code delivered}. */
    ENTREGADO,
    /** Aviso {@code read}. */
    LEIDO,
    /** Fallo permanente con su {@link MotivoFallo}; genera un aviso interno si hay reserva. */
    FALLIDO
}
