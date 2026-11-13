package pe.barberturno.whatsapp;

/**
 * Puerto por el que el procesador entrega los avisos de estado de los mensajes salientes (V2-09), en
 * la transacción del negocio receptor y con la misma política de reintentos que los mensajes.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ReceptorEstadosWhatsApp {
    /**
     * Recibe un aviso de estado ({@code sent}, {@code delivered}, {@code read} o {@code failed}).
     * @param estado aviso de estado; nunca nulo
     */
    void recibir(EstadoEntrante estado);
}
