package pe.barberturno.conversation.ia;

import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Red determinista de seguridad del texto del modelo (V2-RN-11/12, V2-DA-85).
 * Rechaza datos personales, importes, enlaces, afirmaciones de operación y referencias temporales:
 * solo el servidor compone resultados y franjas. Prefiere sustituir texto de más ante una duda.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
public class GuardasSalida {
    private static final int LONGITUD_MAXIMA_CODEPOINTS = 1024;
    private static final Pattern URL = patron(
            "(?:\\b[a-z][a-z0-9+.-]*://|www\\.|\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}\\b|\\bmailto:)");
    private static final Pattern IDENTIFICADOR = patron("\\b(?:BT-\\d+|[spcf]\\d+)\\b");
    private static final Pattern IMPORTE = patron(
            "(?:\\bS/|\\b(?:soles|PEN)\\b|\\$|\\b\\d+(?:[.,]\\d+)?\\s*(?:sol|lucas)\\b)");
    // No se interpreta la fecha: solo evita confundirla con un teléfono en la guarda previa.
    private static final Pattern FECHA_NUMERICA = patron(
            "(?<!\\d)(?:\\d{4}\\s*[/\\p{Pd}−]\\s*\\d{1,2}\\s*[/\\p{Pd}−]\\s*\\d{1,2}"
            + "|\\d{1,2}\\s*[/\\p{Pd}−]\\s*\\d{1,2}(?:\\s*[/\\p{Pd}−]\\s*\\d{2,4})?)(?!\\d)");
    private static final String NUMERO_HABLADO =
            "(?:una?|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|"
            + "dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintidos|veintitres)";
    private static final Pattern REFERENCIA_TEMPORAL = patron(
            "\\p{N}|\\b(?:hoy|manana|ayer|anteayer|mediodia|medianoche|lunes|martes|miercoles|jueves|viernes|"
            + "sabado|domingo|enero|febrero|marzo|abril|mayo|junio|julio|agosto|setiembre|septiembre|octubre|"
            + "noviembre|diciembre|(?:proxim[ao]|siguiente|esta)\\s+(?:semana|mes|ano))\\b"
            + "|\\b(?:a\\s+las?|de\\s+las?|para\\s+las?|desde\\s+las?|hasta\\s+las?|las)\\s+" + NUMERO_HABLADO + "\\b"
            + "|\\b" + NUMERO_HABLADO + "\\s+(?:y\\s+|menos\\s+)?(?:media|cuarto|treinta|cuarenta|cincuenta|"
            + "(?:de|por)\\s+la\\s+(?:tarde|noche|manana)|[ap]\\s*\\.?\\s*m\\.?)\\b"
            + "|^\\s*[¿¡]?\\s*" + NUMERO_HABLADO + "\\s*[.!?…]*\\s*$");
    private static final Pattern PREGUNTA = patron("¿[^¿?]*\\?|[^.!?;,\\n¿]+\\?");
    private static final Pattern CLAUSULAS = patron(
            "[.!;:\\n,]+|\\b(?:pero|aunque|sin embargo|en cambio|ademas)\\b"
            + "|\\by\\s+(?=(?:ya\\s+)?(?:he|hemos|se|te|yo|nosotros|"
            + "(?:reserv|confirm|cancel|reprogram|agend)(?!ad[ao]s?\\b)\\w+)\\b)");
    private static final Pattern OPERACION = patron(
            "\\b(?:reserv|confirm|cancel|reprogram|agend|program)(?:o|as?|amos|ais|an|e|es|emos|eis|en|aste|asteis|aron|"
            + "aba|abas|abamos|abais|aban|are|aras|ara|aremos|areis|aran|aria|arias|ariamos|ariais|arian|"
            + "ase|ases|asemos|aseis|asen|aramos|arais|ando|ad[ao]s?)\\b");
    private static final Pattern NEGACION = patron(
            "\\b(?:no(?!\\s+sol[oa](?:mente)?\\b)|nunca|jamas|tampoco|"
            + "sin(?!\\s+(?:demoras?|problemas?|esperas?)\\b)|ningun[ao]?s?|ni)\\b");
    private static final Pattern PROPUESTA = patron(".*\\b(?:que|para)\\b[^.!?;,:]*");
    private static final Pattern SUBJUNTIVO = patron(
            "(?:reserv|confirm|cancel|reprogram|agend)(?:e|es|en|emos|eis|ara|aras|aramos|aran|ase|ases|asemos|asen)");
    private static final Pattern INDICATIVO_ACENTUADO = patron(
            "\\b(?:reserv|confirm|cancel|reprogram|agend)(?:[éó]|ar[áé](?:s|n|is)?|aría(?:s|mos|is|n)?)\\b");
    private static final Pattern RESULTADO = patron("\\b(?:listo[^a-z0-9]+tu\\s+cita|te\\s+esperamos\\s+el)\\b");
    private static final Pattern NOMBRE = patron(".*\\b(?:la|las|una|unas|tu|tus|su|sus|esta|estas|mi|mis|de)\\s+");
    private static final Pattern SUJETO_TU = patron("\\btú\\b");
    private static final Pattern INSTRUCCION = patron("(?:\\s*|.*\\b(?:y|favor)\\s+)");

