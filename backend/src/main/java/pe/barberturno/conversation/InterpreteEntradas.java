package pe.barberturno.conversation;

import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Traduce el mensaje de Meta de un evento entrante en una {@link Entrada}, sin IA y sin guardar el
 * texto (13-v2-10 §3.1). Reconoce {@code interactive} ({@code button_reply} y {@code list_reply}, por su
 * {@code id}), {@code button} (por su {@code payload}) y {@code text} (por {@link PalabraClave}); un
 * identificador que no decodifica y cualquier otro tipo dan {@link Entrada.NoSoportada}.
 * Implementación: Gemini (bloque G).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class InterpreteEntradas {

    /** Constructor sin dependencias, usado por Spring y por las pruebas unitarias. */
    public InterpreteEntradas() { }

    /**
     * Interpreta un mensaje entrante.
     * @param tipo valor de {@code type} del mensaje de Meta
     * @param dato objeto del mensaje de Meta ({@code MensajeEntrante.dato()})
     * @return entrada interpretada; nunca nula ni excepción por datos mal formados
     */
    public Entrada interpretar(String tipo, JsonNode dato) {
        if (tipo == null || dato == null || !dato.isObject()) {
            return new Entrada.NoSoportada(tipo != null ? tipo : "desconocido");
        }
        return switch (tipo) {
            case "interactive" -> interpretarInteractivo(dato);
            case "button" -> interpretarBoton(dato);
            case "text" -> interpretarTexto(dato);
            default -> new Entrada.NoSoportada(tipo);
        };
    }

    private Entrada interpretarInteractivo(JsonNode dato) {
        JsonNode interactiveNode = dato.path("interactive");
        String id = null;
        JsonNode buttonReply = interactiveNode.path("button_reply");
        if (buttonReply.path("id").isString()) {
            id = buttonReply.path("id").asString();
        } else {
            JsonNode listReply = interactiveNode.path("list_reply");
            if (listReply.path("id").isString()) {
                id = listReply.path("id").asString();
            }
        }
        if (id != null) {
            Optional<CodigoSeleccion> seleccion = CodigoSeleccion.decodificar(id);
            if (seleccion.isPresent()) {
                return new Entrada.Seleccion(seleccion.get());
            }
        }
        return new Entrada.NoSoportada("interactive");
    }

    private Entrada interpretarBoton(JsonNode dato) {
        JsonNode buttonNode = dato.path("button");
        if (buttonNode.path("payload").isString()) {
            String payload = buttonNode.path("payload").asString();
            Optional<CodigoSeleccion> seleccion = CodigoSeleccion.decodificar(payload);
            if (seleccion.isPresent()) {
                return new Entrada.Seleccion(seleccion.get());
            }
        }
        return new Entrada.NoSoportada("button");
    }

    private Entrada interpretarTexto(JsonNode dato) {
        JsonNode textNode = dato.path("text");
        if (textNode.path("body").isString()) {
            String body = textNode.path("body").asString();
            return new Entrada.Texto(PalabraClave.de(body));
        }
        return new Entrada.NoSoportada("text");
    }
}
