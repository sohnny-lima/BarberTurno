package pe.barberturno.conversation.ia;

import java.util.regex.Pattern;

/**
 * Sustituye por {@code [dato]} los teléfonos y correos del texto de un cliente antes de guardarlo en un turno o de
 * enviarlo al modelo (17-v2-12 §7; V2-RNF-05). Conservador: prefiere ocultar de más (un número de 7 o más dígitos,
 * aunque no fuera un teléfono) que enviar un dato personal al proveedor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class SaneadorTexto {
    /** Sustituto visible para el modelo. */
    public static final String DATO = "[dato]";
    private static final Pattern CORREO = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");
    /** 7 o más dígitos con hasta dos separadores entre ellos (espacio, guion, punto, paréntesis) y un {@code +} inicial opcional. */
    private static final Pattern TELEFONO = Pattern.compile("\\+?\\(?\\d(?:[\\s().-]{0,2}\\d){6,}");

    private SaneadorTexto() { }

    /**
     * Devuelve el texto con los correos y teléfonos sustituidos y recortado a {@code maximo} puntos de código.
     * @param texto texto del cliente; nulo equivale a vacío
     * @param maximo longitud máxima, en puntos de código
     * @return texto saneado
     * @throws IllegalArgumentException si {@code maximo} es menor que 1
     */
    public static String sanear(String texto, int maximo) {
        if (maximo < 1) throw new IllegalArgumentException("Longitud máxima no válida.");
        if (texto == null) return "";
        var limpio = TELEFONO.matcher(CORREO.matcher(texto).replaceAll(DATO)).replaceAll(DATO).strip();
        return limpio.codePointCount(0, limpio.length()) <= maximo ? limpio
                : limpio.substring(0, limpio.offsetByCodePoints(0, maximo));
    }
}
