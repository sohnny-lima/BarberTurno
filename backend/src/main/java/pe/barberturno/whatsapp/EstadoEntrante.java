package pe.barberturno.whatsapp;

import java.time.Instant;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/**
 * Aviso de estado de un mensaje saliente, atribuido al negocio por su número receptor (V2-RN-02).
 * @param eventoId evento de la cola que lo originó
 * @param negocioId negocio dueño del número
 * @param wamid identificador del mensaje saliente al que se refiere
 * @param estado estado informado ({@code sent}, {@code delivered}, {@code read}, {@code failed})
 * @param waId destinatario del mensaje ({@code recipient_id}), solo dígitos
 * @param enEstadoDesde instante del cambio de estado informado por WhatsApp
 * @param dato objeto {@code statuses[i]} tal como llegó en la notificación
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record EstadoEntrante(long eventoId, long negocioId, String wamid, String estado, String waId,
        Instant enEstadoDesde, JsonNode dato) {
    /**
     * Exige todos los campos del aviso.
     * @param eventoId evento de la cola
     * @param negocioId negocio receptor
     * @param wamid mensaje saliente
     * @param estado estado informado
     * @param waId destinatario
     * @param enEstadoDesde instante del cambio
     * @param dato objeto original
     * @throws NullPointerException si falta un campo
     */
    public EstadoEntrante {
        Objects.requireNonNull(wamid, "wamid");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(waId, "waId");
        Objects.requireNonNull(enEstadoDesde, "enEstadoDesde");
        Objects.requireNonNull(dato, "dato");
    }
}
