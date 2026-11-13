package pe.barberturno.whatsapp;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;

/**
 * Catálogo de plantillas <em>utility</em> en español que el servidor puede enviar fuera de la ventana de
 * 24 h (V2-RN-13). El texto de cada una es el mismo que se somete a aprobación en Meta, con parámetros con
 * nombre; dentro de la ventana, {@link #textoEnVentana(Map)} lo compone como texto de servicio
 * (12-v2-09-whatsapp-salida.md §3.4).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum PlantillaUtility {
    /** Recordatorio de una cita (lo enviará V2-14, con <em>opt-in</em> vigente). */
    BT_RECORDATORIO_CITA("bt_recordatorio_cita", List.of("negocio", "fecha", "profesional", "codigo"),
            "Hola. Te recordamos tu cita en {{negocio}} el {{fecha}} con {{profesional}} (reserva {{codigo}})."),
    /** Cambio de una cita hecho por el negocio. */
    BT_CITA_MODIFICADA_POR_NEGOCIO("bt_cita_modificada_por_negocio", List.of("negocio", "codigo", "fecha", "profesional"),
            "{{negocio}} cambió tu reserva {{codigo}}: ahora es el {{fecha}} con {{profesional}}."),
    /** Cancelación de una cita hecha por el negocio. */
    BT_CITA_CANCELADA_POR_NEGOCIO("bt_cita_cancelada_por_negocio", List.of("negocio", "codigo", "fecha"),
            "{{negocio}} canceló tu reserva {{codigo}} del {{fecha}}. Escríbenos si quieres reservar otra hora.");

    /** Longitud máxima de cada valor de parámetro. */
    public static final int LONGITUD_MAXIMA_PARAMETRO = 200;
    private static final Pattern NO_PERMITIDO = Pattern.compile("[\\r\\n\\t]| {4,}");

    private final String nombre;
    private final List<String> parametros;
    private final String texto;

    PlantillaUtility(String nombre, List<String> parametros, String texto) {
        this.nombre = nombre;
        this.parametros = parametros;
        this.texto = texto;
    }

    /** {@return nombre exacto de la plantilla en la cuenta de WhatsApp del negocio} */
    public String nombre() {
        return nombre;
    }

    /** {@return nombres de los parámetros del cuerpo, en el orden en que aparecen en el texto} */
    public List<String> parametros() {
        return parametros;
    }

    /** {@return código de idioma con el que se aprueba y se envía la plantilla} */
    public String idioma() {
        return "es";
    }

    /**
     * Comprueba que los parámetros son exactamente los del catálogo y que cada valor es aceptable para
     * Meta: no en blanco, de 1 a {@value #LONGITUD_MAXIMA_PARAMETRO} caracteres, sin saltos de línea ni
     * tabuladores y sin más de tres espacios seguidos.
     * @param valores valores por nombre de parámetro
     * @throws NegocioException VALIDACION si falta, sobra o es inválido algún parámetro
     */
    public void validar(Map<String, String> valores) {
        if (valores == null || !valores.keySet().equals(Set.copyOf(parametros))) {
            throw new NegocioException(ErrorCodigo.VALIDACION,
                    "La plantilla " + nombre + " requiere exactamente los parámetros " + parametros + ".");
        }
        for (String parametro : parametros) {
            String valor = valores.get(parametro);
            if (valor == null || valor.isBlank() || valor.length() > LONGITUD_MAXIMA_PARAMETRO
                    || NO_PERMITIDO.matcher(valor).find()) {
                throw new NegocioException(ErrorCodigo.VALIDACION,
                        "El parámetro " + parametro + " de la plantilla " + nombre + " no es válido.");
            }
        }
    }

    /**
     * Compone el texto de la plantilla con sus valores, para enviarlo como mensaje de servicio cuando la
     * ventana de atención está abierta.
     * @param valores valores por nombre de parámetro
     * @return texto con cada {@code {{parametro}}} sustituido por su valor
     * @throws NegocioException VALIDACION si los valores no superan {@link #validar(Map)}
     */
    public String textoEnVentana(Map<String, String> valores) {
        validar(valores);
        String resultado = texto;
        for (String parametro : parametros) {
            resultado = resultado.replace("{{" + parametro + "}}", valores.get(parametro));
        }
        return resultado;
    }

    /**
     * Localiza una plantilla del catálogo por su nombre en Meta.
     * @param nombre nombre exacto, por ejemplo {@code bt_recordatorio_cita}
     * @return la plantilla del catálogo
     * @throws IllegalArgumentException si el nombre no pertenece al catálogo
     */
    public static PlantillaUtility porNombre(String nombre) {
        for (PlantillaUtility plantilla : values()) {
            if (plantilla.nombre.equals(nombre)) {
                return plantilla;
            }
        }
        throw new IllegalArgumentException("Plantilla fuera del catálogo.");
    }
}
