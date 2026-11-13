package pe.barberturno.whatsapp;

/**
 * Trabajo que vacía el outbox de WhatsApp: por cada negocio con canal reclama un lote con
 * {@code SKIP LOCKED} y ficha, prepara, envía fuera de toda transacción y cierra cada mensaje
 * (12-v2-09-whatsapp-salida.md §4.3, V2-DA-57). Lo dispara el programador periódico; las pruebas lo
 * llaman directamente.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface EnviadorSalidaWhatsApp {
    /**
     * Atiende una pasada de todos los negocios con canal. Sin token de Graph configurado no reclama nada.
     * @return número de mensajes reclamados en esta pasada
     */
    int enviarLote();
}
