package pe.barberturno.reservations;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.audit.*;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.config.ReglasNegocioService;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.users.*;
import pe.barberturno.customers.ClienteRepository;

/**
 * Creación RF-08, reprogramación RF-09, cancelación RF-10 y transiciones RF-12.
 * Aplica los bloqueos de §8 y la trazabilidad atómica RN-15.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ReservaService {
    private final ReservaRepository reservas;
    private final jakarta.persistence.EntityManager entidades;
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final BarberoRepository barberos;
    private final ServicioRepository servicios;
    private final DisponibilidadService disponibilidad;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;
    private final Clock clock;
    private final ReglasNegocioService reglasNegocio;
    private final ReservaAutorizacion autorizacion;

    /**
     * Recibe colaboradores de creación, reprogramación, cancelación y transiciones atómicas.
     * @param reservas persistencia y consultas RN-04/20
     * @param usuarios identidades globales para creador, auditoría y avisos
     * @param clientes fichas y bloqueo ① por negocio (V2-RN-08)
     * @param barberos bloqueo ② de agenda
     * @param servicios catálogo y referencias RN-13
     * @param disponibilidad validador común RN-05
     * @param auditoria escritura del historial RN-15
     * @param notificaciones escritura de avisos RN-15
     * @param clock reloj inyectado del servidor
     * @param reglasNegocio lectura transaccional del límite, confirmación y anticipación RN-07/20/21
     * @param entidades refresco bajo bloqueos para evitar entidades obsoletas en la entrega
     * @param autorizacion política única de visibilidad por propietario, asignación o ADMIN
     */
    public ReservaService(ReservaRepository reservas, UsuarioRepository usuarios, ClienteRepository clientes, BarberoRepository barberos,
            ServicioRepository servicios, DisponibilidadService disponibilidad, AuditoriaService auditoria,
            NotificacionService notificaciones, Clock clock, ReglasNegocioService reglasNegocio, ReservaAutorizacion autorizacion, jakarta.persistence.EntityManager entidades) {
        this.entidades = entidades;
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.barberos = barberos;
        this.servicios = servicios;
        this.disponibilidad = disponibilidad;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.clock = clock;
        this.reglasNegocio = reglasNegocio;
        this.autorizacion = autorizacion;
    }

    /**
     * Crea para el propio CLIENTE o para un CLIENTE elegido por ADMIN (RF-18), con bloqueos ① y ②.
     * Guarda referencias, auditoría y avisos en la misma transacción; saveAndFlush activa la red GiST.
     * @param cmd solicitud validada; clienteId obligatorio para ADMIN y prohibido para CLIENTE
     * @param actor identidad vigente CLIENTE o ADMIN, registrada como creador y actor
     * @return reserva creada con código BT, instantes Lima y permisos DA-15
     * @throws NegocioException si el rol o clienteId no está autorizado (403), falta un recurso (404),
     * hay solapes (409), recursos inactivos, franja inválida o límite excedido (422)
     * @throws org.springframework.dao.DataIntegrityViolationException si una restricción impide guardar
     */
    @Transactional
    public ReservaDto crear(CrearReservaDto cmd, UsuarioAutenticado actor) {
        var parametros = reglasNegocio.reservas();
        boolean asistida = actor.rol() == Rol.ADMIN;
        if (!asistida && (actor.rol() != Rol.CLIENTE || cmd.clienteId() != null)) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Solo puede reservar para usted.");
        }
        if (asistida && cmd.clienteId() == null) {
            throw new NegocioException(ErrorCodigo.VALIDACION, "Seleccione el cliente de la reserva asistida.");
        }
        // Solo existencia antes de esperar: no precargar entidades que quedarían obsoletas en el contexto JPA.
        if (!servicios.existsById(cmd.servicioId()) || !barberos.existsById(cmd.barberoId())) throw noEncontrado();
        long cuentaId = asistida ? cmd.clienteId() : actor.id();
        long fichaId = clientes.buscarIdPorUsuario(cuentaId).orElseThrow(this::noEncontrado);
        var cliente = clientes.bloquearPorId(fichaId).orElseThrow(this::noEncontrado); // ①
        if (!cliente.isActivo()) throw noEncontrado();
        if (!cliente.getUsuario().isActivo()) {
            throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO, "El cliente está inactivo.");
        }
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
        if (!asistida && reservas.contarFuturasQueOcupan(cliente.getId(), ahora) >= parametros.maxActivasPorCliente()) {
            throw new NegocioException(ErrorCodigo.LIMITE_RESERVAS_ACTIVAS,
                    "Ha alcanzado el límite de reservas futuras.");
        }
        EstadoReserva estado = !asistida && parametros.confirmacionManual() ? EstadoReserva.PENDIENTE : EstadoReserva.CONFIRMADA;
        var creador = asistida ? usuarios.getReferenceById(actor.id()) : cliente.getUsuario();
        var reserva = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, inicio, estado, creador, ahora));
        auditoria.registrarCambio(reserva, creador, AccionAuditoria.CREAR, null, null,
                Map.of("inicio", TiempoNegocio.aLima(inicio).toString(), "fin", TiempoNegocio.aLima(fin).toString(),
                        "barberoId", barbero.getId(), "servicioId", servicio.getId(), "estado", estado.name()),
                null, false);
        String mensaje = "Reserva BT-" + reserva.getId() + " creada: " + servicio.getNombre()
                + " con " + barbero.getNombreVisible() + ", "
                + TiempoNegocio.aLima(inicio).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE")))
                + " (hora de Lima).";
        notificaciones.notificar(cliente.getUsuario(), reserva, AccionAuditoria.CREAR, mensaje);
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
        var parametros = reglasNegocio.reservas();
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
        if (reserva.getCliente().getUsuario() != null) {
            notificaciones.notificar(reserva.getCliente().getUsuario(), reserva, AccionAuditoria.CANCELAR, mensaje);
        }
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
     * RN-15 siempre avisa al cliente; solo los barberos quedan excluidos cuando son el propio actor.
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
        var parametros = reglasNegocio.reservas();
        var lectura = reservas.leerParaReprogramar(id).orElseThrow(this::noEncontrado);
        if (!autorizacion.puedeVer(actor, lectura.usuarioId(), lectura.asignadoId())) throw noEncontrado();
        if (actor.rol() != Rol.ADMIN && actor.rol() != Rol.CLIENTE) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Su rol no permite reprogramar reservas.");
        }
        validarReprogramacion(cmd, actor, lectura.version(), lectura.estado(), lectura.inicio(), clock.instant(), parametros);
        long nuevoId = cmd.barberoId() == null ? lectura.barberoId() : cmd.barberoId();
        clientes.bloquearPorId(lectura.clienteId()).orElseThrow(this::noEncontrado); // ①
        var ids = new TreeSet<Long>(List.of(lectura.barberoId(), nuevoId));
        var perfiles = barberos.bloquearPorIds(ids); // ②, únicos y ascendentes
        if (perfiles.size() != ids.size()) throw noEncontrado();
        var reserva = reservas.bloquearPorId(id).orElseThrow(this::noEncontrado); // ③, sin entidad precargada
        Instant ahora = clock.instant();
        boolean excepcional = validarReprogramacion(cmd, actor, reserva.getVersion(),
                reserva.getEstado(), reserva.getInicio(), ahora, parametros);
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
                + " (hora de Lima), con " + nuevo.getNombreVisible() + ".";
        var destinatarios = new LinkedHashMap<Long, Usuario>();
        if (reserva.getCliente().getUsuario() != null) {
            destinatarios.put(reserva.getCliente().getUsuario().getId(), reserva.getCliente().getUsuario());
        }
        for (var usuario : List.of(anterior, nuevo.getUsuario())) {
            if (usuario.getId() != actor.id()) destinatarios.put(usuario.getId(), usuario);
        }
        destinatarios.values().forEach(usuario ->
                notificaciones.notificar(usuario, reserva, AccionAuditoria.REPROGRAMAR, mensaje));
        return ReservaDto.desde(reserva, actor, clock.instant(), parametros);
    }

    /**
     * Aplica RN-10/11/12 bajo bloqueo ③; reserva, auditoría y avisos RN-15 son atómicos.
     * Evalúa visibilidad, destino operativo, versión y política después de adquirir el bloqueo.
     * @param id identidad de la reserva que se bloquea sin precarga JPA
     * @param cmd destino obligatorio y versión optimista leída por el actor
     * @param actor BARBERO asignado o ADMIN con identidad revalidada
     * @return reserva con estado, versión y permisos actualizados para el actor
     * @throws NegocioException si es inexistente o ajena (404), el destino no es operativo (400),
     * el rol no está autorizado (403), la versión o transición son inválidas (409), o falla RN-12 (422)
     * @throws org.springframework.dao.DataIntegrityViolationException si falla una escritura de la transacción
     */
    @Transactional
    public ReservaDto transicionar(long id, TransicionarReservaDto cmd, UsuarioAutenticado actor) {
        var parametros = reglasNegocio.reservas();
        var reserva = reservas.bloquearPorId(id).orElseThrow(this::noEncontrado); // ③, sin precarga
        if (!autorizacion.puedeVer(actor, reserva)) throw noEncontrado();
        if (cmd.estado() == null || cmd.estado() == EstadoReserva.PENDIENTE || cmd.estado() == EstadoReserva.CANCELADA) {
            throw new NegocioException(ErrorCodigo.VALIDACION, "Seleccione un destino operativo válido.");
        }
        if (cmd.version() != reserva.getVersion()) {
            throw new NegocioException(ErrorCodigo.VERSION_DESACTUALIZADA, "La reserva cambió. Actualice sus datos.");
        }
        EstadoReserva anterior = reserva.getEstado();
        Instant ahora = clock.instant();
        var resultado = new PoliticaTransiciones().evaluar(actor.rol(),
                actor.id() == reserva.getBarbero().getUsuario().getId(), anterior, cmd.estado(),
                ahora, reserva.getInicio(), Duration.ofMinutes(parametros.toleranciaInicioMin()),
                parametros.anticipacionCambioCliente(), null);
        switch (resultado) {
            case TRANSICION_INVALIDA -> throw new NegocioException(ErrorCodigo.TRANSICION_INVALIDA,
                    "El estado actual no permite la transición solicitada.");
            case PROHIBIDO -> throw new NegocioException(ErrorCodigo.PROHIBIDO,
                    "Su rol no permite esta transición.");
            case FUERA_DE_VENTANA -> throw new NegocioException(ErrorCodigo.FUERA_DE_VENTANA,
                    "La reserva está fuera de la ventana de atención.");
            case PERMITIDA -> { }
        }
        var accion = Map.of(EstadoReserva.CONFIRMADA, AccionAuditoria.CONFIRMAR,
                EstadoReserva.EN_ATENCION, AccionAuditoria.INICIAR,
                EstadoReserva.COMPLETADA, AccionAuditoria.COMPLETAR,
                EstadoReserva.NO_ASISTIO, AccionAuditoria.NO_ASISTIO).get(cmd.estado());
        String etiqueta = Map.of(EstadoReserva.CONFIRMADA, "confirmada", EstadoReserva.EN_ATENCION, "en atención",
                EstadoReserva.COMPLETADA, "completada", EstadoReserva.NO_ASISTIO, "no asistió").get(cmd.estado());
        reserva.cambiarEstado(cmd.estado(), ahora);
        reservas.saveAndFlush(reserva);
        auditoria.registrarCambio(reserva, usuarios.getReferenceById(actor.id()), accion,
                anterior, Map.of("estado", anterior.name()), Map.of("estado", cmd.estado().name()), null, false);
        String mensaje = "Reserva BT-" + id + ": estado actualizado a " + etiqueta
                + ", " + TiempoNegocio.aLima(reserva.getInicio()).format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE")))
                + " (hora de Lima).";
        if (reserva.getCliente().getUsuario() != null) {
            notificaciones.notificar(reserva.getCliente().getUsuario(), reserva, accion, mensaje);
        }
        var asignado = reserva.getBarbero().getUsuario();
        if (accion == AccionAuditoria.CONFIRMAR && asignado.getId() != actor.id()) {
            notificaciones.notificar(asignado, reserva, accion, mensaje);
        }
        return ReservaDto.desde(reserva, actor, clock.instant(), parametros);
    }

    private boolean validarReprogramacion(ReprogramarReservaDto cmd, UsuarioAutenticado actor,
            int version, EstadoReserva estado, Instant inicio, Instant ahora, ParametrosReserva parametros) {
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

    /**
     * Revalida una creación por ficha sin efectuar cambios de dominio, también para propuestas.
     * Toma ① y ② y refresca las entidades tras esperar; aplica RN-04/05/06/20.
     * @param fichaId ficha verificada del negocio
     * @param servicioId servicio elegido
     * @param barberoId profesional elegido
     * @param inicio inicio absoluto solicitado
     * @return entidad nueva sin persistir con las referencias validadas
     * @throws NegocioException si falta un recurso o incumple las reglas de autoservicio
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public Reserva prepararCreacionFicha(long fichaId, long servicioId, long barberoId, Instant inicio) {
        return prepararCreacionFicha(fichaId, servicioId, barberoId, inicio, null, null);
    }

    private Reserva prepararCreacionFicha(long fichaId, long servicioId, long barberoId, Instant inicio,
            Integer duracionEsperada, java.math.BigDecimal precioEsperado) {
        var parametros = reglasNegocio.reservas();
        var ficha = bloquearFicha(fichaId); // ①
        var perfiles = barberos.bloquearPorIds(List.of(barberoId)); // ②
        if (perfiles.isEmpty()) throw noEncontrado();
        var perfil = perfiles.getFirst();
        entidades.refresh(perfil);
        var servicio = servicios.findById(servicioId).orElseThrow(this::noEncontrado);
        entidades.refresh(servicio);
        if (duracionEsperada != null && (servicio.getDuracionMin() != duracionEsperada
                || servicio.getPrecio().compareTo(precioEsperado) != 0)) {
            throw new NegocioException(ErrorCodigo.VERSION_DESACTUALIZADA, "El servicio cambió. Solicite otro resumen.");
        }
        if (!servicio.isActivo() || !perfil.isActivo()) {
            throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO, "El servicio o barbero está inactivo.");
        }
        var fin = new ReglasTemporales().calcularFin(inicio, servicio.getDuracionMin());
        validarFranjaFicha(fichaId, barberoId, inicio, fin, 0);
        if (reservas.contarFuturasQueOcupan(fichaId, clock.instant()) >= parametros.maxActivasPorCliente()) {
            throw new NegocioException(ErrorCodigo.LIMITE_RESERVAS_ACTIVAS, "Ha alcanzado el límite de reservas futuras.");
        }
        return Reserva.porFicha(ficha, perfil, servicio, inicio,
                parametros.confirmacionManual() ? EstadoReserva.PENDIENTE : EstadoReserva.CONFIRMADA, clock.instant());
    }

    /**
     * Crea como la propia ficha; todas las validaciones preceden a la primera escritura.
     * @param fichaId actor y propietario verificado
     * @param servicioId servicio del resumen
     * @param barberoId profesional del resumen
     * @param inicio inicio confirmado
     * @param duracion duración del resumen para detectar cambios de catálogo
     * @param precio precio del resumen para detectar cambios de catálogo
     * @return reserva persistida con canal WHATSAPP y sin cuenta creadora
     * @throws NegocioException si cambió el catálogo o se incumple alguna regla, sin escrituras
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public Reserva crearFicha(long fichaId, long servicioId, long barberoId, Instant inicio,
            int duracion, java.math.BigDecimal precio) {
        var reserva = prepararCreacionFicha(fichaId, servicioId, barberoId, inicio, duracion, precio);
        reservas.saveAndFlush(reserva);
        auditoria.registrarCambioFicha(reserva, reserva.getCliente(), AccionAuditoria.CREAR, null, null, datosCancelacion(reserva));
        avisarFicha(reserva, AccionAuditoria.CREAR);
        return reserva;
    }

    /**
     * Revalida propiedad después de ③, versión y RN-07/09 sin cambiar la cita.
     * @param fichaId ficha actora
     * @param id cita solicitada
     * @param version versión que leyó el resumen
     * @param inicio nuevo inicio, o nulo al cancelar
     * @return cita bloqueada y validada
     * @throws NegocioException si la cita es ajena, cambió, está fuera de plazo o la franja no es válida
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public Reserva prepararCambioFicha(long fichaId, long id, int version, Instant inicio) {
        var parametros = reglasNegocio.reservas();
        var lectura = reservas.leerParaReprogramar(id).orElseThrow(this::noEncontrado);
        bloquearFicha(fichaId); // ①; nunca se bloquea una ficha ajena indicada por la cita
        var perfiles = barberos.bloquearPorIds(List.of(lectura.barberoId())); // ②
        perfiles.forEach(entidades::refresh);
        var reserva = reservas.bloquearPorId(id).orElseThrow(this::noEncontrado); // ③
        entidades.refresh(reserva);
        if (reserva.getCliente().getId() != fichaId) throw noEncontrado();
        if (reserva.getVersion() != version || reserva.getBarbero().getId() != lectura.barberoId()) {
            throw new NegocioException(ErrorCodigo.VERSION_DESACTUALIZADA, "La reserva cambió. Solicite otro resumen.");
        }
        if (inicio != null ? reserva.getEstado() != EstadoReserva.CONFIRMADA
                : reserva.getEstado() != EstadoReserva.CONFIRMADA && reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new NegocioException(ErrorCodigo.TRANSICION_INVALIDA, "El estado no permite esta acción.");
        }
        if (!new ReglasTemporales().puedeModificar(clock.instant(), reserva.getInicio(), false, null,
                parametros.anticipacionCambioCliente())) {
            throw new NegocioException(ErrorCodigo.FUERA_DE_POLITICA, "La cita está fuera del plazo de modificación.");
        }
        if (inicio != null) {
            validarFranjaFicha(fichaId, reserva.getBarbero().getId(), inicio,
                    new ReglasTemporales().calcularFin(inicio, reserva.getDuracionRefMin()), id);
        }
        return reserva;
    }

    /**
     * Mueve la cita conservando profesional, servicio, precio y duración del resumen.
     * @param fichaId ficha actora y propietaria
     * @param id cita confirmada
     * @param version versión del resumen
     * @param inicio nuevo inicio
     * @return cita reprogramada con versión incrementada
     * @throws NegocioException si alguna validación falla antes de escribir
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public Reserva reprogramarFicha(long fichaId, long id, int version, Instant inicio) {
        var reserva = prepararCambioFicha(fichaId, id, version, inicio);
        var antes = datosReprogramacion(reserva);
        reserva.reprogramar(reserva.getBarbero(), inicio, clock.instant());
        reservas.saveAndFlush(reserva);
        auditoria.registrarCambioFicha(reserva, reserva.getCliente(), AccionAuditoria.REPROGRAMAR,
                EstadoReserva.CONFIRMADA, antes, datosReprogramacion(reserva));
        avisarFicha(reserva, AccionAuditoria.REPROGRAMAR);
        return reserva;
    }

    /**
     * Cancela la cita propia sin borrar su historia ni cambiar el canal original.
     * @param fichaId ficha actora y propietaria
     * @param id cita pendiente o confirmada
     * @param version versión del resumen
     * @return cita cancelada con versión incrementada
     * @throws NegocioException si alguna validación falla antes de escribir
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public Reserva cancelarFicha(long fichaId, long id, int version) {
        var reserva = prepararCambioFicha(fichaId, id, version, null);
        var anterior = reserva.getEstado();
        var antes = datosCancelacion(reserva);
        reserva.cambiarEstado(EstadoReserva.CANCELADA, clock.instant());
        reservas.saveAndFlush(reserva);
        auditoria.registrarCambioFicha(reserva, reserva.getCliente(), AccionAuditoria.CANCELAR,
                anterior, antes, datosCancelacion(reserva));
        avisarFicha(reserva, AccionAuditoria.CANCELAR);
        return reserva;
    }

    private pe.barberturno.customers.Cliente bloquearFicha(long fichaId) {
        var ficha = clientes.bloquearPorId(fichaId).orElseThrow(this::noEncontrado);
        entidades.refresh(ficha);
        if (!ficha.isActivo()) throw noEncontrado();
        if (ficha.getUsuario() != null) {
            entidades.refresh(ficha.getUsuario());
            if (!ficha.getUsuario().isActivo()) {
                throw new NegocioException(ErrorCodigo.RECURSO_INACTIVO, "El cliente está inactivo.");
            }
        }
        return ficha;
    }

    private void validarFranjaFicha(long fichaId, long barberoId, Instant inicio, Instant fin, long excluirId) {
        disponibilidad.validarFranja(barberoId, inicio, fin, excluirId).ifPresent(codigo -> {
            throw new NegocioException(codigo, "La franja no cumple la disponibilidad.");
        });
        if (!reservas.buscarSolapamientosCliente(fichaId, inicio, fin, excluirId).isEmpty()) {
            throw new NegocioException(ErrorCodigo.CLIENTE_CON_RESERVA_SOLAPADA, "Ya tiene una cita en esa franja.");
        }
    }

    private void avisarFicha(Reserva reserva, AccionAuditoria accion) {
        String mensaje = "Reserva BT-" + reserva.getId() + ": " + accion.name() + ", "
                + TiempoNegocio.aLima(reserva.getInicio()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                + " (hora de Lima).";
        if (reserva.getCliente().getUsuario() != null) {
            notificaciones.notificar(reserva.getCliente().getUsuario(), reserva, accion, mensaje);
        }
        notificaciones.notificar(reserva.getBarbero().getUsuario(), reserva, accion, mensaje);
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
