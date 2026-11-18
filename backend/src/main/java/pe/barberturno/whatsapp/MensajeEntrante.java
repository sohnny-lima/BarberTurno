package pe.barberturno.whatsapp;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import tools.jackson.databind.JsonNode;

/**
 * Mensaje de un cliente atribuido a un negocio por su número receptor (V2-RN-02) y, solo por
 * {@code wa_id} exacto, a una ficha de ese negocio (V2-RN-09, V2-DA-54).
 * @param eventoId evento de la cola que lo originó
 * @param negocioId negocio dueño del número receptor
 * @param waId remitente verificado por WhatsApp ({@code messages[].from}), solo dígitos
 * @param clienteId ficha del negocio con ese {@code wa_id}, o vacío si no existe
 * @param nombrePerfil nombre del perfil de WhatsApp declarado por el remitente, o {@code null}; no verificado
 * @param wamid identificador del mensaje en WhatsApp
 * @param tipo tipo de mensaje de WhatsApp ({@code text}, {@code interactive}, {@code button}…)
 * @param enviadoEn instante de envío informado por WhatsApp
 * @param dato objeto {@code messages[i]} tal como llegó en la notificación
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record MensajeEntrante(long eventoId, long negocioId, String waId, OptionalLong clienteId, String nombrePerfil,
        String wamid, String tipo, Instant enviadoEn, JsonNode dato) {
    /**
     * Exige los campos que identifican el mensaje y su remitente.
     * @param eventoId evento de la cola
     * @param negocioId negocio receptor
     * @param waId remitente verificado
     * @param clienteId ficha o vacío
     * @param nombrePerfil nombre declarado o {@code null}
     * @param wamid identificador del mensaje
     * @param tipo tipo de mensaje
     * @param enviadoEn instante de envío
     * @param dato objeto original
     * @throws NullPointerException si falta un campo obligatorio
     */
    public MensajeEntrante {
        Objects.requireNonNull(waId, "waId");
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(wamid, "wamid");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(enviadoEn, "enviadoEn");
        Objects.requireNonNull(dato, "dato");
    }
}
