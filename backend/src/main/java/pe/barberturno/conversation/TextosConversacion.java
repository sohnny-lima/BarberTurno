package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.EstadoReserva;

/**
 * Textos del servidor para la conversación guiada (V2-RN-11; 13-v2-10 §3.2). Fechas y horas siempre en
 * Lima con {@code TiempoNegocio}; precio en soles con coma decimal. Los textos de resultado se componen
 * solo a partir de {@link ResultadoOperacion}. Sin textos de privacidad propios: llegan de la
 * configuración. Implementación: Gemini (bloque G).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class TextosConversacion {

    private static final Locale LOCALE_PE = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FORMATO_FECHA_COMPLETA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", LOCALE_PE);
    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm", LOCALE_PE);

    /** Constructor sin dependencias, usado por Spring y por las pruebas unitarias. */
    public TextosConversacion() { }

    /**
     * Fecha completa en Lima, por ejemplo «jueves 23 de octubre de 2026».
     * @param instante instante que se muestra
     * @return fecha en español
     */
    public String fecha(Instant instante) {
        return fecha(TiempoNegocio.fechaLima(instante));
    }

    /**
     * Fecha completa de un día, por ejemplo «jueves 23 de octubre de 2026».
     * @param dia fecha en Lima
     * @return fecha en español
     */
    public String fecha(LocalDate dia) {
        return dia.format(FORMATO_FECHA_COMPLETA).toLowerCase(LOCALE_PE);
    }

    /**
     * Hora en Lima con 24 horas, por ejemplo «18:00».
     * @param instante instante que se muestra
     * @return hora
     */
    public String hora(Instant instante) {
        return TiempoNegocio.aLima(instante).format(FORMATO_HORA);
    }

    /**
     * Precio en soles, por ejemplo «S/ 25,00».
     * @param precio importe
     * @return precio con dos decimales y coma
     */
    public String precio(BigDecimal precio) {
        BigDecimal val = (precio != null ? precio : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(LOCALE_PE);
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator('.');
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        return "S/ " + formato.format(val);
    }

    /**
     * Saludo con el menú principal.
     * @param negocio nombre del negocio
     * @return texto del cuerpo del menú
     */
    public String bienvenida(String negocio) {
        return "¡Hola! Te damos la bienvenida a " + negocio + ". ¿Qué quieres hacer?";
    }

    /**
     * Texto del aviso de privacidad con sus marcadores {@code {negocio}} y {@code {enlace}} sustituidos.
     * @param plantilla texto configurado (borrador hasta P2-06)
     * @param negocio nombre del negocio
     * @param enlace enlace configurado al aviso de privacidad
     * @return texto del consentimiento
     */
    public String privacidad(String plantilla, String negocio, String enlace) {
        String res = plantilla != null ? plantilla : "";
        if (negocio != null) {
            res = res.replace("{negocio}", negocio);
        }
        if (enlace != null) {
            res = res.replace("{enlace}", enlace);
        }
        return res;
    }

    /**
     * Respuesta a [No, gracias]: sin ficha no se reserva, pero se puede consultar.
     * @return texto
     */
    public String privacidadRechazada() {
        return "Entendido. Sin aceptar el aviso de privacidad no podemos agendar citas, pero puedes consultar nuestros servicios y horarios cuando quieras.";
    }

    /**
     * Cuerpo de la lista de servicios.
     * @return texto
     */
    public String elegirServicio() {
        return "Elige el servicio que deseas reservar:";
    }

    /**
     * Cuerpo de la lista de profesionales.
     * @param servicio nombre del servicio elegido
     * @return texto
     */
    public String elegirProfesional(String servicio) {
        return "Para " + servicio + ", ¿con qué profesional prefieres atenderte?";
    }

    /**
     * Cuerpo de la lista de días.
     * @return texto
     */
    public String elegirDia() {
        return "¿Qué día prefieres tu cita?";
    }

    /**
     * Cuerpo de la lista de franjas de un día.
     * @param dia fecha en Lima
     * @return texto
     */
    public String elegirFranja(LocalDate dia) {
        return "Horarios disponibles para el " + fecha(dia) + ":";
    }

    /**
     * Ningún servicio, profesional o franja disponible para lo pedido.
     * @return texto
     */
    public String sinOpciones() {
        return "No hay opciones disponibles en este momento. Por favor, intenta con otra selección o fecha.";
    }

    /**
     * Cuerpo de la lista de citas.
     * @return texto
     */
    public String misCitas() {
        return "Estas son tus próximas citas:";
    }

    /**
     * La ficha no tiene citas futuras (o la conversación aún no tiene ficha).
     * @return texto
     */
    public String sinCitas() {
        return "No tienes citas próximas agendadas.";
    }

    /**
     * Detalle de una cita con las acciones que permite.
     * @param cita cita de la ficha
     * @return texto con código, servicio, profesional, fecha completa y hora
     */
    public String detalleCita(CitaOpcion cita) {
        return "Tu cita " + cita.codigo() + ":\n"
                + cita.servicio() + " con " + cita.profesional() + "\n"
                + fecha(cita.inicio()) + " a las " + hora(cita.inicio()) + ".";
    }

    /**
     * Resumen que se confirma: fecha completa, hora de inicio y fin, profesional, servicio, duración y
     * precio; al reprogramar, antes y después (V2-RF-11).
     * @param resumen datos de la propuesta
     * @return texto del resumen
     */
    public String resumen(ResumenOperacion resumen) {
        if (resumen.tipo() == TipoOperacion.REPROGRAMAR) {
            StringBuilder sb = new StringBuilder();
            if (resumen.antes().isPresent()) {
                CitaOpcion antes = resumen.antes().get();
                sb.append("Reprogramación de tu cita ").append(antes.codigo()).append(":\n")
                        .append("Antes: ").append(fecha(antes.inicio())).append(" a las ").append(hora(antes.inicio()))
                        .append(" con ").append(antes.profesional()).append("\n")
                        .append("Nueva cita: ");
            } else {
                sb.append("Reprogramación de tu cita:\n");
            }
            sb.append(resumen.servicio()).append(" con ").append(resumen.profesional()).append("\n")
                    .append(fecha(resumen.inicio())).append(", ").append(hora(resumen.inicio())).append(" a ").append(hora(resumen.fin()))
                    .append(" (").append(resumen.duracionMin()).append(" min)\n")
                    .append("Precio: ").append(precio(resumen.precio()));
            return sb.toString();
        }

        if (resumen.tipo() == TipoOperacion.CANCELAR) {
            String cod = resumen.antes().map(CitaOpcion::codigo).orElse("");
            String pre = cod.isBlank() ? "Cancelación de cita:\n" : "Cancelación de tu cita " + cod + ":\n";
            return pre + resumen.servicio() + " con " + resumen.profesional() + "\n"
                    + fecha(resumen.inicio()) + " a las " + hora(resumen.inicio()) + " a " + hora(resumen.fin())
                    + " (" + resumen.duracionMin() + " min)\n"
                    + "Precio: " + precio(resumen.precio());
        }

        return resumen.servicio() + " con " + resumen.profesional() + "\n"
                + fecha(resumen.inicio()) + ", " + hora(resumen.inicio()) + " a " + hora(resumen.fin())
                + " (" + resumen.duracionMin() + " min)\n"
                + "Precio: " + precio(resumen.precio());
    }

    /**
     * Texto de resultado compuesto solo con el resultado confirmado en la base (V2-RN-11). Una reserva
     * {@code PENDIENTE} se presenta como pendiente de confirmación por la barbería.
     * @param resultado resultado de confirmar o rechazar
     * @return texto
     */
    public String resultado(ResultadoOperacion resultado) {
        return switch (resultado) {
            case ResultadoOperacion.Ejecutada ejec -> {
                CitaOpcion cita = ejec.cita();
                yield switch (ejec.tipo()) {
                    case CREAR -> {
                        String txt = "Listo: " + cita.codigo() + " el " + fecha(cita.inicio()) + " a las "
                                + hora(cita.inicio()) + " con " + cita.profesional() + ".";
                        if (cita.estado() == EstadoReserva.PENDIENTE) {
                            txt += " Tu cita queda pendiente de confirmación por la barbería.";
                        }
                        yield txt;
                    }
                    case REPROGRAMAR -> {
                        String txt = "Cambiamos tu cita " + cita.codigo() + " para el " + fecha(cita.inicio())
                                + " a las " + hora(cita.inicio()) + " con " + cita.profesional() + ".";
                        if (cita.estado() == EstadoReserva.PENDIENTE) {
                            txt += " Tu cita queda pendiente de confirmación por la barbería.";
                        }
                        yield txt;
                    }
                    case CANCELAR -> "Cancelamos tu cita " + cita.codigo() + " del " + fecha(cita.inicio())
                            + " a las " + hora(cita.inicio()) + " con " + cita.profesional() + ".";
                };
            }
            case ResultadoOperacion.Rechazada rech -> rechazo(rech.codigo());
            case ResultadoOperacion.NoVigente noVig -> {
                if (noVig.motivo() == MotivoNoVigente.CADUCADA) {
                    yield "Esa propuesta ya no es válida: pasaron más de 10 minutos.";
                }
                yield "Esa propuesta ya no es válida.";
            }
            case ResultadoOperacion.Anulada a -> "De acuerdo, no hice ningún cambio.";
        };
    }

    /**
     * Traducción de una regla incumplida al proponer o ejecutar; códigos no previstos → texto genérico.
     * @param codigo código de error del dominio
     * @return texto para el cliente, sin datos internos
     */
    public String rechazo(ErrorCodigo codigo) {
        if (codigo == null) {
            return "No pudimos completar tu solicitud en este momento. Por favor, intenta de nuevo más tarde.";
        }
        return switch (codigo) {
            case FRANJA_NO_DISPONIBLE -> "Esa hora ya no está disponible. Por favor, elige otro horario.";
            case CLIENTE_CON_RESERVA_SOLAPADA -> "Ya tienes otra cita programada en ese horario.";
            case LIMITE_RESERVAS_ACTIVAS -> "Has alcanzado el límite máximo de reservas activas permitidas.";
            case FUERA_DE_POLITICA -> "Ya no es posible realizar cambios o cancelaciones con tan poca anticipación.";
            case FUERA_DE_HORARIO -> "El horario seleccionado está fuera del horario de atención.";
            case FUERA_DE_HORIZONTE -> "La fecha seleccionada está fuera del periodo de reservas permitido.";
            case INICIO_EN_PASADO -> "No se pueden agendar citas en un horario que ya pasó.";
            case RECURSO_INACTIVO -> "El servicio o profesional seleccionado no se encuentra disponible actualmente.";
            case VERSION_DESACTUALIZADA -> "La información de la cita cambió mientras se procesaba. Por favor, inténtalo nuevamente.";
            case TRANSICION_INVALIDA -> "No se puede realizar esta acción en el estado actual de la cita.";
            case NO_ENCONTRADO -> noDisponible();
            default -> "No pudimos completar tu solicitud en este momento. Por favor, intenta de nuevo más tarde.";
        };
    }

    /**
     * La cita o la opción elegida ya no está disponible (incluye las ajenas, sin revelarlas).
     * @return texto
     */
    public String noDisponible() {
        return "Esa opción o cita ya no está disponible.";
    }

    /**
     * Texto no reconocido o selección inválida.
     * @return texto que invita a usar el menú
     */
    public String noReconocido() {
        return "No entendí esa opción. Por favor, usa los botones del menú para continuar.";
    }

    /**
     * Tipo de mensaje no soportado (06-flujos.md §10: «Por ahora solo entiendo texto y botones»).
     * @return texto
     */
    public String noSoportado() {
        return "Por ahora solo entiendo texto y botones.";
    }
}
