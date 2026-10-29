package pe.barberturno.conversation;

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
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }
}
