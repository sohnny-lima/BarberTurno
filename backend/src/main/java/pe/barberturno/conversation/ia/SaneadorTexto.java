package pe.barberturno.conversation.ia;

import java.util.regex.Pattern;

/**
 * Sustituye por {@code [dato]} los teléfonos y correos del texto de un cliente antes de guardarlo en un turno o de
 * enviarlo al modelo (17-v2-12 §7; V2-RNF-05). Conservador: prefiere ocultar de más (un número de 7 o más dígitos,
 * aunque no fuera un teléfono) que enviar un dato personal al proveedor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.2
 */
public final class SaneadorTexto {
    /** Sustituto visible para el modelo. */
    public static final String DATO = "[dato]";
    private static final Pattern CORREO = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");
    /**
     * 7 o más dígitos con separadores sin límite entre ellos (p. ej. «987 -  654 -  321») y un {@code +} inicial
     * opcional. Separadores: espacios (también los Unicode, como U+00A0 o U+202F), guiones (también U+2011, U+2013
     * o el signo menos U+2212), caracteres de formato invisibles (U+200B, U+2060, U+FEFF…), puntos y paréntesis. Con
     * {@code UNICODE_CHARACTER_CLASS} también cuentan los dígitos de otras escrituras (revisiones 129 y 133).
     * La clase de separadores no contiene dígitos: cada dígito delimita una secuencia, sin particiones ambiguas
     * entre repeticiones ni retroceso catastrófico (revisión 136).
     */
    private static final Pattern TELEFONO = Pattern.compile(
            "\\(?\\+?\\(?\\d(?:[\\s\\p{Zs}\\p{Pd}\\p{Cf}\\u2212().]*\\d){6,}", Pattern.UNICODE_CHARACTER_CLASS);

    private SaneadorTexto() { }

    /**
     * Dice si el texto contiene un teléfono o un correo según los mismos patrones con los que se sanea la entrada,
     * para que las guardas de salida no diverjan del saneador (V2-DA-85).
     * @param texto texto a examinar; nulo equivale a vacío
     * @return verdadero si hay algún teléfono o correo
     */
    public static boolean contieneDatoPersonal(String texto) {
        return texto != null && (TELEFONO.matcher(texto).find() || CORREO.matcher(texto).find());
    }

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
