package pe.barberturno.conversation.ia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.TextosConversacion;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Respuestas que compone el servidor en un turno de IA (17-v2-12 §4.4 y §5): la lista interactiva de
 * franjas de varios días y los textos fijos del servidor. Aplica los límites de longitud de Meta de V2-10
 * recortando con «…».
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class ComposicionTurnoIA {

    private static final int LIMITE_CUERPO = 1024;
    private static final int LIMITE_BOTON_LISTA = 20;
    private static final int LIMITE_TITULO_SECCION = 24;
    private static final int LIMITE_TITULO_FILA = 24;
    private static final int LIMITE_DESCRIPCION_FILA = 72;
    private static final int LIMITE_FILAS_TOTAL = 10;

    private static final Locale LOCALE_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FORMATO_DIA_MES =
            DateTimeFormatter.ofPattern("dd/MM", LOCALE_PE);

    private final TextosConversacion textos = new TextosConversacion();

    /** Constructor sin dependencias, usado por Spring y por las pruebas. */
    public ComposicionTurnoIA() { }

    /**
     * Lista interactiva de franjas agrupadas por día (con título de sección corto en Lima, por ejemplo
     * «vie 23/10», y descripción de cada fila con la fecha completa con el año y el profesional, por ejemplo
     * «viernes 23 de octubre de 2026 · Carlos»), con como máximo 10 filas en total; el identificador de
     * cada fila es la selección de V2-10 de la franja. Si la descripción excede los 72 caracteres de Meta,
     * se recorta únicamente el nombre del profesional con «…», manteniendo intacta la fecha completa.
     *
     * @param cuerpo texto del mensaje (el del modelo si pasó las guardas, o un texto neutro)
     * @param franjas franjas del turno, en orden; si hay más de 10, solo las 10 primeras
     * @param hoy fecha actual de hoy en Lima
     * @return objeto interactivo de lista de Meta
     * @throws IllegalArgumentException si {@code franjas} es nula o vacía
     * @throws NullPointerException si {@code hoy} es nulo
     */
    public ObjectNode franjas(String cuerpo, List<FranjaOfrecida> franjas, LocalDate hoy) {
        if (franjas == null || franjas.isEmpty()) {
            throw new IllegalArgumentException("No hay franjas disponibles para componer la lista.");
        }
        Objects.requireNonNull(hoy, "hoy");

        ObjectNode raiz = JsonNodeFactory.instance.objectNode();
        raiz.put("type", "list");
        raiz.putObject("body").put("text", recortar(cuerpo != null ? cuerpo : "", LIMITE_CUERPO));

        ObjectNode action = raiz.putObject("action");
        action.put("button", recortar("Ver horarios", LIMITE_BOTON_LISTA));

        ArrayNode sections = action.putArray("sections");

        int limite = Math.min(franjas.size(), LIMITE_FILAS_TOTAL);
        List<FranjaOfrecida> primeras = franjas.subList(0, limite);

        Map<LocalDate, List<FranjaOfrecida>> porDia = new LinkedHashMap<>();
        for (FranjaOfrecida f : primeras) {
            LocalDate dia = TiempoNegocio.fechaLima(f.inicio());
            porDia.computeIfAbsent(dia, d -> new ArrayList<>()).add(f);
        }

        for (Map.Entry<LocalDate, List<FranjaOfrecida>> entry : porDia.entrySet()) {
            ObjectNode section = sections.addObject();
            section.put("title", recortar(tituloSeccion(entry.getKey()), LIMITE_TITULO_SECCION));
            String fechaCompleta = textos.fecha(entry.getKey());
            ArrayNode rows = section.putArray("rows");
            for (FranjaOfrecida f : entry.getValue()) {
                ObjectNode row = rows.addObject();
                row.put("id", f.seleccion().codificar());
                row.put("title", recortar(textos.hora(f.inicio()), LIMITE_TITULO_FILA));
                row.put("description", descripcionFila(fechaCompleta, f.profesional()));
            }
        }

        return raiz;
    }

    /**
     * Texto neutro que acompaña a la composición de franjas cuando las guardas descartan el texto del modelo.
     *
     * @return texto neutro
     */
    public String neutro() {
        return "Te muestro las opciones disponibles.";
    }

    /**
     * Texto emitido cuando el modelo preparó dos operaciones distintas: se pide al cliente que elija una sola.
     *
     * @return texto de aclaración
     */
    public String aclararPropuestas() {
        return "Por favor, elige una sola opción para continuar.";
    }

    /**
     * Texto emitido cuando el turno de IA falló: se invita al cliente a continuar con las opciones del menú.
     *
     * @return texto de fallo
     */
    public String fallo() {
        return "No pude entenderte bien; elige una opción del menú.";
    }

    /**
     * Texto fijo emitido cuando la consulta del cliente está fuera del alcance de reservas y citas (17-v2-12 §13.3).
     *
     * @return texto fijo de alcance
     */
    public String fueraDeAlcance() {
        return "Puedo ayudarte a reservar, cambiar o cancelar citas. Elige una opción:";
    }

    /**
     * Texto fijo del servidor que encabeza la lista interactiva de franjas disponibles en un turno de IA
     * (17-v2-12 §13.3; V2-DA-86).
     *
     * @return texto fijo para elegir horario
     */
    public String elegirFranja() {
        return "Estos son los horarios disponibles. Elige uno:";
    }

    private static String tituloSeccion(LocalDate dia) {
        String diaSemana = dia.getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, LOCALE_PE)
                .replace(".", "")
                .toLowerCase(LOCALE_PE)
                .trim();
        return diaSemana + " " + dia.format(FORMATO_DIA_MES);
    }

    private static String descripcionFila(String fechaCompleta, String profesional) {
        String prof = profesional != null ? profesional : "";
        String prefijo = fechaCompleta + " · ";
        if (prefijo.length() + prof.length() <= LIMITE_DESCRIPCION_FILA) {
            return prefijo + prof;
        }
        int espacioProfesional = LIMITE_DESCRIPCION_FILA - prefijo.length();
        if (espacioProfesional <= 1) {
            return prefijo;
        }
        return prefijo + recortar(prof, espacioProfesional);
    }

    private static String recortar(String texto, int limiteMaximo) {
        if (texto == null) {
            return "";
        }
        if (limiteMaximo <= 0) {
            return "";
        }
        if (texto.length() <= limiteMaximo) {
            return texto;
        }
        if (limiteMaximo == 1) {
            return "…";
        }
        int corte = limiteMaximo - 1;
        if (corte > 0 && Character.isHighSurrogate(texto.charAt(corte - 1))) {
            corte--;
        }
        return texto.substring(0, corte) + "…";
    }
}
