package pe.barberturno.conversation.ia;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import pe.barberturno.common.time.TiempoNegocio;

/**
 * Red de seguridad determinista y sin estado sobre el texto del modelo de lenguaje antes de enviarlo
 * (17-v2-12 §5; V2-RN-11 y V2-RN-12). Aplica las reglas en orden estricto: vacío, longitud máxima,
 * datos prohibidos, afirmaciones de operación y horarios no ofrecidos.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class GuardasSalida {

    private static final Locale LOCALE_PE = Locale.forLanguageTag("es-PE");
    private static final int LONGITUD_MAXIMA_CODEPOINTS = 1024;

    private static final Pattern PATRON_URL = Pattern.compile("(?i)(https?://|www\\.)");
    private static final Pattern PATRON_CORREO =
            Pattern.compile("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PATRON_CODIGO_BT = Pattern.compile("(?i)\\bBT-\\d+\\b");
    private static final Pattern PATRON_ALIAS_INTERNO = Pattern.compile("(?i)\\b[spcf]\\d+\\b");

    private static final Pattern PATRON_CANDIDATO_TELEFONO =
            Pattern.compile("(?<![\\w+])\\+?[0-9][0-9\\s\\-+]{5,}[0-9](?![\\w])");
    private static final Pattern PATRON_FECHA_GUIONES =
            Pattern.compile("^(?:\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2}-\\d{4})$");

    private static final Pattern PATRON_PRECIO =
            Pattern.compile("(?i)(?:\\bS/\\.?\\s*\\d+(?:[.,]\\d+)?\\b|\\b\\d+(?:[.,]\\d+)?\\s*soles\\b)");
    private static final Pattern PATRON_DURACION =
            Pattern.compile("(?i)\\b\\d+\\s*(?:min|minutos|hora|horas)\\b");

    private static final Pattern PATRON_AFIRMACION_EXPLICITA = Pattern.compile(
            "\\b(?:listo[^a-z0-9]+tu\\s+cita|te\\s+esperamos\\s+el|(?:he|hemos)\\s+reservado|"
            + "(?:qued[ao]|est[ao])\\s+reservad[ao]s?|confirmada[s]?|"
            + "(?:qued[ao]|est[ao])\\s+confirmad[ao]s?|(?:qued[ao]|est[ao]|he|hemos)?\\s*agendad[ao]s?|"
            + "(?:qued[ao]|est[ao])\\s+cancelad[ao]s?|(?:qued[ao]|est[ao])\\s+reprogramad[ao]s?)\\b"
    );

    private static final Pattern PATRON_HORA_CONJUNCION = Pattern.compile(
            "^\\s*(?:[oy]|u)\\s+(\\d{1,2}|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)\\b"
    );

    private static final Pattern PATRON_VERBO_OPERACION =
            Pattern.compile("\\b(reserve|cancele|reprograme|agende)\\b");

    private static final Pattern PATRON_SUBJUNTIVO_PREFIJO = Pattern.compile(
            ".*\\b(?:que|para|deseas\\s+que|quieres\\s+que|gustaria\\s+que)(\\s+(?:lo|la|te|se|me|nos|le|les))*$"
    );

    private static final Pattern PATRON_FECHA_PASADO_MANANA = Pattern.compile("\\bpasado\\s+manana\\b");
    private static final Pattern PATRON_FECHA_HOY = Pattern.compile("\\bhoy\\b");
    private static final Pattern PATRON_FECHA_MANANA =
            Pattern.compile("(?<!\\bpasado\\s)(?<!(?:de|en)\\s+la\\s)\\bmanana\\b");
    private static final Pattern PATRON_FECHA_DIA_SEMANA =
            Pattern.compile("\\b(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b");
    private static final Pattern PATRON_FECHA_NUMERICA =
            Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?\\b");
    private static final Pattern PATRON_FECHA_TEXTO = Pattern.compile(
            "\\b(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|setiembre|septiembre|octubre|noviembre|diciembre)(?:\\s+de\\s+(\\d{4}))?\\b"
    );

    private static final Pattern PATRON_HORA_MEDIODIA = Pattern.compile("\\bmediodia\\b");
    private static final Pattern PATRON_HORA_MEDIANOCHE = Pattern.compile("\\bmedianoche\\b");
    private static final Pattern PATRON_HORA_DIGITAL =
            Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?:\\s*(am|pm))?(?!\\d)");
    private static final Pattern PATRON_HORA_AM_PM =
            Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(am|pm)\\b");
    private static final Pattern PATRON_HORA_FRACCION = Pattern.compile(
            "\\b(?:(?:a\\s+las|a\\s+la|de\\s+las|de\\s+la|para\\s+las|para\\s+la|las|la)\\s+)?(\\d{1,2}|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)\\s+y\\s+(media|cuarto|treinta|quince)(?:\\s+de\\s+la\\s+(tarde|noche|manana))?\\b"
    );
    private static final Pattern PATRON_HORA_MOMENTO = Pattern.compile(
            "\\b(?:(?:a\\s+las|a\\s+la|de\\s+las|de\\s+la|para\\s+las|para\\s+la|las|la)\\s+)?(\\d{1,2}|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)\\s+de\\s+la\\s+(tarde|noche|manana)\\b"
    );
    private static final Pattern PATRON_HORA_A_LAS = Pattern.compile(
            "\\b(?:a\\s+las|a\\s+la|de\\s+las|de\\s+la|para\\s+las|para\\s+la|desde\\s+las|hasta\\s+las|las)\\s+(\\d{1,2}|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)\\b"
    );

    /** Constructor sin dependencias, usado por Spring y por las pruebas. */
    public GuardasSalida() { }

    /**
     * Revisa el texto del modelo aplicando las guardas de seguridad en el orden definido por 17-v2-12 §5:
     * vacío, demasiado largo, datos prohibidos, afirmación de operación y horario no ofrecido.
     *
     * @param texto texto generado por el modelo; puede ser nulo o vacío
     * @param franjas franjas ofrecidas en este turno en Lima; no nula (lista vacía si no hay)
     * @param ahora fecha y hora actuales en Lima para anclar referencias relativas; no nula
     * @return resultado aceptado con el texto intacto o sustituido con el motivo de rechazo
     */
    public ResultadoGuardas revisar(String texto, List<FranjaOfrecida> franjas, ZonedDateTime ahora) {
        if (texto == null || texto.isBlank()) {
            return new ResultadoGuardas.Sustituido(MotivoGuarda.VACIO);
        }

        if (texto.codePointCount(0, texto.length()) > LONGITUD_MAXIMA_CODEPOINTS) {
            return new ResultadoGuardas.Sustituido(MotivoGuarda.DEMASIADO_LARGO);
        }

        if (contieneDatosProhibidos(texto)) {
            return new ResultadoGuardas.Sustituido(MotivoGuarda.DATOS_PROHIBIDOS);
        }

        if (contieneAfirmacionOperacion(texto)) {
            return new ResultadoGuardas.Sustituido(MotivoGuarda.AFIRMA_OPERACION);
        }

        List<FranjaOfrecida> franjasList = (franjas != null) ? franjas : List.of();
        Objects.requireNonNull(ahora, "ahora");
        ZonedDateTime ahoraLima = ahora.withZoneSameInstant(TiempoNegocio.ZONA);

        if (contieneHorarioNoOfrecido(texto, franjasList, ahoraLima)) {
            return new ResultadoGuardas.Sustituido(MotivoGuarda.HORARIO_NO_OFRECIDO);
        }

        return new ResultadoGuardas.Aceptado(texto);
    }

    private boolean contieneDatosProhibidos(String texto) {
        if (PATRON_URL.matcher(texto).find()) {
            return true;
        }
        if (PATRON_CORREO.matcher(texto).find()) {
            return true;
        }
        if (PATRON_CODIGO_BT.matcher(texto).find()) {
            return true;
        }
        if (PATRON_ALIAS_INTERNO.matcher(texto).find()) {
            return true;
        }
        return contieneTelefono(texto);
    }

    private boolean contieneTelefono(String texto) {
        Matcher m = PATRON_CANDIDATO_TELEFONO.matcher(texto);
        while (m.find()) {
            String match = m.group();
            if (PATRON_FECHA_GUIONES.matcher(match).matches()) {
                continue;
            }
            int digitos = 0;
            for (int i = 0; i < match.length(); i++) {
                char c = match.charAt(i);
                if (c >= '0' && c <= '9') {
                    digitos++;
                }
            }
            if (digitos >= 7) {
                return true;
            }
        }
        return false;
    }

    private boolean contieneAfirmacionOperacion(String texto) {
        String norm = normalizar(texto);
        if (PATRON_AFIRMACION_EXPLICITA.matcher(norm).find()) {
            return true;
        }

        Matcher m = PATRON_VERBO_OPERACION.matcher(norm);
        while (m.find()) {
            int idx = m.start();
            String prefix = norm.substring(Math.max(0, idx - 40), idx).trim();
            if (esSubjuntivoPropuesta(prefix)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean esSubjuntivoPropuesta(String prefix) {
        if (prefix.isEmpty()) {
            return false;
        }
        return PATRON_SUBJUNTIVO_PREFIJO.matcher(prefix).matches();
    }

    private boolean contieneHorarioNoOfrecido(String texto, List<FranjaOfrecida> franjas, ZonedDateTime ahoraLima) {
        Set<LocalDate> diasOfrecidos = new HashSet<>();
        Map<LocalDate, Set<LocalTime>> iniciosPorDia = new HashMap<>();
        for (FranjaOfrecida f : franjas) {
            LocalDate d = TiempoNegocio.fechaLima(f.inicio());
            LocalTime t = TiempoNegocio.aLima(f.inicio()).toLocalTime();
            diasOfrecidos.add(d);
            iniciosPorDia.computeIfAbsent(d, k -> new HashSet<>()).add(t);
        }

        String[] oraciones = texto.split("[.!?;\\n]+");
        for (String oracion : oraciones) {
            if (oracion.isBlank()) {
                continue;
            }
            ResultadoAnalisisOracion analisis = analizarOracion(oracion, ahoraLima);
            if (analisis.tieneMenciones()) {
                if (franjas.isEmpty()) {
                    return true;
                }
                if (!analisis.esValido(diasOfrecidos, iniciosPorDia, ahoraLima.toLocalDate())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ResultadoAnalisisOracion analizarOracion(String oracion, ZonedDateTime ahoraLima) {
        String norm = normalizar(oracion);

        List<Intervalo> exclusiones = new ArrayList<>();
        marcarExclusiones(norm, exclusiones);

        List<MencionFecha> fechas = extraerFechas(norm, ahoraLima, exclusiones);
        for (MencionFecha mf : fechas) {
            exclusiones.add(new Intervalo(mf.inicio(), mf.fin()));
        }

        List<MencionHora> horas = extraerHoras(norm, exclusiones);

        return new ResultadoAnalisisOracion(fechas, horas);
    }

    private void marcarExclusiones(String norm, List<Intervalo> exclusiones) {
        Matcher mPrecio = PATRON_PRECIO.matcher(norm);
        while (mPrecio.find()) {
            exclusiones.add(new Intervalo(mPrecio.start(), mPrecio.end()));
        }
        Matcher mDuracion = PATRON_DURACION.matcher(norm);
        while (mDuracion.find()) {
            exclusiones.add(new Intervalo(mDuracion.start(), mDuracion.end()));
        }
    }

    private List<MencionFecha> extraerFechas(String norm, ZonedDateTime ahoraLima, List<Intervalo> exclusiones) {
        List<MencionFecha> fechas = new ArrayList<>();

        Matcher mPasadoManana = PATRON_FECHA_PASADO_MANANA.matcher(norm);
        while (mPasadoManana.find()) {
            if (!solapa(mPasadoManana.start(), mPasadoManana.end(), exclusiones)) {
                fechas.add(new MencionFecha(mPasadoManana.start(), mPasadoManana.end(),
                        List.of(ahoraLima.toLocalDate().plusDays(2))));
            }
        }

        Matcher mHoy = PATRON_FECHA_HOY.matcher(norm);
        while (mHoy.find()) {
            if (!solapa(mHoy.start(), mHoy.end(), exclusiones)) {
                fechas.add(new MencionFecha(mHoy.start(), mHoy.end(), List.of(ahoraLima.toLocalDate())));
            }
        }

        Matcher mManana = PATRON_FECHA_MANANA.matcher(norm);
        while (mManana.find()) {
            if (!solapa(mManana.start(), mManana.end(), exclusiones)) {
                fechas.add(new MencionFecha(mManana.start(), mManana.end(),
                        List.of(ahoraLima.toLocalDate().plusDays(1))));
            }
        }

        Matcher mSemana = PATRON_FECHA_DIA_SEMANA.matcher(norm);
        while (mSemana.find()) {
            if (!solapa(mSemana.start(), mSemana.end(), exclusiones)) {
                DayOfWeek dow = parsearDiaSemana(mSemana.group(1));
                if (dow != null) {
                    List<LocalDate> candidatas = resolverDiaSemana(dow, ahoraLima.toLocalDate());
                    fechas.add(new MencionFecha(mSemana.start(), mSemana.end(), candidatas));
                }
            }
        }

        Matcher mNum = PATRON_FECHA_NUMERICA.matcher(norm);
        while (mNum.find()) {
            if (!solapa(mNum.start(), mNum.end(), exclusiones)) {
                LocalDate d = parsearFechaNumerica(mNum.group(1), mNum.group(2), mNum.group(3), ahoraLima.getYear());
                if (d != null) {
                    fechas.add(new MencionFecha(mNum.start(), mNum.end(), List.of(d)));
                }
            }
        }

        Matcher mTexto = PATRON_FECHA_TEXTO.matcher(norm);
        while (mTexto.find()) {
            if (!solapa(mTexto.start(), mTexto.end(), exclusiones)) {
                LocalDate d = parsearFechaTexto(mTexto.group(1), mTexto.group(2), mTexto.group(3), ahoraLima.getYear());
                if (d != null) {
                    fechas.add(new MencionFecha(mTexto.start(), mTexto.end(), List.of(d)));
                }
            }
        }

        return fechas;
    }

    private List<MencionHora> extraerHoras(String norm, List<Intervalo> exclusiones) {
        List<MencionHora> horas = new ArrayList<>();
        List<Intervalo> usadas = new ArrayList<>(exclusiones);

        Matcher mMediodia = PATRON_HORA_MEDIODIA.matcher(norm);
        while (mMediodia.find()) {
            if (!solapa(mMediodia.start(), mMediodia.end(), usadas)) {
                horas.add(new MencionHora(mMediodia.start(), mMediodia.end(), List.of(LocalTime.of(12, 0))));
                usadas.add(new Intervalo(mMediodia.start(), mMediodia.end()));
            }
        }

        Matcher mMedianoche = PATRON_HORA_MEDIANOCHE.matcher(norm);
        while (mMedianoche.find()) {
            if (!solapa(mMedianoche.start(), mMedianoche.end(), usadas)) {
                horas.add(new MencionHora(mMedianoche.start(), mMedianoche.end(), List.of(LocalTime.of(0, 0))));
                usadas.add(new Intervalo(mMedianoche.start(), mMedianoche.end()));
            }
        }

        Matcher mFraccion = PATRON_HORA_FRACCION.matcher(norm);
        while (mFraccion.find()) {
            if (!solapa(mFraccion.start(), mFraccion.end(), usadas)) {
                Integer h = parsearNumeroPalabra(mFraccion.group(1));
                if (h != null && h >= 1 && h <= 12) {
                    int min = ("media".equals(mFraccion.group(2)) || "treinta".equals(mFraccion.group(2))) ? 30 : 15;
                    String periodo = mFraccion.group(3);
                    List<LocalTime> c = resolverHoras(h, min, periodo);
                    horas.add(new MencionHora(mFraccion.start(), mFraccion.end(), c));
                    usadas.add(new Intervalo(mFraccion.start(), mFraccion.end()));
                }
            }
        }

        Matcher mMomento = PATRON_HORA_MOMENTO.matcher(norm);
        while (mMomento.find()) {
            if (!solapa(mMomento.start(), mMomento.end(), usadas)) {
                Integer h = parsearNumeroPalabra(mMomento.group(1));
                if (h != null && h >= 1 && h <= 12) {
                    String periodo = mMomento.group(2);
                    List<LocalTime> c = resolverHoras(h, 0, periodo);
                    horas.add(new MencionHora(mMomento.start(), mMomento.end(), c));
                    usadas.add(new Intervalo(mMomento.start(), mMomento.end()));
                }
            }
        }

        Matcher mDigital = PATRON_HORA_DIGITAL.matcher(norm);
        while (mDigital.find()) {
            if (!solapa(mDigital.start(), mDigital.end(), usadas)) {
                int h = Integer.parseInt(mDigital.group(1));
                int min = Integer.parseInt(mDigital.group(2));
                if (h >= 0 && h <= 23 && min >= 0 && min <= 59) {
                    String ampm = mDigital.group(3);
                    List<LocalTime> c = resolverHoras(h, min, ampm);
                    horas.add(new MencionHora(mDigital.start(), mDigital.end(), c));
                    usadas.add(new Intervalo(mDigital.start(), mDigital.end()));
                }
            }
        }

        Matcher mAmPm = PATRON_HORA_AM_PM.matcher(norm);
        while (mAmPm.find()) {
            if (!solapa(mAmPm.start(), mAmPm.end(), usadas)) {
                int h = Integer.parseInt(mAmPm.group(1));
                if (h >= 1 && h <= 12) {
                    String ampm = mAmPm.group(2);
                    List<LocalTime> c = resolverHoras(h, 0, ampm);
                    horas.add(new MencionHora(mAmPm.start(), mAmPm.end(), c));
                    usadas.add(new Intervalo(mAmPm.start(), mAmPm.end()));
                }
            }
        }

        Matcher mALas = PATRON_HORA_A_LAS.matcher(norm);
        while (mALas.find()) {
            if (!solapa(mALas.start(), mALas.end(), usadas)) {
                Integer h = parsearNumeroPalabra(mALas.group(1));
                if (h != null && h >= 0 && h <= 23) {
                    List<LocalTime> c = resolverHoras(h, 0, null);
                    horas.add(new MencionHora(mALas.start(), mALas.end(), c));
                    usadas.add(new Intervalo(mALas.start(), mALas.end()));

                    Matcher mExtra = PATRON_HORA_CONJUNCION.matcher(norm.substring(mALas.end()));
                    if (mExtra.lookingAt()) {
                        Integer hExtra = parsearNumeroPalabra(mExtra.group(1));
                        if (hExtra != null && hExtra >= 0 && hExtra <= 23) {
                            int extraStart = mALas.end() + mExtra.start(1);
                            int extraEnd = mALas.end() + mExtra.end(1);
                            if (!solapa(extraStart, extraEnd, usadas)) {
                                horas.add(new MencionHora(extraStart, extraEnd, resolverHoras(hExtra, 0, null)));
                                usadas.add(new Intervalo(extraStart, extraEnd));
                            }
                        }
                    }
                }
            }
        }

        return horas;
    }

    private List<LocalTime> resolverHoras(int h, int min, String periodo) {
        if ("pm".equalsIgnoreCase(periodo) || "tarde".equalsIgnoreCase(periodo) || "noche".equalsIgnoreCase(periodo)) {
            int horaReal = (h < 12) ? h + 12 : h;
            return List.of(LocalTime.of(horaReal, min));
        }
        if ("am".equalsIgnoreCase(periodo) || "manana".equalsIgnoreCase(periodo)) {
            int horaReal = (h == 12) ? 0 : h;
            return List.of(LocalTime.of(horaReal, min));
        }
        if (h >= 1 && h <= 11) {
            return List.of(LocalTime.of(h, min), LocalTime.of(h + 12, min));
        }
        return List.of(LocalTime.of(h, min));
    }

    private List<LocalDate> resolverDiaSemana(DayOfWeek dow, LocalDate hoy) {
        if (dow == hoy.getDayOfWeek()) {
            return List.of(hoy, hoy.plusDays(7));
        }
        int diff = (dow.getValue() - hoy.getDayOfWeek().getValue() + 7) % 7;
        return List.of(hoy.plusDays(diff));
    }

    private DayOfWeek parsearDiaSemana(String texto) {
        return switch (texto.toLowerCase(LOCALE_PE)) {
            case "lunes" -> DayOfWeek.MONDAY;
            case "martes" -> DayOfWeek.TUESDAY;
            case "miercoles" -> DayOfWeek.WEDNESDAY;
            case "jueves" -> DayOfWeek.THURSDAY;
            case "viernes" -> DayOfWeek.FRIDAY;
            case "sabado" -> DayOfWeek.SATURDAY;
            case "domingo" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private LocalDate parsearFechaNumerica(String diaStr, String mesStr, String anioStr, int anioPorDefecto) {
        try {
            int d = Integer.parseInt(diaStr);
            int m = Integer.parseInt(mesStr);
            int a = (anioStr != null) ? Integer.parseInt(anioStr) : anioPorDefecto;
            return LocalDate.of(a, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parsearFechaTexto(String diaStr, String mesStr, String anioStr, int anioPorDefecto) {
        try {
            int d = Integer.parseInt(diaStr);
            int m = switch (mesStr.toLowerCase(LOCALE_PE)) {
                case "enero" -> 1;
                case "febrero" -> 2;
                case "marzo" -> 3;
                case "abril" -> 4;
                case "mayo" -> 5;
                case "junio" -> 6;
                case "julio" -> 7;
                case "agosto" -> 8;
                case "setiembre", "septiembre" -> 9;
                case "octubre" -> 10;
                case "noviembre" -> 11;
                case "diciembre" -> 12;
                default -> 0;
            };
            if (m == 0) {
                return null;
            }
            int a = (anioStr != null) ? Integer.parseInt(anioStr) : anioPorDefecto;
            return LocalDate.of(a, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parsearNumeroPalabra(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return switch (texto.toLowerCase(LOCALE_PE)) {
                case "una", "uno" -> 1;
                case "dos" -> 2;
                case "tres" -> 3;
                case "cuatro" -> 4;
                case "cinco" -> 5;
                case "seis" -> 6;
                case "siete" -> 7;
                case "ocho" -> 8;
                case "nueve" -> 9;
                case "diez" -> 10;
                case "once" -> 11;
                case "doce" -> 12;
                default -> null;
            };
        }
    }

    private boolean solapa(int start, int end, List<Intervalo> exclusiones) {
        for (Intervalo inv : exclusiones) {
            if (Math.max(start, inv.inicio) < Math.min(end, inv.fin)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String nfd = Normalizer.normalize(s.toLowerCase(LOCALE_PE), Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}", "");
    }

    private record Intervalo(int inicio, int fin) { }

    private record MencionFecha(int inicio, int fin, List<LocalDate> candidatas) { }

    private record MencionHora(int inicio, int fin, List<LocalTime> candidatas) { }

    private static class ResultadoAnalisisOracion {
        private final List<MencionFecha> fechas;
        private final List<MencionHora> horas;

        ResultadoAnalisisOracion(List<MencionFecha> fechas, List<MencionHora> horas) {
            this.fechas = fechas != null ? fechas : Collections.emptyList();
            this.horas = horas != null ? horas : Collections.emptyList();
        }

        boolean tieneMenciones() {
            return !fechas.isEmpty() || !horas.isEmpty();
        }

        boolean esValido(Set<LocalDate> diasOfrecidos, Map<LocalDate, Set<LocalTime>> iniciosPorDia, LocalDate hoy) {
            if (!horas.isEmpty()) {
                List<List<LocalDate>> fechasAsociadas = new ArrayList<>();
                if (!fechas.isEmpty()) {
                    for (MencionFecha mf : fechas) {
                        fechasAsociadas.add(mf.candidatas());
                    }
                } else {
                    fechasAsociadas.add(List.of(hoy));
                }

                for (MencionHora mh : horas) {
                    for (List<LocalDate> grupoFechas : fechasAsociadas) {
                        boolean coincideCombinacion = false;
                        for (LocalDate d : grupoFechas) {
                            Set<LocalTime> inicios = iniciosPorDia.getOrDefault(d, Collections.emptySet());
                            for (LocalTime t : mh.candidatas()) {
                                if (inicios.contains(t)) {
                                    coincideCombinacion = true;
                                    break;
                                }
                            }
                            if (coincideCombinacion) {
                                break;
                            }
                        }
                        if (!coincideCombinacion) {
                            return false;
                        }
                    }
                }
                return true;
            }

            if (!fechas.isEmpty()) {
                for (MencionFecha mf : fechas) {
                    boolean coincideDia = false;
                    for (LocalDate d : mf.candidatas()) {
                        if (diasOfrecidos.contains(d)) {
                            coincideDia = true;
                            break;
                        }
                    }
                    if (!coincideDia) {
                        return false;
                    }
                }
                return true;
            }

            return true;
        }
    }
}
