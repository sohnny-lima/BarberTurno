package pe.barberturno.whatsapp.dto;

import java.time.OffsetDateTime;
import pe.barberturno.whatsapp.EstadoMensaje;
import pe.barberturno.whatsapp.MotivoFallo;
import pe.barberturno.whatsapp.OrigenMensaje;

/**
 * Mensaje del historial de una conversación derivada (V2-11, 16-v2-11 §6).
 * @param id identidad del mensaje
 * @param entrante verdadero si lo envió el cliente
 * @param origen cliente, asistente, sistema o persona del equipo
 * @param texto texto visible (el cuerpo, el título de la selección o el cuerpo del interactivo con sus
 *        botones entre corchetes), o nulo si {@code retirado}
 * @param retirado el contenido se retiró por la política de retención
 * @param estado estado de entrega (o {@code RECIBIDO} si es entrante)
 * @param motivoFallo motivo si no se pudo enviar, o nulo
 * @param creadoEn cuándo se registró, en la zona de Lima
 * @param enviadoPor nombre actual del usuario que respondió (solo origen {@code PERSONA}), o nulo
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record MensajeBandejaDto(long id, boolean entrante, OrigenMensaje origen, String texto, boolean retirado,
        EstadoMensaje estado, MotivoFallo motivoFallo, OffsetDateTime creadoEn, String enviadoPor) { }
