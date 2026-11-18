package pe.barberturno.conversation.ia;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.barberturno.conversation.MenusConversacion;
import pe.barberturno.conversation.ServicioOpcion;
import pe.barberturno.conversation.TextosConversacion;

/**
 * Composición determinista de lo que recibe el cliente al cerrar un turno de IA (17-v2-12 §13.3; V2-DA-86): menús y
 * textos fijos de V2-10 con datos del servidor, con los mismos códigos de selección que el flujo guiado. Ningún
 * parámetro procede del texto del modelo.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class RespuestasAsistente {

    private final MenusConversacion menus;
    private final TextosConversacion textos;
    private final ComposicionTurnoIA composicion;

    /**
     * Constructor sin argumentos para pruebas unitarias, que inicializa los componentes sin estado de composición.
     */
    public RespuestasAsistente() {
        this(new MenusConversacion(), new TextosConversacion(), new ComposicionTurnoIA());
    }

    /**
     * Constructor principal usado por Spring para inyectar los componentes de menús, textos y composición.
     *
     * @param menus constructor de mensajes interactivos de V2-10
     * @param textos textos fijos del servidor de V2-10
     * @param composicion composición de listas de franjas y textos fijos del turno de IA
     * @throws NullPointerException si alguna dependencia es nula
     */
    @Autowired
    public RespuestasAsistente(
            MenusConversacion menus,
            TextosConversacion textos,
            ComposicionTurnoIA composicion) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.textos = Objects.requireNonNull(textos, "textos");
        this.composicion = Objects.requireNonNull(composicion, "composicion");
    }

    /**
     * Compone el mensaje interactivo para una decisión validada sin efecto de escritura ni derivación.
     * Si una lista requerida (servicios, profesionales o citas) está vacía o falta un dato previo en
     * {@code datos}, degrada de forma determinista sin emitir listas con {@code rows: []}.
     *
     * @param decision decisión validada y degradada por el servidor
     * @param datos datos cargados por el servidor bajo RLS y con la ficha revalidada
     * @return mensaje interactivo para el cliente
     * @throws NullPointerException si {@code decision} o {@code datos} es nulo
     */
    public MensajeAsistente responder(DecisionRespuesta decision, DatosRespuesta datos) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(datos, "datos");
        return switch (decision.intencion()) {
            case MOSTRAR_SERVICIOS -> responderServicios(datos);
            case MOSTRAR_PROFESIONALES -> responderProfesionales(datos);
            case MOSTRAR_CITAS -> responderCitas(datos);
            case SIN_DISPONIBILIDAD -> new MensajeAsistente.Interactivo(
                    menus.menuPrincipal(textos.sinOpciones()));
            case FUERA_DE_ALCANCE -> new MensajeAsistente.Interactivo(
                    menus.menuPrincipal(composicion.fueraDeAlcance()));
            case SALUDO -> new MensajeAsistente.Interactivo(
                    menus.menuPrincipal(textos.bienvenida(datos.negocio())));
            case PEDIR_DATO -> switch (decision.falta().orElseThrow()) {
                case SERVICIO -> responderServicios(datos);
                case PROFESIONAL -> responderProfesionales(datos);
                case DIA -> responderDia(datos);
                case CITA -> responderCitas(datos);
            };
        };
    }

    /**
     * Compone la lista interactiva de franjas del turno con el texto fijo del servidor.
     * Si la lista de franjas es nula o vacía, devuelve el menú principal con el motivo {@link MotivoMenu#NEUTRO}.
     *
     * @param franjas franjas del turno, en orden (como máximo se muestran 10)
     * @param hoy fecha actual en Lima
     * @return mensaje interactivo
     * @throws NullPointerException si {@code hoy} es nulo
     */
    public MensajeAsistente franjas(List<FranjaOfrecida> franjas, LocalDate hoy) {
        Objects.requireNonNull(hoy, "hoy");
        if (franjas == null || franjas.isEmpty()) {
            return menu(MotivoMenu.NEUTRO);
        }
        return new MensajeAsistente.Interactivo(
                composicion.franjas(composicion.elegirFranja(), franjas, hoy));
    }

    /**
     * Compone el menú principal con el texto fijo correspondiente al motivo de cierre.
     *
     * @param motivo motivo del cierre con menú principal
     * @return mensaje interactivo de botones del menú principal
     * @throws NullPointerException si {@code motivo} es nulo
     */
    public MensajeAsistente menu(MotivoMenu motivo) {
        Objects.requireNonNull(motivo, "motivo");
        String cuerpo = switch (motivo) {
            case NEUTRO -> composicion.neutro();
            case FALLO -> composicion.fallo();
            case VARIAS_PROPUESTAS -> composicion.aclararPropuestas();
            case NO_DISPONIBLE -> textos.noDisponible();
        };
        return new MensajeAsistente.Interactivo(menus.menuPrincipal(cuerpo));
    }

    private MensajeAsistente responderServicios(DatosRespuesta datos) {
        if (datos.servicios().isEmpty()) {
            return new MensajeAsistente.Interactivo(menus.menuPrincipal(textos.sinOpciones()));
        }
        return new MensajeAsistente.Interactivo(
                menus.servicios(textos.elegirServicio(), datos.servicios(), 0));
    }

    private MensajeAsistente responderProfesionales(DatosRespuesta datos) {
        if (datos.servicio().isEmpty()) {
            return responderServicios(datos);
        }
        if (datos.profesionales().isEmpty()) {
            return new MensajeAsistente.Interactivo(menus.menuPrincipal(textos.sinOpciones()));
        }
        ServicioOpcion servicio = datos.servicio().get();
        return new MensajeAsistente.Interactivo(
                menus.profesionales(
                        textos.elegirProfesional(servicio.nombre()),
                        servicio.id(),
                        datos.profesionales(),
                        0));
    }

    private MensajeAsistente responderDia(DatosRespuesta datos) {
        if (datos.servicio().isEmpty()) {
            return responderServicios(datos);
        }
        ServicioOpcion servicio = datos.servicio().get();
        return switch (datos.profesional()) {
            case SeleccionProfesional.SinElegir ignorado -> responderProfesionales(datos);
            case SeleccionProfesional.Cualquiera ignorado -> new MensajeAsistente.Interactivo(
                    menus.dias(textos.elegirDia(), servicio.id(), 0L, datos.hoy()));
            case SeleccionProfesional.Concreto(var profesional) -> new MensajeAsistente.Interactivo(
                    menus.dias(textos.elegirDia(), servicio.id(), profesional.id(), datos.hoy()));
        };
    }

    private MensajeAsistente responderCitas(DatosRespuesta datos) {
        if (datos.citas().isEmpty()) {
            return new MensajeAsistente.Interactivo(menus.menuPrincipal(textos.sinCitas()));
        }
        return new MensajeAsistente.Interactivo(
                menus.citas(textos.misCitas(), datos.citas()));
    }
}
