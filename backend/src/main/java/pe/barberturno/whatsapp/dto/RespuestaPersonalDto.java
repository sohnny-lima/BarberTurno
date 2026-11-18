package pe.barberturno.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Respuesta de texto libre del personal desde la bandeja (V2-11, 16-v2-11 §6).
 * @param texto de 1 a 4096 caracteres, no en blanco
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record RespuestaPersonalDto(@NotBlank(message = "El texto es obligatorio.")
        @Size(max = 4096, message = "El texto admite como máximo 4096 caracteres.") String texto) { }
