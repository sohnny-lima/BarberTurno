package pe.barberturno.whatsapp;

/**
 * Motivo codificado de un evento descartado o fallido; nunca contiene datos personales (V2-DA-55).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum MotivoEvento {
    /** Ningún canal tiene el {@code phone_number_id} receptor. */
    NUMERO_NO_REGISTRADO,
    /** El canal del número está desconectado. */
    CANAL_DESCONECTADO,
    /** El negocio está suspendido o no tiene propietario activo (V2-DA-53). */
    NEGOCIO_NO_OPERATIVO,
    /** El remitente o destinatario no es un {@code wa_id} válido. */
    REMITENTE_INVALIDO,
    /** El puerto falló en todos los intentos permitidos. */
    FALLO_PROCESAMIENTO
}
