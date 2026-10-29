package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import pe.barberturno.common.error.ErrorCodigo;

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

    /** Constructor sin dependencias, usado por Spring y por las pruebas unitarias. */
    public TextosConversacion() { }

    /**
     * Fecha completa en Lima, por ejemplo «jueves 23 de octubre de 2026».
     * @param instante instante que se muestra
     * @return fecha en español
     */
    public String fecha(Instant instante) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Fecha completa de un día, por ejemplo «jueves 23 de octubre de 2026».
     * @param dia fecha en Lima
     * @return fecha en español
     */
    public String fecha(LocalDate dia) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Hora en Lima con 24 horas, por ejemplo «18:00».
     * @param instante instante que se muestra
     * @return hora
     */
    public String hora(Instant instante) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Precio en soles, por ejemplo «S/ 25,00».
     * @param precio importe
     * @return precio con dos decimales y coma
     */
    public String precio(BigDecimal precio) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Saludo con el menú principal.
     * @param negocio nombre del negocio
     * @return texto del cuerpo del menú
     */
    public String bienvenida(String negocio) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Texto del aviso de privacidad con sus marcadores {@code {negocio}} y {@code {enlace}} sustituidos.
     * @param plantilla texto configurado (borrador hasta P2-06)
     * @param negocio nombre del negocio
     * @param enlace enlace configurado al aviso de privacidad
     * @return texto del consentimiento
     */
    public String privacidad(String plantilla, String negocio, String enlace) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Respuesta a [No, gracias]: sin ficha no se reserva, pero se puede consultar.
     * @return texto
     */
    public String privacidadRechazada() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Cuerpo de la lista de servicios.
     * @return texto
     */
    public String elegirServicio() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Cuerpo de la lista de profesionales.
     * @param servicio nombre del servicio elegido
     * @return texto
     */
    public String elegirProfesional(String servicio) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Cuerpo de la lista de días.
     * @return texto
     */
    public String elegirDia() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Cuerpo de la lista de franjas de un día.
     * @param dia fecha en Lima
     * @return texto
     */
    public String elegirFranja(LocalDate dia) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Ningún servicio, profesional o franja disponible para lo pedido.
     * @return texto
     */
    public String sinOpciones() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Cuerpo de la lista de citas.
     * @return texto
     */
    public String misCitas() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * La ficha no tiene citas futuras (o la conversación aún no tiene ficha).
     * @return texto
     */
    public String sinCitas() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Detalle de una cita con las acciones que permite.
     * @param cita cita de la ficha
     * @return texto con código, servicio, profesional, fecha completa y hora
     */
    public String detalleCita(CitaOpcion cita) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Resumen que se confirma: fecha completa, hora de inicio y fin, profesional, servicio, duración y
     * precio; al reprogramar, antes y después (V2-RF-11).
     * @param resumen datos de la propuesta
     * @return texto del resumen
     */
    public String resumen(ResumenOperacion resumen) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Texto de resultado compuesto solo con el resultado confirmado en la base (V2-RN-11). Una reserva
     * {@code PENDIENTE} se presenta como pendiente de confirmación por la barbería.
     * @param resultado resultado de confirmar o rechazar
     * @return texto
     */
    public String resultado(ResultadoOperacion resultado) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Traducción de una regla incumplida al proponer o ejecutar; códigos no previstos → texto genérico.
     * @param codigo código de error del dominio
     * @return texto para el cliente, sin datos internos
     */
    public String rechazo(ErrorCodigo codigo) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * La cita o la opción elegida ya no está disponible (incluye las ajenas, sin revelarlas).
     * @return texto
     */
    public String noDisponible() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Texto no reconocido o selección inválida.
     * @return texto que invita a usar el menú
     */
    public String noReconocido() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Tipo de mensaje no soportado (06-flujos.md §10: «Por ahora solo entiendo texto y botones»).
     * @return texto
     */
    public String noSoportado() {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }
}
