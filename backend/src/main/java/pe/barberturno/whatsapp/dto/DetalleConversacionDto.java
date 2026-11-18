package pe.barberturno.whatsapp.dto;

import java.util.List;

/**
 * Conversación derivada con una página de su historial (V2-11, 16-v2-11 §6).
 * @param conversacion datos de la conversación
 * @param mensajes hasta 50 mensajes, en orden ascendente de {@code id}
 * @param hayAnteriores quedan mensajes más antiguos (pedirlos con {@code antesDe} = el primer id)
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record DetalleConversacionDto(ConversacionBandejaDto conversacion, List<MensajeBandejaDto> mensajes,
        boolean hayAnteriores) { }
