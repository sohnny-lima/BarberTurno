package pe.barberturno.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.audit.*;
import pe.barberturno.auth.PoliticaPassword;
import pe.barberturno.catalog.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.reservations.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.users.*;

/**
 * Importa las instantáneas académicas sin modificar reglas, catálogo ajeno ni identidades existentes.
 * La transacción, el marcador y los bloqueos impiden cargas parciales o duplicadas.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Profile("demo & !prod")
public class DatosDemoService {
    private final UsuarioRepository usuarios;
    private final BarberoRepository barberos;
    private final ServicioRepository servicios;
    private final JornadaRepository jornadas;
    private final BloqueoRepository bloqueos;
    private final ReservaRepository reservas;
    private final AuditoriaService auditoria;
    private final NotificacionService avisos;
    private final PasswordEncoder encoder;
    private final Clock clock;

    /**
     * Recibe los repositorios del escenario, los servicios RN-15 y la seguridad del entorno.
     * @param usuarios persistencia de identidades y marcador cliente@ejemplo.test
     * @param barberos persistencia y bloqueo global de cupo RN-19
     * @param servicios catálogo con instantáneas de duración y precio
     * @param jornadas intervalos semanales de atención
     * @param bloqueos excepciones de disponibilidad
     * @param reservas instantáneas históricas del prototipo
     * @param auditoria registro transaccional de cada importación
     * @param avisos avisos internos transaccionales RN-15
     * @param encoder BCrypt configurado por la aplicación
     * @param clock reloj inyectado para marcas temporales
     */
    public DatosDemoService(UsuarioRepository usuarios, BarberoRepository barberos,
            ServicioRepository servicios, JornadaRepository jornadas, BloqueoRepository bloqueos,
            ReservaRepository reservas, AuditoriaService auditoria, NotificacionService avisos,
            PasswordEncoder encoder, Clock clock) {
        this.usuarios = usuarios;
        this.barberos = barberos;
        this.servicios = servicios;
        this.jornadas = jornadas;
        this.bloqueos = bloqueos;
        this.reservas = reservas;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.encoder = encoder;
        this.clock = clock;
    }

    /**
     * Importa BT-100…BT-104 y K1 una vez; omite si ya existe el primer cliente demo.
     * Usa el bloqueo global de altas y luego clientes ① y barberos ② antes de disponibilidad.
     * Las fechas originales se conservan incluso históricas: son datos iniciales, no reservas nuevas.
     * @param password contraseña externa válida según RN-25 para todas las cuentas demo
     * @throws IllegalStateException si la clave es inválida, falta cupo o el catálogo existente difiere
     * @throws org.springframework.dao.DataIntegrityViolationException si una identidad demo ya está ocupada
     */
    @Transactional
    public void cargar(String password) {
        barberos.bloquearLimiteActivos();
        if (usuarios.findByCorreo("cliente@ejemplo.test").isPresent()) return;
        if (!new PoliticaPassword().validar(password).isEmpty()) {
            throw new IllegalStateException("BT_DEMO_PASSWORD debe cumplir RN-25.");
        }
        if (barberos.countByActivoTrue() + 2 > 10) {
            throw new IllegalStateException("La demo necesita cupo para dos barberos activos.");
        }
        String hash = encoder.encode(password);
        Usuario cliente = usuario("Cliente de demostración", "cliente", Rol.CLIENTE, hash);
        Usuario ana = usuario("Ana", "ana", Rol.CLIENTE, hash);
        Usuario luis = usuario("Luis", "luis", Rol.CLIENTE, hash);
        Usuario carlos = usuario("Carlos", "carlos", Rol.BARBERO, hash);
        Usuario miguel = usuario("Miguel", "miguel", Rol.BARBERO, hash);
        Usuario admin = usuarios.findAll().stream().filter(u -> u.getRol() == Rol.ADMIN && u.isActivo())
                .findFirst().orElseGet(() -> usuario("Administrador de demostración", "admin-demo", Rol.ADMIN, hash));
        Barbero b1 = barberos.saveAndFlush(new Barbero(carlos, "Corte y barba", clock.instant()));
        Barbero b2 = barberos.saveAndFlush(new Barbero(miguel, "Corte y barba", clock.instant()));
        Servicio corte = catalogo("Corte clásico", "Corte y acabado", (short) 30, "25.00");
        Servicio barba = catalogo("Barba", "Perfilado de barba", (short) 20, "20.00");
        for (Usuario u : List.of(cliente, ana, luis)) usuarios.bloquearPorId(u.getId()).orElseThrow();
        barberos.bloquearPorIds(List.of(b1.getId(), b2.getId()));
        for (Barbero b : List.of(b1, b2)) {
            for (short dia = 1; dia <= 6; dia++) {
                jornadas.save(new Jornada(b, dia, LocalTime.of(9, 0), LocalTime.of(13, 0)));
                jornadas.save(new Jornada(b, dia, LocalTime.of(14, 0), LocalTime.of(18, 0)));
            }
        }
        bloqueos.save(new Bloqueo(b1, instante("2026-10-01", "16:00"),
                instante("2026-10-01", "17:00"), "Trámite", admin, clock.instant()));
        importar(cliente, b1, corte, "2026-10-01", "10:00", EstadoReserva.CONFIRMADA, "BT-101");
        importar(luis, b1, barba, "2026-09-28", "09:30", EstadoReserva.CONFIRMADA, "BT-102");
        importar(cliente, b2, corte, "2026-09-27", "10:00", EstadoReserva.COMPLETADA, "BT-103");
        importar(cliente, b1, barba, "2026-09-28", "10:00", EstadoReserva.CONFIRMADA, "BT-104");
        importar(luis, b1, corte, "2026-09-29", "11:00", EstadoReserva.PENDIENTE, "BT-100");
    }

    private Usuario usuario(String nombre, String correo, Rol rol, String hash) {
        Instant ahora = clock.instant();
        return usuarios.saveAndFlush(new Usuario(nombre, correo + "@ejemplo.test",
                rol == Rol.CLIENTE ? "999000000" : null, hash, rol,
                rol == Rol.CLIENTE ? ahora : null, ahora));
    }

    private Servicio catalogo(String nombre, String descripcion, short duracion, String precio) {
        Servicio existente = servicios.buscarPorNombre(nombre).orElse(null);
        if (existente == null) {
            return servicios.saveAndFlush(new Servicio(nombre, descripcion, duracion,
                    new BigDecimal(precio), clock.instant()));
        }
        if (!existente.isActivo() || existente.getDuracionMin() != duracion
                || existente.getPrecio().compareTo(new BigDecimal(precio)) != 0
                || !existente.getDescripcion().equals(descripcion)) {
            throw new IllegalStateException("El catálogo existente es incompatible con la demo.");
        }
        return existente;
    }

    private void importar(Usuario cliente, Barbero barbero, Servicio servicio, String fecha, String hora,
            EstadoReserva estado, String referencia) {
        Reserva reserva = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio,
                instante(fecha, hora), estado, cliente, clock.instant()));
        auditoria.registrarCambio(reserva, cliente, AccionAuditoria.CREAR, null, null,
                Map.of("inicio", TiempoNegocio.aLima(reserva.getInicio()).toString(),
                        "fin", TiempoNegocio.aLima(reserva.getFin()).toString(),
                        "barberoId", barbero.getId(), "servicioId", servicio.getId(),
                        "estado", estado.name(), "referenciaDemo", referencia),
                "Importación de instantánea del prototipo APF2", false);
        String mensaje = "Reserva BT-" + reserva.getId() + " importada para la demostración (" + referencia + ").";
        avisos.notificar(cliente, reserva, AccionAuditoria.CREAR, mensaje);
        avisos.notificar(barbero.getUsuario(), reserva, AccionAuditoria.CREAR, mensaje);
    }

    private static Instant instante(String fecha, String hora) {
        return OffsetDateTime.parse(fecha + "T" + hora + ":00-05:00").toInstant();
    }
}
