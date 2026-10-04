package pe.barberturno.reservations;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.audit.*;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.users.*;

/**
 * Creación RF-08, reprogramación RF-09 con bloqueos ① ② ③ y cancelación RF-10; trazabilidad atómica RN-15.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ReservaService {
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final ServicioRepository servicios;
    private final DisponibilidadService disponibilidad;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;
    private final Clock clock;
    private final ParametrosReserva parametros;
    private final ReservaAutorizacion autorizacion;

    /**
     * Recibe colaboradores de creación, reprogramación y cancelación atómicas.
     * @param reservas persistencia y consultas RN-04/20
     * @param usuarios bloqueo ① del cliente
     * @param barberos bloqueo ② de agenda
     * @param servicios catálogo y referencias RN-13
     * @param disponibilidad validador común RN-05
     * @param auditoria escritura del historial RN-15
     * @param notificaciones escritura de avisos RN-15
     * @param clock reloj inyectado del servidor
     * @param parametros límite, confirmación y anticipación RN-07/20/21
     * @param autorizacion política única de visibilidad por propietario, asignación o ADMIN
     */
    public ReservaService(ReservaRepository reservas, UsuarioRepository usuarios, BarberoRepository barberos,
            ServicioRepository servicios, DisponibilidadService disponibilidad, AuditoriaService auditoria,
            NotificacionService notificaciones, Clock clock, ParametrosReserva parametros, ReservaAutorizacion autorizacion) {
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.servicios = servicios;
        this.disponibilidad = disponibilidad;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.clock = clock;
        this.parametros = parametros;
        this.autorizacion = autorizacion;
    }

    /**
     * Crea para el propio CLIENTE después de serializar cliente y agenda en ese orden (§8.1).
     * Guarda referencias, auditoría y avisos en la misma transacción; saveAndFlush activa la red GiST.
     * @param cmd solicitud validada por la API, sin clienteId asistido
     * @param actor identidad vigente CLIENTE, registrada como creador y actor
     * @return reserva creada con código BT, instantes Lima y permisos DA-15
     * @throws NegocioException si el rol o clienteId no está autorizado (403), falta un recurso (404),
     * hay solapes (409), recursos inactivos, franja inválida o límite excedido (422)
     * @throws org.springframework.dao.DataIntegrityViolationException si una restricción impide guardar
     */
    @Transactional
    public ReservaDto crear(CrearReservaDto cmd, UsuarioAutenticado actor) {
        if (actor.rol() != Rol.CLIENTE || cmd.clienteId() != null) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Solo puede reservar para usted.");
        }
        // Solo existencia antes de esperar: no precargar entidades que quedarían obsoletas en el contexto JPA.
        if (!servicios.existsById(cmd.servicioId()) || !barberos.existsById(cmd.barberoId())) throw noEncontrado();
        var cliente = usuarios.bloquearPorId(actor.id()).orElseThrow(this::noEncontrado); // ①
        var perfiles = barberos.bloquearPorIds(List.of(cmd.barberoId())); // ②
        if (perfiles.isEmpty()) throw noEncontrado();
        var barbero = perfiles.getFirst();
        var servicio = servicios.findById(cmd.servicioId()).orElseThrow(this::noEncontrado);
        if (!servicio.isActivo() || !barbero.isActivo()) {
            throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO, "El servicio o barbero está inactivo.");
        }
        Instant inicio = cmd.inicio().toInstant();
        Instant fin = new ReglasTemporales().calcularFin(inicio, servicio.getDuracionMin());
        disponibilidad.validarFranja(barbero.getId(), inicio, fin, 0L).ifPresent(codigo -> {
            throw new NegocioException(codigo, "La franja solicitada no cumple la disponibilidad.");
        });
        if (!reservas.buscarSolapamientosCliente(cliente.getId(), inicio, fin, 0L).isEmpty()) {
            throw new NegocioException(ErrorCodigo.CLIENTE_CON_RESERVA_SOLAPADA,
                    "Ya tiene una reserva que se cruza con esta franja.");
        }
        Instant ahora = clock.instant();
        if (reservas.contarFuturasQueOcupan(cliente.getId(), ahora) >= parametros.maxActivasPorCliente()) {
            throw new NegocioException(ErrorCodigo.LIMITE_RESERVAS_ACTIVAS,
                    "Ha alcanzado el límite de reservas futuras.");
        }
        EstadoReserva estado = parametros.confirmacionManual() ? EstadoReserva.PENDIENTE : EstadoReserva.CONFIRMADA;
        var reserva = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, inicio, estado, cliente, ahora));
        auditoria.registrarCambio(reserva, cliente, AccionAuditoria.CREAR, null, null,
                Map.of("inicio", TiempoNegocio.aLima(inicio).toString(), "fin", TiempoNegocio.aLima(fin).toString(),
                        "barberoId", barbero.getId(), "servicioId", servicio.getId(), "estado", estado.name()),
                null, false);
        String mensaje = "Reserva BT-" + reserva.getId() + " creada: " + servicio.getNombre()
                + " con " + barbero.getUsuario().getNombre() + ", "
                + TiempoNegocio.aLima(inicio).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE")))
                + " (hora de Lima).";
        notificaciones.notificar(cliente, reserva, AccionAuditoria.CREAR, mensaje);
        if (barbero.getUsuario().getId() != actor.id()) {
            notificaciones.notificar(barbero.getUsuario(), reserva, AccionAuditoria.CREAR, mensaje);
        }
        return ReservaDto.desde(reserva, actor, clock.instant(), parametros);
    }

    /**
     * Cancela RF-10 bajo bloqueo ③, sin borrar historia, liberando la franja RN-14.
     * Revalida visibilidad, rol, versión, estado y RN-07/08 después de esperar por la reserva;
     * el cambio, auditoría y avisos RN-15 se confirman o revierten juntos.
     * @param id identidad de la reserva que se bloquea
     * @param cmd versión leída y motivo validado de hasta 300 caracteres
     * @param actor identidad vigente CLIENTE propietario o ADMIN
     * @return reserva CANCELADA con versión incrementada y permisos recalculados
     * @throws NegocioException si es inexistente o ajena (404), el rol no cancela (403),
     * la versión o estado no permiten la acción (409), o la ventana o motivo incumplen RN-07/08 (422)
     * @throws org.springframework.dao.DataIntegrityViolationException si falla una escritura de la transacción
     */
    @Transactional
    public ReservaDto cancelar(long id, CancelarReservaDto cmd, UsuarioAutenticado actor) {
        var reserva = reservas.bloquearPorId(id).orElseThrow(this::noEncontrado); // ③, sin precarga
        if (!autorizacion.puedeVer(actor, reserva)) throw noEncontrado();
        boolean admin = actor.rol() == Rol.ADMIN;
        if (!admin && actor.rol() != Rol.CLIENTE) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Su rol no permite cancelar reservas.");
        }
        if (cmd.version() != reserva.getVersion()) {
            throw new NegocioException(ErrorCodigo.VERSION_DESACTUALIZADA, "La reserva cambió. Actualice sus datos.");
        }
        EstadoReserva anterior = reserva.getEstado();
        if (anterior != EstadoReserva.PENDIENTE && anterior != EstadoReserva.CONFIRMADA) {
            throw new NegocioException(ErrorCodigo.TRANSICION_INVALIDA, "El estado actual no permite cancelar.");
        }
        Instant ahora = clock.instant();
        var reglas = new ReglasTemporales();
        // Distingue la ventana vencida del motivo ausente usando la misma política pura.
        if (!reglas.puedeModificar(ahora, reserva.getInicio(), admin,
                admin ? "Motivo requerido" : cmd.motivo(), parametros.anticipacionCambioCliente())) {
            throw new NegocioException(ErrorCodigo.FUERA_DE_POLITICA, "La reserva está fuera del plazo de cancelación.");
        }
        if (admin && !reglas.puedeModificar(ahora, reserva.getInicio(), true,
                cmd.motivo(), parametros.anticipacionCambioCliente())) {
            throw new NegocioException(ErrorCodigo.MOTIVO_REQUERIDO,
                    "Indique un motivo de al menos cinco caracteres no blancos.");
        }
        boolean excepcional = admin && !reglas.puedeModificar(ahora, reserva.getInicio(), false,
                null, parametros.anticipacionCambioCliente());
        var datosAnteriores = datosCancelacion(reserva);
        reserva.cambiarEstado(EstadoReserva.CANCELADA, ahora);
        reservas.saveAndFlush(reserva);
        auditoria.registrarCambio(reserva, usuarios.getReferenceById(actor.id()), AccionAuditoria.CANCELAR,
                anterior, datosAnteriores, datosCancelacion(reserva), cmd.motivo(), excepcional);
        String mensaje = "Reserva BT-" + reserva.getId() + " cancelada: "
                + TiempoNegocio.aLima(reserva.getInicio()).format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE")))
                + " (hora de Lima).";
        notificaciones.notificar(reserva.getCliente(), reserva, AccionAuditoria.CANCELAR, mensaje);
        var asignado = reserva.getBarbero().getUsuario();
        if (asignado.getId() != actor.id()) {
            notificaciones.notificar(asignado, reserva, AccionAuditoria.CANCELAR, mensaje);
        }
        return ReservaDto.desde(reserva, actor, clock.instant(), parametros);
    }

    /**
     * Reprograma RF-09 conservando referencias RN-13, bajo el orden global ① ② ③ (§8).
     * La lectura inicial escalar no deja entidades obsoletas; versión, estado y política se
     * revalidan tras los bloqueos. Reserva, auditoría y avisos se revierten juntos ante cualquier fallo.
     * @param id reserva propia o administrada que se desea mover
     * @param cmd nuevo inicio, barbero opcional, versión y motivo de la acción
     * @param actor CLIENTE propietario o ADMIN con motivo RN-08
     * @return reserva con nueva asignación, intervalo, versión y permisos actualizados
     * @throws NegocioException si es inexistente o ajena (404), el rol no puede actuar (403),
     * hay versión antigua, estado inválido o solapes (409), o recursos/política/franja inválidos (422)
     * @throws org.springframework.dao.DataIntegrityViolationException si una restricción impide guardar
     */
    @Transactional
    public ReservaDto reprogramar(long id, ReprogramarReservaDto cmd, UsuarioAutenticado actor) {
        var lectura = reservas.leerParaReprogramar(id).orElseThrow(this::noEncontrado);
        if (!autorizacion.puedeVer(actor, lectura.clienteId(), lectura.asignadoId())) throw noEncontrado();
        if (actor.rol() != Rol.ADMIN && actor.rol() != Rol.CLIENTE) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Su rol no permite reprogramar reservas.");
        }
        validarReprogramacion(cmd, actor, lectura.version(), lectura.estado(), lectura.inicio(), clock.instant());
        long nuevoId = cmd.barberoId() == null ? lectura.barberoId() : cmd.barberoId();
        usuarios.bloquearPorId(lectura.clienteId()).orElseThrow(this::noEncontrado); // ①
        var ids = new TreeSet<Long>(List.of(lectura.barberoId(), nuevoId));
        var perfiles = barberos.bloquearPorIds(ids); // ②, únicos y ascendentes
        if (perfiles.size() != ids.size()) throw noEncontrado();
        var reserva = reservas.bloquearPorId(id).orElseThrow(this::noEncontrado); // ③, sin entidad precargada
        Instant ahora = clock.instant();
        boolean excepcional = validarReprogramacion(cmd, actor, reserva.getVersion(),
                reserva.getEstado(), reserva.getInicio(), ahora);
        var nuevo = perfiles.stream().filter(b -> b.getId() == nuevoId).findFirst().orElseThrow(this::noEncontrado);
        if (!nuevo.isActivo()) {
            throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO, "El barbero de destino está inactivo.");
        }
        Instant inicio = cmd.inicio().toInstant();
        Instant fin = new ReglasTemporales().calcularFin(inicio, reserva.getDuracionRefMin());
        disponibilidad.validarFranja(nuevoId, inicio, fin, id).ifPresent(codigo -> {
            throw new NegocioException(codigo, "La franja solicitada no cumple la disponibilidad.");
        });
        if (!reservas.buscarSolapamientosCliente(lectura.clienteId(), inicio, fin, id).isEmpty()) {
            throw new NegocioException(ErrorCodigo.CLIENTE_CON_RESERVA_SOLAPADA,
                    "Ya tiene una reserva que se cruza con esta franja.");
        }
        var anterior = reserva.getBarbero().getUsuario();
        var datosAnteriores = datosReprogramacion(reserva);
        reserva.reprogramar(nuevo, inicio, ahora);
        reservas.saveAndFlush(reserva);
        auditoria.registrarCambio(reserva, usuarios.getReferenceById(actor.id()), AccionAuditoria.REPROGRAMAR,
                EstadoReserva.CONFIRMADA, datosAnteriores, datosReprogramacion(reserva), cmd.motivo(), excepcional);
        String mensaje = "Reserva BT-" + id + " reprogramada: "
                + TiempoNegocio.aLima(inicio).format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE")))
                + " (hora de Lima), con " + nuevo.getUsuario().getNombre() + ".";
        var destinatarios = new LinkedHashMap<Long, Usuario>();
        for (var usuario : List.of(reserva.getCliente(), anterior, nuevo.getUsuario())) {
            if (usuario.getId() != actor.id()) destinatarios.put(usuario.getId(), usuario);
        }
        destinatarios.values().forEach(usuario ->
                notificaciones.notificar(usuario, reserva, AccionAuditoria.REPROGRAMAR, mensaje));
        return ReservaDto.desde(reserva, actor, clock.instant(), parametros);
    }

    private boolean validarReprogramacion(ReprogramarReservaDto cmd, UsuarioAutenticado actor,
            int version, EstadoReserva estado, Instant inicio, Instant ahora) {
        if (cmd.version() != version) {
            throw new NegocioException(ErrorCodigo.VERSION_DESACTUALIZADA, "La reserva cambió. Actualice sus datos.");
        }
        if (estado != EstadoReserva.CONFIRMADA) {
            throw new NegocioException(ErrorCodigo.TRANSICION_INVALIDA, "El estado actual no permite reprogramar.");
        }
        boolean admin = actor.rol() == Rol.ADMIN;
        var reglas = new ReglasTemporales();
        if (!reglas.puedeModificar(ahora, inicio, admin, admin ? "Motivo requerido" : cmd.motivo(),
                parametros.anticipacionCambioCliente())) {
            throw new NegocioException(ErrorCodigo.FUERA_DE_POLITICA, "La reserva está fuera del plazo de reprogramación.");
        }
        if (admin && !reglas.puedeModificar(ahora, inicio, true, cmd.motivo(), parametros.anticipacionCambioCliente())) {
            throw new NegocioException(ErrorCodigo.MOTIVO_REQUERIDO,
                    "Indique un motivo de al menos cinco caracteres no blancos.");
        }
        return admin && !reglas.puedeModificar(ahora, inicio, false, null, parametros.anticipacionCambioCliente());
    }

    private Map<String, Object> datosReprogramacion(Reserva reserva) {
        return Map.of("inicio", TiempoNegocio.aLima(reserva.getInicio()).toString(),
                "fin", TiempoNegocio.aLima(reserva.getFin()).toString(), "barberoId", reserva.getBarbero().getId());
    }

    private Map<String, Object> datosCancelacion(Reserva reserva) {
        return Map.of("inicio", TiempoNegocio.aLima(reserva.getInicio()).toString(),
                "fin", TiempoNegocio.aLima(reserva.getFin()).toString(),
                "barberoId", reserva.getBarbero().getId(), "estado", reserva.getEstado().name());
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el recurso solicitado.");
    }
}
