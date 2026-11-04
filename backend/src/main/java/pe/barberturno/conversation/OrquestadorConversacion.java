package pe.barberturno.conversation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.CodigoSeleccion.AccionCita;
import pe.barberturno.conversation.CodigoSeleccion.Cita;
import pe.barberturno.conversation.CodigoSeleccion.Confirmacion;
import pe.barberturno.conversation.CodigoSeleccion.Dia;
import pe.barberturno.conversation.CodigoSeleccion.DiaCambio;
import pe.barberturno.conversation.CodigoSeleccion.Franja;
import pe.barberturno.conversation.CodigoSeleccion.FranjaCambio;
import pe.barberturno.conversation.CodigoSeleccion.Menu;
import pe.barberturno.conversation.CodigoSeleccion.PaginaProfesionales;
import pe.barberturno.conversation.CodigoSeleccion.PaginaServicios;
import pe.barberturno.conversation.CodigoSeleccion.Privacidad;
import pe.barberturno.conversation.CodigoSeleccion.Profesional;
import pe.barberturno.conversation.CodigoSeleccion.Servicio;
import pe.barberturno.conversation.BandejaPropiedades.Entrantes;
import pe.barberturno.conversation.ConversacionesWhatsApp.EstadoConversacion;
import pe.barberturno.whatsapp.MensajeEntrante;
import pe.barberturno.whatsapp.OrigenMensaje;
import pe.barberturno.whatsapp.ReceptorMensajesWhatsApp;
import pe.barberturno.whatsapp.RegistroEntradaWhatsApp;
import pe.barberturno.whatsapp.SalidaWhatsApp;
import tools.jackson.databind.node.ObjectNode;

