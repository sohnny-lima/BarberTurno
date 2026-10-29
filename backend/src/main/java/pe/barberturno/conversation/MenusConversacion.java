package pe.barberturno.conversation;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * Construye el objeto {@code interactive} de Meta ({@code type} {@code button} o {@code list}) que acepta
 * {@code SalidaWhatsApp.encolarInteractivo} (13-v2-10 §3.2). Es determinista: los mismos datos dan el
 * mismo JSON. Aplica los límites de Meta recortando con «…»: cuerpo ≤ 1024, título de botón ≤ 20, como
 * máximo 3 botones, texto del botón de la lista ≤ 20, título de fila ≤ 24, descripción ≤ 72 y como
 * máximo 10 filas. Las listas paginadas muestran 9 opciones y una fila «Ver más» si quedan; una página
 * fuera de rango muestra la primera. Los identificadores salen de {@link CodigoSeleccion#codificar()}.
 * Sin «Hablar con alguien» (V2-11). Implementación: Gemini (bloque G).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class MenusConversacion {

    /** Constructor sin dependencias, usado por Spring y por las pruebas unitarias. */
    public MenusConversacion() { }

    /**
     * Menú principal con [Reservar] ({@code m:RESERVAR}) y [Mis citas] ({@code m:MIS_CITAS}).
     * @param cuerpo texto del mensaje (saludo, aviso o resultado previo)
     * @return objeto interactivo de botones
     */
    public ObjectNode menuPrincipal(String cuerpo) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Consentimiento con [Aceptar] ({@code p:S}) y [No, gracias] ({@code p:N}).
     * @param cuerpo texto de privacidad ya compuesto
     * @return objeto interactivo de botones
     */
    public ObjectNode consentimiento(String cuerpo) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Lista paginada de servicios ({@code s:<sid>}; «Ver más» = {@code sp:<pág+1>}). Título: nombre;
     * descripción: duración y precio.
     * @param cuerpo texto del mensaje
     * @param servicios todos los servicios activos, ya ordenados; no vacía
     * @param pagina página pedida
     * @return objeto interactivo de lista
     */
    public ObjectNode servicios(String cuerpo, List<ServicioOpcion> servicios, int pagina) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Lista paginada de profesionales ({@code b:<sid>:<bid>}). La página 0 empieza por «Cualquiera»
     * ({@code bid = 0}); «Ver más» = {@code bp:<sid>:<pág+1>}.
     * @param cuerpo texto del mensaje
     * @param servicioId servicio elegido
     * @param profesionales todos los profesionales activos, ya ordenados; no vacía
     * @param pagina página pedida
     * @return objeto interactivo de lista
     */
    public ObjectNode profesionales(String cuerpo, long servicioId, List<ProfesionalOpcion> profesionales,
            int pagina) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Lista de 7 días desde hoy ({@code d:<sid>:<bid>:<aaaammdd>:0}): «Hoy», «Mañana» y después el día de
     * la semana abreviado con la fecha («vie 31/10»).
     * @param cuerpo texto del mensaje
     * @param servicioId servicio elegido
     * @param barberoId profesional o 0
     * @param hoy fecha actual en Lima (del {@code Clock})
     * @return objeto interactivo de lista
     */
    public ObjectNode dias(String cuerpo, long servicioId, long barberoId, LocalDate hoy) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Lista paginada de franjas ({@code f:<sid>:<bid>:<seg>}; «Ver más» = {@code d:<sid>:<bid>:<día>:<pág+1>}).
     * Título: hora y profesional; descripción: fecha completa.
     * @param cuerpo texto del mensaje
     * @param servicioId servicio elegido
     * @param barberoId profesional elegido o 0 (para el «Ver más»)
     * @param dia día de las franjas
     * @param franjas todas las franjas del día, ya ordenadas; no vacía
     * @param pagina página pedida
     * @return objeto interactivo de lista
     */
    public ObjectNode franjas(String cuerpo, long servicioId, long barberoId, LocalDate dia,
            List<FranjaOpcion> franjas, int pagina) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Lista de citas ({@code c:<rid>}). Título: código y fecha corta con hora; descripción: servicio y
     * profesional.
     * @param cuerpo texto del mensaje
     * @param citas citas futuras de la ficha (como máximo 10); no vacía
     * @return objeto interactivo de lista
     */
    public ObjectNode citas(String cuerpo, List<CitaOpcion> citas) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Acciones sobre una cita: [Cancelar] ({@code a:<rid>:C}) si {@code puedeCancelar}, [Reprogramar]
     * ({@code a:<rid>:R}) si {@code puedeReprogramar}, y siempre [Menú] ({@code m:INICIO}).
     * @param cuerpo detalle de la cita
     * @param cita cita de la ficha
     * @return objeto interactivo de botones
     */
    public ObjectNode accionesCita(String cuerpo, CitaOpcion cita) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Lista de 7 días para reprogramar ({@code rd:<rid>:<aaaammdd>:0}), con los mismos títulos que
     * {@link #dias}.
     * @param cuerpo texto del mensaje
     * @param reservaId cita que se reprograma
     * @param hoy fecha actual en Lima
     * @return objeto interactivo de lista
     */
    public ObjectNode diasCambio(String cuerpo, long reservaId, LocalDate hoy) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Lista paginada de franjas para reprogramar ({@code rf:<rid>:<seg>}; «Ver más» =
     * {@code rd:<rid>:<día>:<pág+1>}).
     * @param cuerpo texto del mensaje
     * @param reservaId cita que se reprograma
     * @param dia día de las franjas
     * @param franjas franjas del mismo profesional, ya ordenadas; no vacía
     * @param pagina página pedida
     * @return objeto interactivo de lista
     */
    public ObjectNode franjasCambio(String cuerpo, long reservaId, LocalDate dia, List<FranjaOpcion> franjas,
            int pagina) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }

    /**
     * Confirmación explícita: [Confirmar] ({@code o:<oid>:<nonce>:S}) y [Cambiar] ({@code o:<oid>:<nonce>:N}).
     * @param cuerpo resumen ya compuesto
     * @param operacionId operación vigente
     * @param nonce nonce de la operación
     * @return objeto interactivo de botones
     */
    public ObjectNode confirmacion(String cuerpo, long operacionId, String nonce) {
        throw new UnsupportedOperationException("Bloque G (Gemini)");
    }
}
