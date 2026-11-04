package pe.barberturno.conversation;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Palabras clave que la conversación guiada reconoce en un texto, tras normalizarlo (recorte,
 * minúsculas, sin tildes ni signos). Ningún otro texto se interpreta (13-v2-10 §3.1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum PalabraClave {
    /** «menu», «hola», «inicio» o «0»: vuelve al menú principal y anula la operación vigente. */
    MENU,
    /** «si», «confirmar» o «confirmo»: confirma la única operación vigente (V2-RN-10). */
    SI,
    /** «no»: rechaza la única operación vigente. */
    NO,
    /**
     * Petición de una persona (V2-11, 16-v2-11 §3.1): frases exactas tras normalizar —«hablar con alguien»,
     * «hablar con una persona», «persona», «humano», «asesor», «agente», «ayuda»—, sin buscar subcadenas.
     */
    PERSONA,
    /** Cualquier otro texto: se responde con el menú. */
    OTRA;

    /**
     * Normaliza el texto y lo reduce a una palabra clave.
     * @param texto texto recibido; nulo o vacío equivale a {@link #OTRA}
     * @return palabra clave reconocida, o {@link #OTRA}
     */
    public static PalabraClave de(String texto) {
        if (texto == null) {
            return OTRA;
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalizado.isEmpty()) {
            return OTRA;
        }
        return switch (normalizado) {
            case "menu", "hola", "inicio", "0" -> MENU;
            case "si", "confirmar", "confirmo" -> SI;
            case "no" -> NO;
            default -> OTRA;
        };
    }
}