/**
 * Conversación guiada sin IA (V2-10; 13-v2-10 §2): recibe cada mensaje en la transacción de entrega de
 * V2-08, con el contexto del negocio del número receptor, y responde siempre por la outbox de V2-09 en esa
 * misma transacción (V2-DA-65). Orden: comprobación de que el negocio puede atender → registro de la
 * entrada (que bloquea la conversación) → vaciado por inactividad → interpretación determinista →
 * paso del menú, consentimiento u operación → respuesta. Ninguna decisión de negocio se toma aquí: las
 * consultas, propuestas y ejecuciones revalidan en {@link ConsultasConversacion} y
 * {@link OperacionesConversacion}. No registra el texto del cliente en los registros.
 * <p>Desde V2-11 (16-v2-11 §2): en modo {@code HUMANO} solo guarda el mensaje y no responde (V2-RN-15), antes
 * que cualquier confirmación; «Hablar con alguien», la palabra clave {@code PERSONA} o dos textos seguidos no
 * reconocidos derivan a una persona (sin ficha, tras el consentimiento); los entrantes se guardan según
 * {@link BandejaPropiedades#entrantes()}, una sola vez por mensaje.</p>
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrquestadorConversacion implements ReceptorMensajesWhatsApp {
    /** Días que ofrece la lista de días (hoy y los 6 siguientes). */
    static final int DIAS_OFRECIDOS = 7;
    /** 9999-12-31T23:59:59Z: mayor inicio aceptado en una selección. */
    static final long INSTANTE_MAXIMO = 253_402_300_799L;
    /** Clave del estado con los textos no reconocidos seguidos (V2-11). */
    static final String FALLOS = "fallos";
    /** Clave del estado con el motivo de una derivación pendiente de consentimiento (V2-11). */
    static final String MOTIVO = "motivo";
    /** Textos no reconocidos seguidos que derivan a una persona. */
    static final int FALLOS_PARA_DERIVAR = 2;

    private final RegistroEntradaWhatsApp registro;
    private final ConversacionesWhatsApp conversaciones;
    private final ConsentimientoConversacion consentimiento;
    private final InterpreteEntradas interprete;
    private final MenusConversacion menus;
    private final TextosConversacion textos;
    private final ConsultasConversacion consultas;
    private final OperacionesConversacion operaciones;
    private final SalidaWhatsApp salida;
    private final ConversacionPropiedades propiedades;
    private final DerivacionConversacion derivacion;
    private final BandejaPropiedades bandeja;
    private final Clock reloj;

    /**
     * Recibe sus dependencias de Spring.
     * @param registro ventana de atención y entrada anterior
     * @param conversaciones estado y ficha de la conversación
     * @param consentimiento aceptación de la privacidad y ficha
     * @param interprete interpretación determinista de las entradas
     * @param menus mensajes interactivos
     * @param textos textos del servidor
     * @param consultas lecturas del negocio
     * @param operaciones operaciones pendientes y su ejecución
     * @param salida outbox de WhatsApp
     * @param propiedades textos de privacidad e inactividad
     * @param derivacion paso a modo humano (V2-11)
     * @param bandeja qué entrantes se guardan (V2-11)
     * @param reloj reloj de la aplicación
     */
    public OrquestadorConversacion(RegistroEntradaWhatsApp registro, ConversacionesWhatsApp conversaciones,
            ConsentimientoConversacion consentimiento, InterpreteEntradas interprete, MenusConversacion menus,
            TextosConversacion textos, ConsultasConversacion consultas, OperacionesConversacion operaciones,
            SalidaWhatsApp salida, ConversacionPropiedades propiedades, DerivacionConversacion derivacion,
            BandejaPropiedades bandeja, Clock reloj) {
        this.registro = registro;
        this.conversaciones = conversaciones;
        this.consentimiento = consentimiento;
        this.interprete = interprete;
        this.menus = menus;
        this.textos = textos;
        this.consultas = consultas;
        this.operaciones = operaciones;
        this.salida = salida;
        this.propiedades = propiedades;
        this.derivacion = derivacion;
        this.bandeja = bandeja;
        this.reloj = reloj;
    }

    /**
     * Atiende un mensaje entrante. Si el negocio ya no puede atender (canal desconectado, negocio
     * suspendido o sin propietario), termina sin ninguna escritura. Una excepción técnica revierte toda la
     * transacción de la entrega (conversación, ficha, operación, reserva, auditoría, avisos y outbox) y
     * V2-08 reintenta el evento.
     * @param mensaje mensaje entrante con el negocio ya resuelto por el número receptor
     * @throws IllegalStateException si el negocio no coincide con el contexto de la transacción
     */
    @Override
    public void recibir(MensajeEntrante mensaje) {
        if (!registro.operativo(mensaje.negocioId())) return;
        var entrada = registro.registrar(mensaje);
        var conversacion = conversaciones.bloquear(entrada.conversacionId());
        if (conversacion.modo() == ModoConversacion.HUMANO) {
            registro.guardarEntrante(conversacion.id(), mensaje);
            return;
        }
        Instant ahora = reloj.instant();
        boolean inactiva = entrada.entradaAnterior()
                .map(anterior -> !anterior.plus(propiedades.inactividad()).isAfter(ahora)).orElse(true);
        if (inactiva && (!conversacion.estado().isEmpty() || conversacion.clienteId().isPresent())) {
            conversaciones.vaciar(conversacion.id());
            operaciones.anularVigente(conversacion.id());
            conversacion = conversaciones.bloquear(conversacion.id());
        }
        boolean guardado = false;
        if (bandeja.entrantes() == Entrantes.TODOS) {
            registro.guardarEntrante(conversacion.id(), mensaje);
            guardado = true;
        }
        var interpretada = interprete.interpretar(mensaje.tipo(), mensaje.dato());
        boolean noReconocido = interpretada instanceof Entrada.Texto(var clave) && clave == PalabraClave.OTRA;
        if (!noReconocido && conversacion.estado().has(FALLOS)) {
            var estado = conversacion.estado().deepCopy();
            estado.remove(FALLOS);
            conversaciones.guardar(conversacion.id(), estado);
            conversacion = conversaciones.bloquear(conversacion.id());
        }
        var turno = new Turno(conversacion, mensaje, guardado, TiempoNegocio.fechaLima(ahora));
        switch (interpretada) {
            case Entrada.NoSoportada ignorada -> turno.menu(textos.noSoportado());
            case Entrada.Texto texto -> turno.texto(texto.clave());
            case Entrada.Seleccion seleccion -> turno.seleccion(seleccion.codigo());
        }
    }

    /** Un mensaje atendido: la conversación bloqueada y lo necesario para responder. */
    private final class Turno {
        private final EstadoConversacion conversacion;
        private final MensajeEntrante mensaje;
        private final String nombrePerfil;
        private final LocalDate hoy;
        private boolean guardado;

        Turno(EstadoConversacion conversacion, MensajeEntrante mensaje, boolean guardado, LocalDate hoy) {
            this.conversacion = conversacion;
            this.mensaje = mensaje;
            this.nombrePerfil = mensaje.nombrePerfil();
            this.guardado = guardado;
            this.hoy = hoy;
        }

        void texto(PalabraClave clave) {
            switch (clave) {
                case MENU -> inicio();
                case SI -> {
                    if (conversacion.clienteId().isEmpty()) {
                        bienvenida();
                        return;
                    }
                    var resultado = operaciones.confirmarVigente(conversacion.id(), ficha());
                    if (resultado instanceof ResultadoOperacion.NoVigente(var motivo) && motivo == MotivoNoVigente.NO_ENCONTRADA) {
                        bienvenida();
                    } else {
                        resultado(resultado);
                    }
                }
                case NO -> {
                    if (conversacion.clienteId().isPresent() && operaciones.anularVigente(conversacion.id())) {
                        resultado(new ResultadoOperacion.Anulada());
                    } else {
                        bienvenida();
                    }
                }
                case PERSONA -> pedirPersona(MotivoDerivacion.PETICION);
                case OTRA -> noReconocido();
            }
        }

        void seleccion(CodigoSeleccion codigo) {
            switch (codigo) {
                case Menu(var opcion) -> {
                    switch (opcion) {
                        case RESERVAR -> servicios(0);
                        case MIS_CITAS -> misCitas();
                        case INICIO -> inicio();
                        case PERSONA -> pedirPersona(MotivoDerivacion.PETICION);
                    }
                }
                case Privacidad(var acepta) -> privacidad(acepta);
                case PaginaServicios(var pagina) -> servicios(pagina);
                case Servicio(var servicioId) -> profesionales(servicioId, 0);
                case PaginaProfesionales(var servicioId, var pagina) -> profesionales(servicioId, pagina);
                case Profesional(var servicioId, var barberoId) -> dias(servicioId, barberoId);
                case Dia(var servicioId, var barberoId, var dia, var pagina) -> franjas(servicioId, barberoId, dia, pagina);
                case Franja franja -> elegirFranja(franja);
                case Cita(var reservaId) -> cita(reservaId);
                case AccionCita(var reservaId, var cancelar) -> accionCita(reservaId, cancelar);
                case DiaCambio(var reservaId, var dia, var pagina) -> franjasCambio(reservaId, dia, pagina);
                case FranjaCambio(var reservaId, var inicio) -> {
                    var instante = instante(inicio);
                    if (conversacion.clienteId().isEmpty() || instante == null) menu(textos.noDisponible());
                    else propuesta(operaciones.proponerReprogramacion(conversacion.id(), ficha(), reservaId, instante));
                }
                case Confirmacion(var operacionId, var nonce, var confirma) -> confirmacion(operacionId, nonce, confirma);
            }
        }

        private void inicio() {
            bienvenida();
        }

        /**
         * Texto no reconocido (V2-11): el primero responde con el aviso y el menú, y el contador sobrevive al
         * vaciado del menú; el segundo seguido deriva a una persona ({@code SIN_PROGRESO}).
         */
        private void noReconocido() {
            int fallos = conversacion.estado().path(FALLOS).asInt(0) + 1;
            if (fallos >= FALLOS_PARA_DERIVAR) {
                pedirPersona(MotivoDerivacion.SIN_PROGRESO);
                return;
            }
            menu(textos.noEntendido(fallos));
            var estado = conversaciones.bloquear(conversacion.id()).estado();
            estado.put(FALLOS, fallos);
            conversaciones.guardar(conversacion.id(), estado);
        }

        /**
         * Petición de una persona (16-v2-11 §2.3). Con ficha deriva y guarda el mensaje que la provoca; sin ficha
         * pide antes el consentimiento con la derivación como pendiente (V2-DA-71).
         */
        private void pedirPersona(MotivoDerivacion motivo) {
            if (conversacion.clienteId().isPresent()) {
                derivar(motivo);
                return;
            }
            operaciones.anularVigente(conversacion.id());
            var estado = conversaciones.bloquear(conversacion.id()).estado();
            estado.removeAll();
            estado.put(ConversacionesWhatsApp.PENDIENTE, new Menu(OpcionMenu.PERSONA).codificar());
            estado.put(MOTIVO, motivo.name());
            estado.put(ConversacionesWhatsApp.PASO, "CONSENTIMIENTO");
            conversaciones.guardar(conversacion.id(), estado);
            var privacidad = propiedades.privacidad();
            enviar(menus.consentimiento(textos.privacidad(privacidad.texto(), consultas.nombreNegocio(),
                    privacidad.enlace())), null);
        }

        /** Guarda el mensaje (una sola vez) y deriva; la derivación anula la vigente, vacía el estado y avisa. */
        private void derivar(MotivoDerivacion motivo) {
            guardarEntrante();
            derivacion.derivar(conversacion.id(), motivo);
        }

        private void guardarEntrante() {
            if (!guardado) {
                registro.guardarEntrante(conversacion.id(), mensaje);
                guardado = true;
            }
        }

        private void bienvenida() {
            menu(textos.bienvenida(consultas.nombreNegocio()));
        }

        private void servicios(int pagina) {
            var lista = consultas.servicios();
            if (lista.isEmpty()) menu(textos.sinOpciones());
            else enviar(menus.servicios(textos.elegirServicio(), lista, pagina), "SERVICIO");
        }

        private void profesionales(long servicioId, int pagina) {
            var servicio = consultas.servicio(servicioId);
            if (servicio.isEmpty()) {
                menu(textos.noDisponible());
                return;
            }
            var lista = consultas.profesionales(servicioId);
            if (lista.isEmpty()) menu(textos.sinOpciones());
            else enviar(menus.profesionales(textos.elegirProfesional(servicio.get().nombre()), servicioId, lista, pagina),
                    "PROFESIONAL");
        }

        private void dias(long servicioId, long barberoId) {
            if (consultas.servicio(servicioId).isEmpty() || (barberoId != 0
                    && consultas.profesionales(servicioId).stream().noneMatch(p -> p.id() == barberoId))) {
                menu(textos.noDisponible());
                return;
            }
            enviar(menus.dias(textos.elegirDia(), servicioId, barberoId, hoy), "DIA");
        }

        private void franjas(long servicioId, long barberoId, LocalDate dia, int pagina) {
            if (!diaOfrecido(dia)) {
                enviar(menus.dias(textos.noDisponible(), servicioId, barberoId, hoy), "DIA");
                return;
            }
            var lista = consultas.franjas(servicioId,
                    barberoId == 0 ? OptionalLong.empty() : OptionalLong.of(barberoId), dia);
            if (lista.isEmpty()) enviar(menus.dias(textos.sinOpciones(), servicioId, barberoId, hoy), "DIA");
            else enviar(menus.franjas(textos.elegirFranja(dia), servicioId, barberoId, dia, lista, pagina), "FRANJA");
        }

        private void elegirFranja(Franja franja) {
            var inicio = instante(franja.inicioEpochSeg());
            if (inicio == null) {
                menu(textos.noDisponible());
                return;
            }
            if (conversacion.clienteId().isEmpty()) {
                var estado = conversacion.estado().deepCopy();
                estado.put(ConversacionesWhatsApp.PENDIENTE, franja.codificar());
                estado.put(ConversacionesWhatsApp.PASO, "CONSENTIMIENTO");
                conversaciones.guardar(conversacion.id(), estado);
                var privacidad = propiedades.privacidad();
                enviar(menus.consentimiento(textos.privacidad(privacidad.texto(), consultas.nombreNegocio(),
                        privacidad.enlace())), null);
                return;
            }
            propuesta(operaciones.proponerCreacion(conversacion.id(), ficha(), franja.servicioId(), franja.barberoId(),
                    inicio));
        }

        /**
         * [Aceptar] solo crea la ficha si el servidor presentó el aviso en esta conversación (paso
         * {@code CONSENTIMIENTO} con una franja pendiente válida, V2-RF-15). Un {@code p:S} sin esa solicitud
         * no crea nada; con la ficha ya enlazada, la aceptación es idempotente.
         */
        private void privacidad(boolean acepta) {
            var pendiente = conversacion.estado().path(ConversacionesWhatsApp.PENDIENTE).asString(null);
            boolean paraDerivar = new Menu(OpcionMenu.PERSONA).codificar().equals(pendiente);
            if (!acepta) {
                if (conversacion.clienteId().isEmpty()) conversaciones.vaciar(conversacion.id());
                menu(paraDerivar ? textos.consentimientoDerivacionRechazado() : textos.privacidadRechazada());
                return;
            }
            boolean pedido = "CONSENTIMIENTO".equals(conversacion.estado().path(ConversacionesWhatsApp.PASO).asString(null));
            if (paraDerivar && pedido) {
                var motivo = motivoPendiente();
                consentimiento.aceptar(conversacion, nombrePerfil);
                conversaciones.vaciar(conversacion.id());
                derivar(motivo);
                return;
            }
            var franja = pendiente == null ? null
                    : CodigoSeleccion.decodificar(pendiente).filter(Franja.class::isInstance).map(Franja.class::cast).orElse(null);
            boolean solicitado = "CONSENTIMIENTO".equals(conversacion.estado().path(ConversacionesWhatsApp.PASO).asString(null));
            Instant inicio = franja == null ? null : instante(franja.inicioEpochSeg());
            if (conversacion.clienteId().isEmpty() && (!solicitado || inicio == null)) {
                bienvenida();
                return;
            }
            long ficha = consentimiento.aceptar(conversacion, nombrePerfil);
            conversaciones.vaciar(conversacion.id());
            if (inicio == null) {
                bienvenida();
                return;
            }
            propuesta(operaciones.proponerCreacion(conversacion.id(), ficha, franja.servicioId(), franja.barberoId(), inicio));
        }

        /** Motivo guardado con la derivación pendiente; uno desconocido cuenta como petición del cliente. */
        private MotivoDerivacion motivoPendiente() {
            try {
                return MotivoDerivacion.valueOf(conversacion.estado().path(MOTIVO).asString(MotivoDerivacion.PETICION.name()));
            } catch (IllegalArgumentException desconocido) {
                return MotivoDerivacion.PETICION;
            }
        }

        private void misCitas() {
            if (conversacion.clienteId().isEmpty()) {
                menu(textos.sinCitas());
                return;
            }
            var citas = consultas.citasFuturas(ficha());
            if (citas.isEmpty()) menu(textos.sinCitas());
            else enviar(menus.citas(textos.misCitas(), citas), "CITAS");
        }

        private void cita(long reservaId) {
            var cita = conversacion.clienteId().isEmpty() ? null : consultas.cita(ficha(), reservaId).orElse(null);
            if (cita == null) menu(textos.noDisponible());
            else enviar(menus.accionesCita(textos.detalleCita(cita), cita), "CITA");
        }

        private void accionCita(long reservaId, boolean cancelar) {
            if (conversacion.clienteId().isEmpty()) {
                menu(textos.noDisponible());
                return;
            }
            if (cancelar) {
                propuesta(operaciones.proponerCancelacion(conversacion.id(), ficha(), reservaId));
                return;
            }
            var cita = consultas.cita(ficha(), reservaId);
            if (cita.isEmpty() || !cita.get().puedeReprogramar()) menu(textos.noDisponible());
            else enviar(menus.diasCambio(textos.elegirDia(), reservaId, hoy), "DIA_CAMBIO");
        }

        private void franjasCambio(long reservaId, LocalDate dia, int pagina) {
            var cita = conversacion.clienteId().isEmpty() ? null : consultas.cita(ficha(), reservaId).orElse(null);
            if (cita == null || !cita.puedeReprogramar()) {
                menu(textos.noDisponible());
                return;
            }
            if (!diaOfrecido(dia)) {
                enviar(menus.diasCambio(textos.noDisponible(), reservaId, hoy), "DIA_CAMBIO");
                return;
            }
            var lista = consultas.franjasCambio(ficha(), reservaId, dia);
            if (lista.isEmpty()) enviar(menus.diasCambio(textos.sinOpciones(), reservaId, hoy), "DIA_CAMBIO");
            else enviar(menus.franjasCambio(textos.elegirFranja(dia), reservaId, dia, lista, pagina), "FRANJA_CAMBIO");
        }

        private void confirmacion(long operacionId, String nonce, boolean confirma) {
            if (conversacion.clienteId().isEmpty()) {
                resultado(new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
                return;
            }
            resultado(confirma ? operaciones.confirmar(conversacion.id(), ficha(), operacionId, nonce)
                    : operaciones.anular(conversacion.id(), ficha(), operacionId, nonce));
        }

        /** Propuesta nula = la conversación aún no tiene ficha (no se puede proponer). */
        private void propuesta(Propuesta propuesta) {
            switch (propuesta) {
                case null -> menu(textos.noDisponible());
                case Propuesta.Creada creada -> enviar(menus.confirmacion(textos.resumen(creada.resumen()),
                        creada.operacionId(), creada.nonce()), "CONFIRMACION");
                case Propuesta.Rechazada rechazada -> menu(textos.rechazo(rechazada.codigo()));
            }
        }

        /**
         * Responde al resultado de una confirmación. {@code Ejecutada} (también repetida) muestra el menú y,
         * como todo menú, anula cualquier otra vigente (revisión 120-1). {@code NoVigente} responde solo con
         * texto y no toca la vigente: una pulsación inválida o antigua no cambia nada (CJ-5; revisión 120-2).
         */
        private void resultado(ResultadoOperacion resultado) {
            switch (resultado) {
                case ResultadoOperacion.Ejecutada ejecutada -> {
                    operaciones.anularVigente(conversacion.id());
                    conversaciones.vaciar(conversacion.id());
                    salida.encolarInteractivo(conversacion.id(), menus.menuPrincipal(textos.resultado(resultado)),
                            OrigenMensaje.SISTEMA, OptionalLong.of(ejecutada.cita().reservaId()));
                }
                case ResultadoOperacion.NoVigente ignorada -> salida.encolarTexto(conversacion.id(),
                        textos.resultado(resultado), OrigenMensaje.ASISTENTE, OptionalLong.empty());
                default -> menu(textos.resultado(resultado));
            }
        }

        /**
         * Muestra el menú principal. Mostrarlo equivale a volver al menú (13-v2-10 §8; revisión 119-1): anula
         * la operación vigente y vacía el estado, así un «sí» posterior no confirma un resumen anterior.
         */
        private void menu(String cuerpo) {
            operaciones.anularVigente(conversacion.id());
            conversaciones.vaciar(conversacion.id());
            enviar(menus.menuPrincipal(cuerpo), null);
        }

        /** Encola la respuesta y, si se indica, anota el paso mostrado (informativo). */
        private void enviar(ObjectNode interactivo, String paso) {
            if (paso != null) {
                var estado = conversaciones.bloquear(conversacion.id()).estado();
                estado.put(ConversacionesWhatsApp.PASO, paso);
                conversaciones.guardar(conversacion.id(), estado);
            }
            salida.encolarInteractivo(conversacion.id(), interactivo, OrigenMensaje.ASISTENTE, OptionalLong.empty());
        }

        private long ficha() {
            return conversacion.clienteId().orElseThrow();
        }

        /** Inicio de una selección, o nulo si no es un instante representable (revisión 119-3). */
        private Instant instante(long epochSeg) {
            return epochSeg <= 0 || epochSeg > INSTANTE_MAXIMO ? null : Instant.ofEpochSecond(epochSeg);
        }

        private boolean diaOfrecido(LocalDate dia) {
            return !dia.isBefore(hoy) && dia.isBefore(hoy.plusDays(DIAS_OFRECIDOS));
        }
    }
}
