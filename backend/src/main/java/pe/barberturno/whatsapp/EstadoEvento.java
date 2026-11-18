package pe.barberturno.whatsapp;

/**
 * Ciclo de vida de un evento de la cola de entrada (V2-DA-52).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum EstadoEvento {
    /** Pendiente o reclamado y en curso; disponible cuando vence {@code proximo_intento}. */
    PENDIENTE,
    /** Entregado al puerto del negocio dentro de su transacción. */
    PROCESADO,
    /** No se entrega: número no registrado, canal desconectado, negocio no operativo o remitente inválido. */
    DESCARTADO,
    /** Agotó los intentos de entrega. */
    ERROR
}
