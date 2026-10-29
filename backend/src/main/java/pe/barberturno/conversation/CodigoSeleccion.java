package pe.barberturno.conversation;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identificador de un botón o de una fila de lista generado por el servidor (13-v2-10 §3.1). Cada
 * selección lleva todo lo que el paso siguiente necesita, así el estado de la conversación no guarda
 * servicio, profesional ni fecha; pero el identificador vuelve del cliente y **no es de confianza**:
 * quien lo usa revalida el negocio (RLS), la ficha, el estado y las reglas.
 *
 * <p>Formatos (como máximo 200 caracteres; ids de 1 a 18 cifras, sin ceros a la izquierda y mayores que
 * cero salvo {@code bid = 0} = cualquiera; fecha {@code aaaammdd} real; página de 0 a 9; segundos epoch
 * de 1 a 253402300799):
 * {@code m:RESERVAR|MIS_CITAS|INICIO} · {@code p:S|N} · {@code sp:<pág>} · {@code s:<sid>} ·
 * {@code bp:<sid>:<pág>} · {@code b:<sid>:<bid>} · {@code d:<sid>:<bid>:<aaaammdd>:<pág>} ·
 * {@code f:<sid>:<bid>:<seg>} ({@code bid > 0}) · {@code c:<rid>} · {@code a:<rid>:C|R} ·
 * {@code rd:<rid>:<aaaammdd>:<pág>} · {@code rf:<rid>:<seg>} · {@code o:<oid>:<nonce>:S|N}
 * ({@code nonce} base64url canónico sin relleno de 16 bytes / 22 caracteres).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface CodigoSeleccion {

    /** Longitud máxima de un identificador (límite de Meta para filas de lista). */
    int LONGITUD_MAXIMA = 200;

    private static long maxSegundosEpoch() {
        return 253_402_300_799L; // 9999-12-31T23:59:59Z
    }

    /**
     * Representa la selección como identificador de botón o fila.
     * @return identificador con el formato de la tabla de la interfaz
     */
    default String codificar() {
        return switch (this) {
            case Menu m -> "m:" + m.opcion().name();
            case Privacidad p -> "p:" + (p.acepta() ? "S" : "N");
            case PaginaServicios sp -> "sp:" + sp.pagina();
            case Servicio s -> "s:" + s.servicioId();
            case PaginaProfesionales bp -> "bp:" + bp.servicioId() + ":" + bp.pagina();
            case Profesional b -> "b:" + b.servicioId() + ":" + b.barberoId();
            case Dia d -> "d:" + d.servicioId() + ":" + d.barberoId() + ":" + formatearFecha(d.dia()) + ":" + d.pagina();
            case Franja f -> "f:" + f.servicioId() + ":" + f.barberoId() + ":" + f.inicioEpochSeg();
            case Cita c -> "c:" + c.reservaId();
            case AccionCita a -> "a:" + a.reservaId() + ":" + (a.cancelar() ? "C" : "R");
            case DiaCambio rd -> "rd:" + rd.reservaId() + ":" + formatearFecha(rd.dia()) + ":" + rd.pagina();
            case FranjaCambio rf -> "rf:" + rf.reservaId() + ":" + rf.inicioEpochSeg();
            case Confirmacion o -> "o:" + o.operacionId() + ":" + o.nonce() + ":" + (o.confirma() ? "S" : "N");
        };
    }

    /**
     * Decodifica un identificador recibido. Cualquier desviación del formato devuelve vacío, nunca una
     * excepción.
     * @param id identificador del botón o la fila; puede ser nulo
     * @return selección decodificada, o vacío si no cumple exactamente un formato
     */
    static Optional<CodigoSeleccion> decodificar(String id) {
        if (id == null || id.isEmpty() || id.length() > LONGITUD_MAXIMA) {
            return Optional.empty();
        }
        try {
            if (id.startsWith("m:")) {
                Matcher m = Pattern.compile("^m:(RESERVAR|MIS_CITAS|INICIO)$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new Menu(OpcionMenu.valueOf(m.group(1))));
                }
            } else if (id.startsWith("p:")) {
                Matcher m = Pattern.compile("^p:([SN])$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new Privacidad("S".equals(m.group(1))));
                }
            } else if (id.startsWith("sp:")) {
                Matcher m = Pattern.compile("^sp:([0-9])$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new PaginaServicios(Integer.parseInt(m.group(1))));
                }
            } else if (id.startsWith("s:")) {
                Matcher m = Pattern.compile("^s:([1-9][0-9]{0,17})$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new Servicio(Long.parseLong(m.group(1))));
                }
            } else if (id.startsWith("bp:")) {
                Matcher m = Pattern.compile("^bp:([1-9][0-9]{0,17}):([0-9])$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new PaginaProfesionales(Long.parseLong(m.group(1)), Integer.parseInt(m.group(2))));
                }
            } else if (id.startsWith("b:")) {
                Matcher m = Pattern.compile("^b:([1-9][0-9]{0,17}):(0|[1-9][0-9]{0,17})$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new Profesional(Long.parseLong(m.group(1)), Long.parseLong(m.group(2))));
                }
            } else if (id.startsWith("d:")) {
                Matcher m = Pattern.compile("^d:([1-9][0-9]{0,17}):(0|[1-9][0-9]{0,17}):([0-9]{8}):([0-9])$").matcher(id);
                if (m.matches()) {
                    long sid = Long.parseLong(m.group(1));
                    long bid = Long.parseLong(m.group(2));
                    LocalDate dia = parsearFecha(m.group(3));
                    if (dia != null) {
                        int pag = Integer.parseInt(m.group(4));
                        return Optional.of(new Dia(sid, bid, dia, pag));
                    }
                }
            } else if (id.startsWith("f:")) {
                Matcher m = Pattern.compile("^f:([1-9][0-9]{0,17}):([1-9][0-9]{0,17}):([1-9][0-9]{0,17})$").matcher(id);
                if (m.matches()) {
                    long sid = Long.parseLong(m.group(1));
                    long bid = Long.parseLong(m.group(2));
                    long seg = Long.parseLong(m.group(3));
                    if (seg >= 1 && seg <= maxSegundosEpoch()) {
                        return Optional.of(new Franja(sid, bid, seg));
                    }
                }
            } else if (id.startsWith("c:")) {
                Matcher m = Pattern.compile("^c:([1-9][0-9]{0,17})$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new Cita(Long.parseLong(m.group(1))));
                }
            } else if (id.startsWith("a:")) {
                Matcher m = Pattern.compile("^a:([1-9][0-9]{0,17}):([CR])$").matcher(id);
                if (m.matches()) {
                    return Optional.of(new AccionCita(Long.parseLong(m.group(1)), "C".equals(m.group(2))));
                }
            } else if (id.startsWith("rd:")) {
                Matcher m = Pattern.compile("^rd:([1-9][0-9]{0,17}):([0-9]{8}):([0-9])$").matcher(id);
                if (m.matches()) {
                    long rid = Long.parseLong(m.group(1));
                    LocalDate dia = parsearFecha(m.group(2));
                    if (dia != null) {
                        int pag = Integer.parseInt(m.group(3));
                        return Optional.of(new DiaCambio(rid, dia, pag));
                    }
                }
            } else if (id.startsWith("rf:")) {
                Matcher m = Pattern.compile("^rf:([1-9][0-9]{0,17}):([1-9][0-9]{0,17})$").matcher(id);
                if (m.matches()) {
                    long rid = Long.parseLong(m.group(1));
                    long seg = Long.parseLong(m.group(2));
                    if (seg >= 1 && seg <= maxSegundosEpoch()) {
                        return Optional.of(new FranjaCambio(rid, seg));
                    }
                }
            } else if (id.startsWith("o:")) {
                Matcher m = Pattern.compile("^o:([1-9][0-9]{0,17}):([A-Za-z0-9_-]{22}):([SN])$").matcher(id);
                if (m.matches()) {
                    long oid = Long.parseLong(m.group(1));
                    String nonce = m.group(2);
                    if (esNonceCanonica16Bytes(nonce)) {
                        return Optional.of(new Confirmacion(oid, nonce, "S".equals(m.group(3))));
                    }
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static String formatearFecha(LocalDate dia) {
        return DateTimeFormatter.BASIC_ISO_DATE.format(dia);
    }

    private static LocalDate parsearFecha(String texto) {
        try {
            return LocalDate.parse(texto, DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static boolean esNonceCanonica16Bytes(String nonce) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(nonce);
            if (bytes.length != 16) {
                return false;
            }
            String recodificado = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            return recodificado.equals(nonce);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Opción del menú principal.
     * @param opcion opción elegida
     */
    record Menu(OpcionMenu opcion) implements CodigoSeleccion {
        /**
         * Exige la opción.
         * @param opcion opción; no nula
         * @throws NullPointerException si falta
         */
        public Menu {
            Objects.requireNonNull(opcion, "opcion");
        }
    }

    /**
     * Respuesta al aviso de privacidad.
     * @param acepta verdadero para [Aceptar], falso para [No, gracias]
     */
    record Privacidad(boolean acepta) implements CodigoSeleccion { }

    /**
     * Página de la lista de servicios.
     * @param pagina página de 0 a 9
     */
    record PaginaServicios(int pagina) implements CodigoSeleccion { }

    /**
     * Servicio elegido.
     * @param servicioId servicio
     */
    record Servicio(long servicioId) implements CodigoSeleccion { }

    /**
     * Página de la lista de profesionales de un servicio.
     * @param servicioId servicio elegido
     * @param pagina página de 0 a 9
     */
    record PaginaProfesionales(long servicioId, int pagina) implements CodigoSeleccion { }

    /**
     * Profesional elegido para un servicio.
     * @param servicioId servicio elegido
     * @param barberoId profesional, o 0 para «Cualquiera»
     */
    record Profesional(long servicioId, long barberoId) implements CodigoSeleccion { }

    /**
     * Día elegido y página de sus franjas.
     * @param servicioId servicio elegido
     * @param barberoId profesional, o 0 para «Cualquiera»
     * @param dia fecha en Lima
     * @param pagina página de franjas de 0 a 9
     */
    record Dia(long servicioId, long barberoId, LocalDate dia, int pagina) implements CodigoSeleccion {
        /**
         * Exige la fecha.
         * @param servicioId servicio elegido
         * @param barberoId profesional o 0
         * @param dia fecha; no nula
         * @param pagina página
         * @throws NullPointerException si falta la fecha
         */
        public Dia {
            Objects.requireNonNull(dia, "dia");
        }
    }

    /**
     * Franja elegida para reservar.
     * @param servicioId servicio elegido
     * @param barberoId profesional concreto de la franja (mayor que cero)
     * @param inicioEpochSeg inicio en segundos desde la época
     */
    record Franja(long servicioId, long barberoId, long inicioEpochSeg) implements CodigoSeleccion { }

    /**
     * Cita elegida en «Mis citas».
     * @param reservaId reserva
     */
    record Cita(long reservaId) implements CodigoSeleccion { }

    /**
     * Acción elegida sobre una cita.
     * @param reservaId reserva
     * @param cancelar verdadero para cancelar, falso para reprogramar
     */
    record AccionCita(long reservaId, boolean cancelar) implements CodigoSeleccion { }

    /**
     * Día elegido para reprogramar una cita con el mismo profesional y servicio.
     * @param reservaId reserva
     * @param dia fecha en Lima
     * @param pagina página de franjas de 0 a 9
     */
    record DiaCambio(long reservaId, LocalDate dia, int pagina) implements CodigoSeleccion {
        /**
         * Exige la fecha.
         * @param reservaId reserva
         * @param dia fecha; no nula
         * @param pagina página
         * @throws NullPointerException si falta la fecha
         */
        public DiaCambio {
            Objects.requireNonNull(dia, "dia");
        }
    }

    /**
     * Franja nueva elegida para reprogramar.
     * @param reservaId reserva
     * @param inicioEpochSeg nuevo inicio en segundos desde la época
     */
    record FranjaCambio(long reservaId, long inicioEpochSeg) implements CodigoSeleccion { }

    /**
     * Respuesta al resumen de una operación pendiente (V2-RN-10).
     * @param operacionId operación pendiente
     * @param nonce nonce en base64url sin relleno, 22 caracteres
     * @param confirma verdadero para [Confirmar], falso para [Cambiar]
     */
    record Confirmacion(long operacionId, String nonce, boolean confirma) implements CodigoSeleccion {
        /**
         * Exige el nonce.
         * @param operacionId operación
         * @param nonce nonce; no nulo
         * @param confirma decisión
         * @throws NullPointerException si falta el nonce
         */
        public Confirmacion {
            Objects.requireNonNull(nonce, "nonce");
        }

        /**
         * Oculta el nonce: un registro de la selección no debe permitir reutilizarlo.
         * @return representación sin el nonce
         */
        @Override
        public String toString() {
            return "Confirmacion[operacionId=" + operacionId + ", confirma=" + confirma + "]";
        }
    }
}
