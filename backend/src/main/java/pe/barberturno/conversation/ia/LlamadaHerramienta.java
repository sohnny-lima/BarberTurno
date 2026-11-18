package pe.barberturno.conversation.ia;

import java.util.Objects;

/**
 * Herramienta pedida por el modelo (17-v2-12 §4.1). Los argumentos viajan como JSON en texto: los nodos
 * Jackson 2 del SDK y los Jackson 3 de la aplicación no se mezclan (consulta 127-12). No son de confianza: los
 * valida {@code HerramientasReserva}.
 * @param id identificador del proveedor ({@code tool_use_id})
 * @param herramienta herramienta pedida
 * @param argumentosJson argumentos en JSON
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record LlamadaHerramienta(String id, HerramientaIA herramienta, String argumentosJson) {
    /**
     * Valida.
     * @param id identificador del proveedor
     * @param herramienta herramienta pedida
     * @param argumentosJson argumentos en JSON
     * @throws NullPointerException si falta algún valor
     */
    public LlamadaHerramienta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(herramienta, "herramienta");
        Objects.requireNonNull(argumentosJson, "argumentosJson");
    }
}
