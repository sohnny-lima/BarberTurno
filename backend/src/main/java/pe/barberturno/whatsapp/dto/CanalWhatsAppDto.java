package pe.barberturno.whatsapp.dto;

import java.time.OffsetDateTime;
import pe.barberturno.whatsapp.EstadoCanal;

/**
 * Canal de WhatsApp de un negocio visto por la plataforma; nunca incluye tokens ni secretos (V2-DA-14).
 * @param negocioId negocio dueño del número
 * @param wabaId cuenta de WhatsApp Business del número
 * @param phoneNumberId identificador del número en la Cloud API, que identifica el negocio de cada mensaje
 * @param numeroVisible número tal como lo ven los clientes
 * @param estado activo o desconectado
 * @param actualizadoEn último cambio, con desfase de Lima
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CanalWhatsAppDto(long negocioId, String wabaId, String phoneNumberId, String numeroVisible,
        EstadoCanal estado, OffsetDateTime actualizadoEn) { }
