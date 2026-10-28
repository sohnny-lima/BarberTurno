package pe.barberturno.whatsapp;

/**
 * Puerto por el que el procesador entrega cada mensaje entrante a la conversación (V2-10). Se invoca
 * una sola vez por mensaje, dentro de la transacción del negocio receptor; si lanza una excepción, la
 * transacción se revierte y el evento se reintenta (11-v2-08-whatsapp-entrada.md §4.4).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ReceptorMensajesWhatsApp {
    /**
     * Recibe un mensaje ya atribuido a su negocio y, si existe, a la ficha cuyo {@code wa_id} coincide.
     * @param mensaje mensaje entrante; nunca nulo
     */
    void recibir(MensajeEntrante mensaje);
}