    /** Constructor sin dependencias para Spring y las pruebas deterministas. */
    public GuardasSalida() { }

    /**
     * Aplica en orden las guardas de vacío, longitud, datos prohibidos, afirmaciones y referencias temporales.
     * Las preguntas y negaciones se exceptúan solo de afirmaciones; sus datos y horarios siguen prohibidos.
     *
     * @param texto salida del modelo; nulo equivale a vacío
     * @param franjas conservado por compatibilidad del contrato; no se usa ni se validan combinaciones
     * @param ahora conservado por compatibilidad del contrato; no se usa ni se resuelven fechas relativas
     * @return texto intacto si supera las guardas, o el motivo de su sustitución por composición del servidor
     */
    public ResultadoGuardas revisar(String texto, List<FranjaOfrecida> franjas, ZonedDateTime ahora) {
        if (texto == null || texto.isBlank()) return sustituir(MotivoGuarda.VACIO);
        if (texto.codePointCount(0, texto.length()) > LONGITUD_MAXIMA_CODEPOINTS) {
            return sustituir(MotivoGuarda.DEMASIADO_LARGO);
        }
        if (contieneDatosProhibidos(texto)) return sustituir(MotivoGuarda.DATOS_PROHIBIDOS);
        if (contieneAfirmacionOperacion(texto)) return sustituir(MotivoGuarda.AFIRMA_OPERACION);
        if (REFERENCIA_TEMPORAL.matcher(normalizar(texto)).find()) return sustituir(MotivoGuarda.HORARIO_NO_OFRECIDO);
        return new ResultadoGuardas.Aceptado(texto);
    }

    private boolean contieneDatosProhibidos(String texto) {
        if (URL.matcher(texto).find() || IDENTIFICADOR.matcher(texto).find() || IMPORTE.matcher(texto).find()) return true;
        String sinFechas = FECHA_NUMERICA.matcher(texto).replaceAll(" ");
        // Canoniza separadores espaciados; el único detector sigue siendo el del saneador.
        String compacto = sinFechas.replaceAll("(?U)\\s*([\\p{Pd}().])\\s*", "$1");
        return SaneadorTexto.contieneDatoPersonal(sinFechas) || SaneadorTexto.contieneDatoPersonal(compacto);
    }

    private boolean contieneAfirmacionOperacion(String texto) {
        String declarativo = PREGUNTA.matcher(texto).replaceAll(";");
        for (String clausula : CLAUSULAS.split(declarativo)) {
            String norm = normalizar(clausula);
            Matcher operaciones = OPERACION.matcher(norm);
            while (operaciones.find()) {
                String prefijo = norm.substring(0, operaciones.start());
                if (NEGACION.matcher(prefijo).find()) continue;
                String verbo = operaciones.group();
                // La tilde distingue «que reservé» del subjuntivo «que reserve».
                String original = Normalizer.normalize(clausula, Normalizer.Form.NFC);
                boolean indicativo = INDICATIVO_ACENTUADO.matcher(original).find();
                if (!indicativo && SUBJUNTIVO.matcher(verbo).matches() && PROPUESTA.matcher(prefijo).matches()) continue;
                if (verbo.matches("(?:reserv|agend)as?") && NOMBRE.matcher(prefijo).matches()
                        && !SUJETO_TU.matcher(original).find()) continue;
                if (verbo.matches("(?:reserv|confirm|cancel|reprogram|agend)a") && INSTRUCCION.matcher(prefijo).matches()) continue;
                return true;
            }
            Matcher resultado = RESULTADO.matcher(norm);
            while (resultado.find()) {
                if (!NEGACION.matcher(norm.substring(0, resultado.start())).find()) return true;
            }
        }
        return false;
    }

    private static Pattern patron(String expresion) {
        return Pattern.compile(expresion, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.forLanguageTag("es-PE")), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("(?U)\\s", " ");
    }

    private ResultadoGuardas sustituir(MotivoGuarda motivo) {
        return new ResultadoGuardas.Sustituido(motivo);
    }
}
