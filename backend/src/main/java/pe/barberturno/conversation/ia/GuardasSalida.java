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
 * Incluye estados completados sin verbo de reserva; las exenciones deben gobernar la operación examinada (g099).
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 2.1
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
            + "dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintidos|veintitres|veinticuatro|"
            + "veinticinco|veintiseis|veintisiete|veintiocho|veintinueve|treinta|cuarenta|cincuenta|sesenta)";
    private static final Pattern REFERENCIA_TEMPORAL = patron(
            "\\p{N}|\\b(?:hoy|manana|ayer|anteayer|mediodia|medianoche|lunes|martes|miercoles|jueves|viernes|"
            + "sabados?|domingos?|enero|febrero|marzo|abril|mayo|junio|julio|agosto|setiembre|septiembre|octubre|"
            + "noviembre|diciembre|(?:proxim[ao]|siguiente|est[ea])\\s+(?:semana|mes|ano)|temprano|mas\\s+tarde|"
            + "(?:esta|por\\s+la|en\\s+la|de\\s+la)\\s+(?:tarde|noche|manana))\\b"
            + "|\\b(?:a\\s+las?|de\\s+las?|para\\s+las?|desde\\s+las?|hasta\\s+las?|las?|el)\\s+" + NUMERO_HABLADO + "\\b"
            + "|\\b" + NUMERO_HABLADO + "\\s+(?:y\\s+|menos\\s+)?(?:media|cuarto|horas?|hs|hrs|" + NUMERO_HABLADO + "|"
            + "(?:de|por)\\s+la\\s+(?:tarde|noche|manana)|[ap]\\s*\\.?\\s*m\\.?)\\b"
            + "|^\\s*[¿¡]?\\s*" + NUMERO_HABLADO + "\\s*[.!?…]*\\s*$");
    private static final Pattern PREGUNTA = patron("¿[^¿?]*\\?|[^.!?;:,\\n¿]+\\?");
    private static final Pattern CLAUSULAS = patron(
            "[.!?;:\\n,]+|\\b(?:pero|aunque|sin embargo|en cambio|ademas)\\b");
    private static final String RAIZ_OPERACION = "(?:reserv|confirm|cancel|reprogram|agend|program|anot|registr|separ)";
    private static final Pattern OPERACION = patron(
            "\\b" + RAIZ_OPERACION + "(?:o|as?|amos|ais|an|e|es|emos|eis|en|aste|asteis|aron|"
            + "aba|abas|abamos|abais|aban|are|aras|ara|aremos|areis|aran|aria|arias|ariamos|ariais|arian|"
            + "ase|ases|asemos|aseis|asen|aramos|arais|ando|ad[ao]s?)\\b");
    private static final Pattern NEGACION = patron(
            "\\b(?:no(?!\\s+sol[oa](?:mente)?\\b)|nunca|jamas|tampoco|ni|ningun[ao]?s?)\\s+"
            + "(?:(?:te|se|me|lo|la|le|les|nos|os|he|has|ha|hemos|habeis|han|habia|habias|habiamos|habian|"
            + "hubiera|hubieran|hubiese|hubiesen|habre|habra|habran|habremos|esta|estan|estaba|estaban|estas|"
            + "estoy|estamos|tengo|tienes|tiene|tenemos|tienen|hay|existe|existen|aun|todavia|nada|"
            + "citas?|reservas?|turnos?|ningun[ao]?s?|las|los|el|una?|unas|tu|tus|su|sus|mi|mis|nuestras?|nuestros?)\\s+)*$"
            + "|\\bsin\\s+(?:haber\\s+)?(?:(?:citas?|reservas?|turnos?)\\s+)?$");
    private static final Pattern PROPUESTA = patron(
            ".*\\b(?:para\\s+que|(?:quieres|deseas|prefieres|gustaria|necesitas|puedes)\\s+que|para)\\b[^.!?;,:]*");
    private static final Pattern REALIZADO = patron("\\b(?:ya|hice|pedi)\\b");
    private static final Pattern SUBJUNTIVO = patron(
            RAIZ_OPERACION + "(?:e|es|en|emos|eis|ara|aras|aramos|aran|ase|ases|asemos|asen)");
    private static final Pattern INDICATIVO_ACENTUADO = patron(
            "\\b" + RAIZ_OPERACION + "(?:[éó]|ar[áé](?:s|n|is)?|aría(?:s|mos|is|n)?)\\b");
    private static final Pattern RESULTADO = patron(
            "\\b(?:ya\\s+(?:esta|quedo)|list[ao]s?|te\\s+esperamos|"
            + "(?:deje|dejamos|dejo|dejaron|dejaste)\\s+(?:(?:la|tu|el|un|una)\\s+)?(?:cita|turno|reserva)|"
            + "(?:esta|estan|quedo|queda|quedaron)\\s+en\\s+(?:(?:la|mi|nuestra|tu|su)\\s+)?agenda)\\b");
    private static final Pattern TEMA_CITA = patron("\\b(?:citas?|reservas?|turnos?)\\b");
    private static final Pattern ESTADO_CITA = patron(
            "\\b(?:esta(?!\\s+(?:tarde|noche|manana|semana|mes|ano)\\b)|estan|quedo|queda|quedaron)\\b");
    private static final String CONTEXTO_NOMBRE =
            "(?:^\\s*|.*\\b(?:con|de|para|sobre|en|a|ver|revisar|consultar|elegir|cambiar|elige|revisa|consulta)\\s+)";
    private static final Pattern NOMBRE_SINGULAR = patron(CONTEXTO_NOMBRE + "(?:la|una|tu|su|esta|mi|nuestra)\\s+");
    private static final Pattern NOMBRE_PLURAL = patron(CONTEXTO_NOMBRE + "(?:las|unas|tus|sus|estas|mis|nuestras)\\s+");
    private static final Pattern INSTRUCCION_PREVIA = patron("\\b(?:por\\s+favor|revisa|pulsa|elige)\\b");
    private static final Pattern INSTRUCCION_POSTERIOR = patron("\\b(?:con\\s+el\\s+boton|abajo|para\\s+continuar)\\b");
    private static final Pattern PARTICIPIO = patron(RAIZ_OPERACION + "ad[ao]s?");
    private static final Pattern COORDINACION_NEGADA = patron("\\s+(?:y|ni)\\s+");

    /** Constructor sin dependencias para Spring y las pruebas deterministas. */
    public GuardasSalida() { }

    /**
     * Aplica en orden las guardas de vacío, longitud, datos prohibidos, afirmaciones y referencias temporales.
     * Las preguntas y negaciones se exceptúan solo de afirmaciones; sus datos y horarios siguen prohibidos.
     * En caso ambiguo se sustituye; la detección de estados completados también evita promesas sin verbo de operación.
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
            int finNegada = -1;
            while (operaciones.find()) {
                String prefijo = norm.substring(0, operaciones.start());
                String verbo = operaciones.group();
                if (NEGACION.matcher(prefijo).find()
                        || (finNegada >= 0 && PARTICIPIO.matcher(verbo).matches()
                        && COORDINACION_NEGADA.matcher(norm.substring(finNegada, operaciones.start())).matches())) {
                    finNegada = operaciones.end();
                    continue;
                }
                finNegada = -1;
                // La tilde distingue «que reservé» del subjuntivo «que reserve».
                String original = Normalizer.normalize(clausula, Normalizer.Form.NFC);
                boolean indicativo = INDICATIVO_ACENTUADO.matcher(original).find();
                if (!indicativo && !REALIZADO.matcher(prefijo).find()
                        && SUBJUNTIVO.matcher(verbo).matches() && PROPUESTA.matcher(prefijo).matches()) continue;
                if (verbo.matches("(?:reserva|agenda)") && NOMBRE_SINGULAR.matcher(prefijo).matches()) continue;
                if (verbo.matches("(?:reservas|agendas)") && NOMBRE_PLURAL.matcher(prefijo).matches()) continue;
                if (verbo.matches(RAIZ_OPERACION + "a")
                        && (INSTRUCCION_PREVIA.matcher(prefijo).find()
                        || (prefijo.isBlank() && INSTRUCCION_POSTERIOR.matcher(norm.substring(operaciones.end())).find()))) continue;
                return true;
            }
            Matcher resultado = RESULTADO.matcher(norm);
            while (resultado.find()) {
                if (!NEGACION.matcher(norm.substring(0, resultado.start())).find()) return true;
            }
            if (TEMA_CITA.matcher(norm).find()) {
                Matcher estados = ESTADO_CITA.matcher(norm);
                while (estados.find()) {
                    if (!NEGACION.matcher(norm.substring(0, estados.start())).find()) return true;
                }
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
