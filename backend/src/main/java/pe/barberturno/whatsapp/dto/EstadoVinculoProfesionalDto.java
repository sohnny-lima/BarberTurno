package pe.barberturno.whatsapp.dto;

import java.time.Instant;

/**
 * Estado de vinculación sin número, identificador de WhatsApp ni código.
 * @param vinculado indica si existe el vínculo del profesional autorizado
 * @param vinculadoEn fecha de vinculación, nula cuando no existe
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record EstadoVinculoProfesionalDto(boolean vinculado, Instant vinculadoEn) { }
