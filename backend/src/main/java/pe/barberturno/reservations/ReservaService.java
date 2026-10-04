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
 * Creación RF-08 atómica con bloqueos ① ②, revalidación, exclusiones GiST y trazabilidad RN-15.
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

    /**
     * Recibe colaboradores de la única transacción de creación.
     * @param reservas persistencia y consultas RN-04/20
     * @param usuarios bloqueo ① del cliente
     * @param barberos bloqueo ② de agenda
     * @param servicios catálogo y referencias RN-13
     * @param disponibilidad validador común RN-05
     * @param auditoria escritura del historial RN-15
     * @param notificaciones escritura de avisos RN-15
     * @param clock reloj inyectado del servidor
     * @param parametros límite y confirmación RN-20/21
     */
    public ReservaService(ReservaRepository reservas, UsuarioRepository usuarios, BarberoRepository barberos,
            ServicioRepository servicios, DisponibilidadService disponibilidad, AuditoriaService auditoria,
            NotificacionService notificaciones, Clock clock, ParametrosReserva parametros) {
        this.reservas = reservas;
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.servicios = servicios;
        this.disponibilidad = disponibilidad;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.clock = clock;
        this.parametros = parametros;
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

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el recurso solicitado.");
    }
}
