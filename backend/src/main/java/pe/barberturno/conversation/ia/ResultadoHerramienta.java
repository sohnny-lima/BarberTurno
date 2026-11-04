package pe.barberturno.conversation.ia;

import java.util.Objects;

/**
 * Lo que el modelo recibe como resultado de una herramienta (17-v2-12 §4.3). Un error lleva un código y un mensaje
 * genéricos, sin el argumento recibido ni datos de otro negocio o ficha.
 * @param llamadaId {@code tool_use_id} de la llamada
 * @param json resultado en JSON
 * @param error si es un error ({@code is_error})
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ResultadoHerramienta(String llamadaId, String json, boolean error) {
    /**
     * Valida.
     * @param llamadaId id de la llamada
     * @param json resultado en JSON
     * @param error si es un error
     * @throws NullPointerException si falta algún valor
     */
    public ResultadoHerramienta {
        Objects.requireNonNull(llamadaId, "llamadaId");
        Objects.requireNonNull(json, "json");
    }
}
