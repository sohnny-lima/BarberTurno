package pe.barberturno.conversation;

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
    /** Cualquier otro texto: se responde con el menú. */
    OTRA;

    /**
     * Normaliza el texto y lo reduce a una palabra clave. Implementación: Gemini (bloque G).
     * @param texto texto recibido; nulo o vacío equivale a {@link #OTRA}
     * @return palabra clave reconocida, o {@link #OTRA}
     */
    public static PalabraClave de(String texto) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }
}
