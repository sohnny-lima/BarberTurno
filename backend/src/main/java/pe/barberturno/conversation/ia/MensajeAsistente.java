package pe.barberturno.conversation.ia;

import java.util.Objects;
import tools.jackson.databind.node.ObjectNode;

/**
 * Mensaje que el servidor encola para el cliente al cerrar un turno de IA (17-v2-12 §13.4). Solo lo produce
 * {@link RespuestasAsistente}; nunca contiene texto del proveedor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface MensajeAsistente {
    /**
     * Mensaje de texto.
     * @param texto texto fijo del servidor
     */
    record Texto(String texto) implements MensajeAsistente {
        /**
         * Valida.
         * @param texto texto fijo del servidor
         * @throws NullPointerException si es nulo
         */
        public Texto {
            Objects.requireNonNull(texto, "texto");
        }
    }

    /**
     * Mensaje interactivo de Meta (lista o botones) con los códigos de selección de V2-10.
     * @param contenido objeto {@code interactive} de Meta
     */
    record Interactivo(ObjectNode contenido) implements MensajeAsistente {
        /**
         * Valida.
         * @param contenido objeto interactivo
         * @throws NullPointerException si es nulo
         */
        public Interactivo {
            Objects.requireNonNull(contenido, "contenido");
        }
    }
}
