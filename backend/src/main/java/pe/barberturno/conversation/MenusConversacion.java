package pe.barberturno.conversation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import pe.barberturno.common.time.TiempoNegocio;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
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

    private static final int LIMITE_CUERPO = 1024;
    private static final int LIMITE_TITULO_BOTON = 20;
    private static final int LIMITE_BOTONES = 3;
    private static final int LIMITE_TEXTO_BOTON_LISTA = 20;
    private static final int LIMITE_TITULO_FILA = 24;
    private static final int LIMITE_DESCRIPCION_FILA = 72;
    private static final int LIMITE_FILAS = 10;
    private static final int TAMANO_PAGINA = 9;

    private final TextosConversacion textos = new TextosConversacion();

    /** Constructor sin dependencias, usado por Spring y por las pruebas unitarias. */
    public MenusConversacion() { }

    /**
     * Menú principal con [Reservar] ({@code m:RESERVAR}) y [Mis citas] ({@code m:MIS_CITAS}).
     * @param cuerpo texto del mensaje (saludo, aviso o resultado previo)
     * @return objeto interactivo de botones
     */
    public ObjectNode menuPrincipal(String cuerpo) {
        return crearBotones(cuerpo, List.of(
                new BotonDef(new CodigoSeleccion.Menu(OpcionMenu.RESERVAR).codificar(), "Reservar"),
                new BotonDef(new CodigoSeleccion.Menu(OpcionMenu.MIS_CITAS).codificar(), "Mis citas")
        ));
    }

    /**
     * Consentimiento con [Aceptar] ({@code p:S}) y [No, gracias] ({@code p:N}).
     * @param cuerpo texto de privacidad ya compuesto
     * @return objeto interactivo de botones
     */
    public ObjectNode consentimiento(String cuerpo) {
        return crearBotones(cuerpo, List.of(
                new BotonDef(new CodigoSeleccion.Privacidad(true).codificar(), "Aceptar"),
                new BotonDef(new CodigoSeleccion.Privacidad(false).codificar(), "No, gracias")
        ));
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
        List<ServicioOpcion> lista = (servicios != null) ? servicios : List.of();
        int pag = pagina;
        int inicio = pag * TAMANO_PAGINA;
        if (pag < 0 || pag > 9 || (pag > 0 && inicio >= lista.size())) {
            pag = 0;
            inicio = 0;
        }
        int fin = Math.min(inicio + TAMANO_PAGINA, lista.size());
        List<FilaDef> filas = new ArrayList<>();
        for (int i = inicio; i < fin; i++) {
            ServicioOpcion s = lista.get(i);
            filas.add(new FilaDef(
                    new CodigoSeleccion.Servicio(s.id()).codificar(),
                    s.nombre(),
                    s.duracionMin() + " min · " + textos.precio(s.precio())
            ));
        }
        if (fin < lista.size() && pag < 9) {
            filas.add(new FilaDef(
                    new CodigoSeleccion.PaginaServicios(pag + 1).codificar(),
                    "Ver más",
                    "Más opciones"
            ));
        }
        return crearLista(cuerpo, filas);
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
        List<ProfesionalOpcion> lista = (profesionales != null) ? profesionales : List.of();
        int pag = pagina;
        List<FilaDef> filas = new ArrayList<>();

        if (pag == 0) {
            filas.add(new FilaDef(
                    new CodigoSeleccion.Profesional(servicioId, 0).codificar(),
                    "Cualquiera",
                    "El primer profesional libre"
            ));
            int fin = Math.min(8, lista.size());
            for (int i = 0; i < fin; i++) {
                ProfesionalOpcion p = lista.get(i);
                filas.add(new FilaDef(
                        new CodigoSeleccion.Profesional(servicioId, p.id()).codificar(),
                        p.nombre(),
                        "Profesional"
                ));
            }
            if (lista.size() > 8) {
                filas.add(new FilaDef(
                        new CodigoSeleccion.PaginaProfesionales(servicioId, 1).codificar(),
                        "Ver más",
                        "Más profesionales"
                ));
            }
        } else {
            int inicio = 8 + (pag - 1) * TAMANO_PAGINA;
            if (pag < 0 || pag > 9 || inicio >= lista.size()) {
                return profesionales(cuerpo, servicioId, profesionales, 0);
            }
            int fin = Math.min(inicio + TAMANO_PAGINA, lista.size());
            for (int i = inicio; i < fin; i++) {
                ProfesionalOpcion p = lista.get(i);
                filas.add(new FilaDef(
                        new CodigoSeleccion.Profesional(servicioId, p.id()).codificar(),
                        p.nombre(),
                        "Profesional"
                ));
            }
            if (fin < lista.size() && pag < 9) {
                filas.add(new FilaDef(
                        new CodigoSeleccion.PaginaProfesionales(servicioId, pag + 1).codificar(),
                        "Ver más",
                        "Más profesionales"
                ));
            }
        }
        return crearLista(cuerpo, filas);
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
        List<FilaDef> filas = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate dia = hoy.plusDays(i);
            String id = new CodigoSeleccion.Dia(servicioId, barberoId, dia, 0).codificar();
            String titulo;
            if (i == 0) {
                titulo = "Hoy";
            } else if (i == 1) {
                titulo = "Mañana";
            } else {
                titulo = diaSemanaAbreviado(dia.getDayOfWeek()) + " "
                        + String.format(Locale.ROOT, "%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue());
            }
            filas.add(new FilaDef(id, titulo, textos.fecha(dia)));
        }
        return crearLista(cuerpo, filas);
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
        List<FranjaOpcion> lista = (franjas != null) ? franjas : List.of();
        int pag = pagina;
        int inicio = pag * TAMANO_PAGINA;
        if (pag < 0 || pag > 9 || (pag > 0 && inicio >= lista.size())) {
            pag = 0;
            inicio = 0;
        }
        int fin = Math.min(inicio + TAMANO_PAGINA, lista.size());
        List<FilaDef> filas = new ArrayList<>();
        for (int i = inicio; i < fin; i++) {
            FranjaOpcion f = lista.get(i);
            filas.add(new FilaDef(
                    new CodigoSeleccion.Franja(servicioId, f.barberoId(), f.inicio().getEpochSecond()).codificar(),
                    textos.hora(f.inicio()) + " · " + f.profesional(),
                    textos.fecha(f.inicio())
            ));
        }
        if (fin < lista.size() && pag < 9) {
            filas.add(new FilaDef(
                    new CodigoSeleccion.Dia(servicioId, barberoId, dia, pag + 1).codificar(),
                    "Ver más",
                    "Más horarios"
            ));
        }
        return crearLista(cuerpo, filas);
    }

    /**
     * Lista de citas ({@code c:<rid>}). Título: código y fecha corta con hora; descripción: servicio y
     * profesional.
     * @param cuerpo texto del mensaje
     * @param citas citas futuras de la ficha (como máximo 10); no vacía
     * @return objeto interactivo de lista
     */
    public ObjectNode citas(String cuerpo, List<CitaOpcion> citas) {
        List<CitaOpcion> lista = (citas != null) ? citas : List.of();
        int limite = Math.min(lista.size(), LIMITE_FILAS);
        List<FilaDef> filas = new ArrayList<>();
        for (int i = 0; i < limite; i++) {
            CitaOpcion c = lista.get(i);
            OffsetDateTime lima = TiempoNegocio.aLima(c.inicio());
            String fechaHora = String.format(Locale.ROOT, "%02d/%02d %02d:%02d",
                    lima.getDayOfMonth(), lima.getMonthValue(), lima.getHour(), lima.getMinute());
            filas.add(new FilaDef(
                    new CodigoSeleccion.Cita(c.reservaId()).codificar(),
                    c.codigo() + " · " + fechaHora,
                    c.servicio() + " con " + c.profesional()
            ));
        }
        return crearLista(cuerpo, filas);
    }

    /**
     * Acciones sobre una cita: [Cancelar] ({@code a:<rid>:C}) si {@code puedeCancelar}, [Reprogramar]
     * ({@code a:<rid>:R}) si {@code puedeReprogramar}, y siempre [Menú] ({@code m:INICIO}).
     * @param cuerpo detalle de la cita
     * @param cita cita de la ficha
     * @return objeto interactivo de botones
     */
    public ObjectNode accionesCita(String cuerpo, CitaOpcion cita) {
        List<BotonDef> botones = new ArrayList<>();
        if (cita != null && cita.puedeCancelar()) {
            botones.add(new BotonDef(new CodigoSeleccion.AccionCita(cita.reservaId(), true).codificar(), "Cancelar"));
        }
        if (cita != null && cita.puedeReprogramar()) {
            botones.add(new BotonDef(new CodigoSeleccion.AccionCita(cita.reservaId(), false).codificar(), "Reprogramar"));
        }
        botones.add(new BotonDef(new CodigoSeleccion.Menu(OpcionMenu.INICIO).codificar(), "Menú"));
        return crearBotones(cuerpo, botones);
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
        List<FilaDef> filas = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate dia = hoy.plusDays(i);
            String id = new CodigoSeleccion.DiaCambio(reservaId, dia, 0).codificar();
            String titulo;
            if (i == 0) {
                titulo = "Hoy";
            } else if (i == 1) {
                titulo = "Mañana";
            } else {
                titulo = diaSemanaAbreviado(dia.getDayOfWeek()) + " "
                        + String.format(Locale.ROOT, "%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue());
            }
            filas.add(new FilaDef(id, titulo, textos.fecha(dia)));
        }
        return crearLista(cuerpo, filas);
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
        List<FranjaOpcion> lista = (franjas != null) ? franjas : List.of();
        int pag = pagina;
        int inicio = pag * TAMANO_PAGINA;
        if (pag < 0 || pag > 9 || (pag > 0 && inicio >= lista.size())) {
            pag = 0;
            inicio = 0;
        }
        int fin = Math.min(inicio + TAMANO_PAGINA, lista.size());
        List<FilaDef> filas = new ArrayList<>();
        for (int i = inicio; i < fin; i++) {
            FranjaOpcion f = lista.get(i);
            filas.add(new FilaDef(
                    new CodigoSeleccion.FranjaCambio(reservaId, f.inicio().getEpochSecond()).codificar(),
                    textos.hora(f.inicio()) + " · " + f.profesional(),
                    textos.fecha(f.inicio())
            ));
        }
        if (fin < lista.size() && pag < 9) {
            filas.add(new FilaDef(
                    new CodigoSeleccion.DiaCambio(reservaId, dia, pag + 1).codificar(),
                    "Ver más",
                    "Más horarios"
            ));
        }
        return crearLista(cuerpo, filas);
    }

    /**
     * Confirmación explícita: [Confirmar] ({@code o:<oid>:<nonce>:S}) y [Cambiar] ({@code o:<oid>:<nonce>:N}).
     * @param cuerpo resumen ya compuesto
     * @param operacionId operación vigente
     * @param nonce nonce de la operación
     * @return objeto interactivo de botones
     */
    public ObjectNode confirmacion(String cuerpo, long operacionId, String nonce) {
        return crearBotones(cuerpo, List.of(
                new BotonDef(new CodigoSeleccion.Confirmacion(operacionId, nonce, true).codificar(), "Confirmar"),
                new BotonDef(new CodigoSeleccion.Confirmacion(operacionId, nonce, false).codificar(), "Cambiar")
        ));
    }

    private ObjectNode crearBotones(String cuerpo, List<BotonDef> botones) {
        ObjectNode raiz = JsonNodeFactory.instance.objectNode();
        raiz.put("type", "button");
        raiz.putObject("body").put("text", recortar(cuerpo, LIMITE_CUERPO));
        ArrayNode botonesArr = raiz.putObject("action").putArray("buttons");
        int limite = Math.min(botones.size(), LIMITE_BOTONES);
        for (int i = 0; i < limite; i++) {
            BotonDef b = botones.get(i);
            ObjectNode btn = botonesArr.addObject();
            btn.put("type", "reply");
            btn.putObject("reply")
                    .put("id", b.id())
                    .put("title", recortar(b.titulo(), LIMITE_TITULO_BOTON));
        }
        return raiz;
    }

    private ObjectNode crearLista(String cuerpo, List<FilaDef> filas) {
        ObjectNode raiz = JsonNodeFactory.instance.objectNode();
        raiz.put("type", "list");
        raiz.putObject("body").put("text", recortar(cuerpo, LIMITE_CUERPO));
        ObjectNode action = raiz.putObject("action");
        action.put("button", recortar("Ver opciones", LIMITE_TEXTO_BOTON_LISTA));
        ArrayNode sections = action.putArray("sections");
        ObjectNode section = sections.addObject();
        section.put("title", recortar("Opciones", LIMITE_TITULO_FILA));
        ArrayNode rows = section.putArray("rows");
        int limite = Math.min(filas.size(), LIMITE_FILAS);
        for (int i = 0; i < limite; i++) {
            FilaDef f = filas.get(i);
            ObjectNode row = rows.addObject();
            row.put("id", f.id());
            row.put("title", recortar(f.titulo(), LIMITE_TITULO_FILA));
            row.put("description", recortar(f.descripcion(), LIMITE_DESCRIPCION_FILA));
        }
        return raiz;
    }

    static String recortar(String texto, int limiteMaximo) {
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

    private String diaSemanaAbreviado(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "lun";
            case TUESDAY -> "mar";
            case WEDNESDAY -> "mié";
            case THURSDAY -> "jue";
            case FRIDAY -> "vie";
            case SATURDAY -> "sáb";
            case SUNDAY -> "dom";
        };
    }

    private record BotonDef(String id, String titulo) { }

    private record FilaDef(String id, String titulo, String descripcion) { }
}
