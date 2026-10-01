package pe.barberturno;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.audit.*;
import pe.barberturno.catalog.*;
import pe.barberturno.notifications.*;
import pe.barberturno.reservations.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;

import static org.assertj.core.api.Assertions.*;
import static pe.barberturno.support.DatosPrueba.AHORA;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas")
class RepositoriosIT {

    @Autowired private UsuarioRepository usuarios;
    @Autowired private BarberoRepository barberos;
    @Autowired private ServicioRepository servicios;
    @Autowired private JornadaRepository jornadas;
    @Autowired private BloqueoRepository bloqueos;
    @Autowired private ReservaRepository reservas;
    @Autowired private AuditoriaRepository auditorias;
    @Autowired private NotificacionRepository notificaciones;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager gestorTransacciones;
    @Autowired private JdbcTemplate jdbc;

    private DatosPrueba datos;
    private TransactionTemplate transaccion;

    @BeforeEach
    void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        SqlPruebas.limpiar();
        datos = new DatosPrueba(usuarios, barberos, servicios);
        transaccion = new TransactionTemplate(gestorTransacciones);
    }

    @AfterEach
    void limpiar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        SqlPruebas.limpiar();
    }

    @Test
    void guardarYLeer_preservaTodosLosCamposYJsonb() {
        transaccion.executeWithoutResult(tx -> {
            Usuario cliente = datos.cliente("CLIENTE");
            cliente.cambiarPassword("otro-hash-ficticio", true, AHORA.plusSeconds(10));
            cliente.desactivar(AHORA.plusSeconds(20));
            cliente.registrarIntentoFallido(AHORA.plusSeconds(900), AHORA.plusSeconds(30));
            usuarios.saveAndFlush(cliente);
            Barbero barbero = datos.barbero("personal");
            barbero.desactivar(AHORA.plusSeconds(20));
            barberos.saveAndFlush(barbero);
            Servicio servicio = datos.servicio();
            servicio.desactivar(AHORA.plusSeconds(20));
            servicios.saveAndFlush(servicio);
            Jornada jornada = jornadas.saveAndFlush(new Jornada(barbero, (short) 1,
                    LocalTime.of(9, 0), LocalTime.of(13, 0)));
            Bloqueo bloqueo = bloqueos.saveAndFlush(new Bloqueo(barbero, AHORA.plusSeconds(7200),
                    AHORA.plusSeconds(9000), "Mantenimiento", barbero.getUsuario(), AHORA));
            Reserva reserva = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio,
                    AHORA.plusSeconds(3600), EstadoReserva.CONFIRMADA, barbero.getUsuario(), AHORA));
            Map<String, Object> anterior = Map.of("inicio", "2026-09-28T15:00:00Z",
                    "barberoId", barbero.getId(), "estado", "PENDIENTE");
            Map<String, Object> nuevo = Map.of("inicio", "2026-09-28T15:00:00Z",
                    "barberoId", barbero.getId(), "estado", "CONFIRMADA", "excepcional", true,
                    "detalle", Map.of("nota", "Dato ficticio"));
            AuditoriaReserva auditoria = auditorias.saveAndFlush(new AuditoriaReserva(reserva,
                    barbero.getUsuario(), AccionAuditoria.CONFIRMAR, EstadoReserva.PENDIENTE,
                    EstadoReserva.CONFIRMADA, anterior, nuevo, "Motivo ficticio", true, AHORA));
            Notificacion aviso = new Notificacion(cliente, reserva, AccionAuditoria.CONFIRMAR,
                    "Su turno ha sido confirmado.", AHORA);
            aviso.marcarLeida();
            notificaciones.saveAndFlush(aviso);
            entityManager.clear();

            Usuario leido = usuarios.findById(cliente.getId()).orElseThrow();
            assertThat(leido).extracting(Usuario::getId, Usuario::getNombre, Usuario::getCorreo,
                    Usuario::getTelefono, Usuario::getPasswordHash, Usuario::getRol, Usuario::isActivo,
                    Usuario::isDebeCambiarPassword, Usuario::getTokenVersion, Usuario::getIntentosFallidos,
                    Usuario::getBloqueadoHasta, Usuario::getPrivacidadAceptadaEn,
                    Usuario::getCreadoEn, Usuario::getActualizadoEn)
                    .containsExactly(cliente.getId(), "Cliente ficticio", "cliente@ejemplo.test", "999000001",
                            "otro-hash-ficticio", Rol.CLIENTE, false, true, 2, (short) 1,
                            AHORA.plusSeconds(900), AHORA, AHORA, AHORA.plusSeconds(30));
            Barbero b = barberos.findById(barbero.getId()).orElseThrow();
            assertThat(b).extracting(Barbero::getId, x -> x.getUsuario().getId(),
                    Barbero::getEspecialidad, Barbero::isActivo, Barbero::getCreadoEn, Barbero::getActualizadoEn)
                    .containsExactly(barbero.getId(), barbero.getUsuario().getId(), "Cortes",
                            false, AHORA, AHORA.plusSeconds(20));
            assertThat(b.getUsuario().getTelefono()).isNull();
            assertThat(b.getUsuario().getPrivacidadAceptadaEn()).isNull();
            assertThat(b.getUsuario().getBloqueadoHasta()).isNull();
            Servicio s = servicios.findById(servicio.getId()).orElseThrow();
            assertThat(s).extracting(Servicio::getId, Servicio::getNombre, Servicio::getDescripcion,
                    Servicio::getDuracionMin, Servicio::getPrecio, Servicio::isActivo,
                    Servicio::getCreadoEn, Servicio::getActualizadoEn)
                    .containsExactly(servicio.getId(), "Corte de prueba", "Descripción ficticia",
                            (short) 30, new BigDecimal("20.00"), false, AHORA, AHORA.plusSeconds(20));
            assertThat(jornadas.findById(jornada.getId()).orElseThrow())
                    .extracting(Jornada::getId, x -> x.getBarbero().getId(), Jornada::getDiaSemana,
                            Jornada::getHoraInicio, Jornada::getHoraFin)
                    .containsExactly(jornada.getId(), barbero.getId(), (short) 1,
                            LocalTime.of(9, 0), LocalTime.of(13, 0));
            assertThat(bloqueos.findById(bloqueo.getId()).orElseThrow())
                    .extracting(Bloqueo::getId, x -> x.getBarbero().getId(), Bloqueo::getInicio,
                            Bloqueo::getFin, Bloqueo::getMotivo, x -> x.getCreadoPor().getId(), Bloqueo::getCreadoEn)
                    .containsExactly(bloqueo.getId(), barbero.getId(), AHORA.plusSeconds(7200),
                            AHORA.plusSeconds(9000), "Mantenimiento", barbero.getUsuario().getId(), AHORA);
            assertThat(reservas.findById(reserva.getId()).orElseThrow())
                    .extracting(Reserva::getId, x -> x.getCliente().getId(), x -> x.getBarbero().getId(),
                            x -> x.getServicio().getId(), Reserva::getInicio, Reserva::getFin,
                            Reserva::getDuracionRefMin, Reserva::getPrecioRef, Reserva::getEstado,
                            x -> x.getCreadaPor().getId(), Reserva::getVersion, Reserva::getCreadoEn,
                            Reserva::getActualizadoEn)
                    .containsExactly(100L, cliente.getId(), barbero.getId(), servicio.getId(),
                            AHORA.plusSeconds(3600), AHORA.plusSeconds(5400), (short) 30,
                            new BigDecimal("20.00"), EstadoReserva.CONFIRMADA,
                            barbero.getUsuario().getId(), 0, AHORA, AHORA);
            AuditoriaReserva a = auditorias.findById(auditoria.getId()).orElseThrow();
            assertThat(a).extracting(AuditoriaReserva::getId, x -> x.getReserva().getId(),
                    x -> x.getActor().getId(), AuditoriaReserva::getAccion, AuditoriaReserva::getEstadoAnterior,
                    AuditoriaReserva::getEstadoNuevo, AuditoriaReserva::getMotivo,
                    AuditoriaReserva::isExcepcional, AuditoriaReserva::getCreadoEn)
                    .containsExactly(auditoria.getId(), reserva.getId(), barbero.getUsuario().getId(),
                            AccionAuditoria.CONFIRMAR, EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA,
                            "Motivo ficticio", true, AHORA);
            // JSON convierte un entero pequeño en Integer; se compara su valor numérico.
            assertThat(a.getDatosAnteriores()).hasSize(3).containsEntry("estado", "PENDIENTE")
                    .containsEntry("inicio", anterior.get("inicio"));
            assertThat(((Number) a.getDatosAnteriores().get("barberoId")).longValue()).isEqualTo(barbero.getId());
            assertThat(a.getDatosNuevos()).hasSize(5).containsEntry("estado", "CONFIRMADA")
                    .containsEntry("inicio", nuevo.get("inicio")).containsEntry("excepcional", true)
                    .containsEntry("detalle", Map.of("nota", "Dato ficticio"));
            assertThat(((Number) a.getDatosNuevos().get("barberoId")).longValue()).isEqualTo(barbero.getId());
            assertThat(jdbc.queryForObject("select jsonb_typeof(datos_nuevos) from auditoria_reserva where id = ?",
                    String.class, a.getId())).isEqualTo("object");
            assertThat(notificaciones.findById(aviso.getId()).orElseThrow())
                    .extracting(Notificacion::getId, x -> x.getUsuario().getId(), x -> x.getReserva().getId(),
                            Notificacion::getTipo, Notificacion::getMensaje, Notificacion::isLeida,
                            Notificacion::getCreadoEn)
                    .containsExactly(aviso.getId(), cliente.getId(), reserva.getId(), AccionAuditoria.CONFIRMAR,
                            "Su turno ha sido confirmado.", true, AHORA);
        });
    }

    @Test
    void auditoriaDeCreacion_conCamposOpcionalesNulos_persiste() {
        transaccion.executeWithoutResult(tx -> {
            Usuario cliente = datos.cliente("cliente");
            Reserva reserva = reservar(cliente, datos.barbero("barbero"), datos.servicio(), AHORA,
                    EstadoReserva.PENDIENTE);
            AuditoriaReserva auditoria = auditorias.saveAndFlush(new AuditoriaReserva(reserva, cliente,
                    AccionAuditoria.CREAR, null, EstadoReserva.PENDIENTE, null,
                    datosConNull(), null, false, AHORA));
            Notificacion aviso = notificaciones.saveAndFlush(new Notificacion(cliente, reserva,
                    AccionAuditoria.CREAR, "Turno creado.", AHORA));
            entityManager.clear();
            AuditoriaReserva leida = auditorias.findById(auditoria.getId()).orElseThrow();
            assertThat(leida.getEstadoAnterior()).isNull();
            assertThat(leida.getDatosAnteriores()).isNull();
            assertThat(leida.getMotivo()).isNull();
            assertThat(leida.getDatosNuevos()).containsEntry("motivo", null);
            assertThat(leida.isExcepcional()).isFalse();
            assertThat(notificaciones.findById(aviso.getId()).orElseThrow().isLeida()).isFalse();
        });
    }

    @ParameterizedTest
    @EnumSource(EstadoReserva.class)
    void buscarSolapamientos_respetaContiguidadCanceladasYExclusion(EstadoReserva estado) {
        transaccion.executeWithoutResult(tx -> {
            Usuario cliente = datos.cliente("cliente");
            Barbero barbero = datos.barbero("barbero");
            Barbero otro = datos.barbero("otro");
            Servicio servicio = datos.servicio();
            Reserva r = reservar(cliente, barbero, servicio, AHORA, estado);
            reservar(datos.cliente("otro-cliente"), otro, servicio, AHORA, EstadoReserva.CONFIRMADA);
            boolean ocupa = estado != EstadoReserva.CANCELADA;
            assertThat(reservas.buscarSolapamientos(barbero.getId(), AHORA.plusSeconds(1800),
                    AHORA.plusSeconds(3600), null)).isEmpty();
            assertThat(reservas.buscarSolapamientos(barbero.getId(), AHORA.minusSeconds(1800), AHORA, null)).isEmpty();
            List<Reserva> solapes = reservas.buscarSolapamientos(barbero.getId(),
                    AHORA.plusSeconds(600), AHORA.plusSeconds(2400), null);
            assertThat(solapes).hasSize(ocupa ? 1 : 0);
            if (ocupa) {
                assertThat(solapes).extracting(Reserva::getId).containsExactly(r.getId());
            }
            assertThat(reservas.buscarSolapamientos(barbero.getId(), AHORA, r.getFin(), r.getId())).isEmpty();
            assertThat(reservas.buscarSolapamientos(barbero.getId(), AHORA, r.getFin(), 0L)).hasSize(ocupa ? 1 : 0);
            // La consulta del cliente no depende del barbero que presta el servicio.
            assertThat(reservas.buscarSolapamientosCliente(cliente.getId(),
                    AHORA.plusSeconds(600), AHORA.plusSeconds(2400), null)).hasSize(ocupa ? 1 : 0);
            assertThat(reservas.buscarSolapamientosCliente(cliente.getId(),
                    AHORA.plusSeconds(1800), AHORA.plusSeconds(3600), null)).isEmpty();
            assertThat(reservas.buscarSolapamientosCliente(cliente.getId(), AHORA, r.getFin(), r.getId())).isEmpty();
        });
    }

    @Test
    void contarFuturasQueOcupan_excluyePasadasInicioExactoCanceladasYOtroCliente() {
        transaccion.executeWithoutResult(tx -> {
            Usuario cliente = datos.cliente("cliente");
            Barbero barbero = datos.barbero("barbero");
            Servicio servicio = datos.servicio();
            reservar(cliente, barbero, servicio, AHORA.minusSeconds(3600), EstadoReserva.CONFIRMADA);
            reservar(cliente, barbero, servicio, AHORA, EstadoReserva.CONFIRMADA);
            int indice = 1;
            for (EstadoReserva estado : EstadoReserva.values()) {
                reservar(cliente, barbero, servicio, AHORA.plusSeconds(indice++ * 3600L), estado);
            }
            reservar(datos.cliente("otro"), datos.barbero("otro"), servicio, AHORA.plusSeconds(3600),
                    EstadoReserva.CONFIRMADA);
            assertThat(reservas.contarFuturasQueOcupan(cliente.getId(), AHORA)).isEqualTo(5);
        });
    }

    @Test
    void buscarBloqueos_respetaIntervalosSemiabiertosYBarbero() {
        transaccion.executeWithoutResult(tx -> {
            Barbero barbero = datos.barbero("barbero");
            Barbero otro = datos.barbero("otro");
            Bloqueo bloqueo = bloqueos.saveAndFlush(new Bloqueo(barbero, AHORA,
                    AHORA.plusSeconds(1800), "Descanso", barbero.getUsuario(), AHORA));
            bloqueos.saveAndFlush(new Bloqueo(otro, AHORA, AHORA.plusSeconds(1800),
                    "Descanso", otro.getUsuario(), AHORA));
            assertThat(bloqueos.buscarQueSeCruzan(barbero.getId(), AHORA.plusSeconds(1800),
                    AHORA.plusSeconds(3600))).isEmpty();
            assertThat(bloqueos.buscarQueSeCruzan(barbero.getId(), AHORA.minusSeconds(1800), AHORA)).isEmpty();
            assertThat(bloqueos.buscarQueSeCruzan(barbero.getId(), AHORA.plusSeconds(600),
                    AHORA.plusSeconds(2400))).extracting(Bloqueo::getId).containsExactly(bloqueo.getId());
        });
    }

    @Test
    void reprogramar_incrementaVersionYConservaReferenciasAnteCambiosDelCatalogo() {
        transaccion.executeWithoutResult(tx -> {
            Usuario cliente = datos.cliente("cliente");
            Barbero barbero = datos.barbero("barbero");
            Servicio servicio = datos.servicio();
            Reserva reserva = reservar(cliente, barbero, servicio, AHORA, EstadoReserva.CONFIRMADA);
            servicio.editar("Corte modificado", "Otra descripción", (short) 60,
                    new BigDecimal("50.00"), AHORA.plusSeconds(10));
            servicios.saveAndFlush(servicio);
            reserva.reprogramar(datos.barbero("otro"), AHORA.plusSeconds(3600), AHORA.plusSeconds(20));
            reservas.saveAndFlush(reserva);
            assertThat(reserva.getVersion()).isEqualTo(1);
            reserva.cambiarEstado(EstadoReserva.CANCELADA, AHORA.plusSeconds(30));
            reservas.saveAndFlush(reserva);
            entityManager.clear();
            Reserva leida = reservas.findById(reserva.getId()).orElseThrow();
            assertThat(leida.getVersion()).isEqualTo(2);
            assertThat(leida.getDuracionRefMin()).isEqualTo((short) 30);
            assertThat(leida.getPrecioRef()).isEqualByComparingTo("20.00");
            assertThat(leida.getFin()).isEqualTo(AHORA.plusSeconds(5400));
            assertThat(leida.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(leida.getActualizadoEn()).isEqualTo(AHORA.plusSeconds(30));
            assertThat(leida.getServicio().getId()).isEqualTo(servicio.getId());
        });
    }

    @Test
    void limpiar_reiniciaReservaEnCienYConservaFlyway() {
        Usuario cliente = datos.cliente("cliente");
        Barbero barbero = datos.barbero("barbero");
        Servicio servicio = datos.servicio();
        assertThat(reservar(cliente, barbero, servicio, AHORA, EstadoReserva.CONFIRMADA).getId()).isEqualTo(100);
        assertThat(reservar(cliente, barbero, servicio, AHORA.plusSeconds(1800), EstadoReserva.CONFIRMADA)
                .getId()).isEqualTo(101);
        LimpiezaBaseDatos.limpiar(jdbc);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '1' and success",
                Integer.class)).isEqualTo(1);
        assertThat(reservar(datos.cliente("nuevo"), datos.barbero("nuevo"), datos.servicio(), AHORA,
                EstadoReserva.CONFIRMADA).getId()).isEqualTo(100);
    }

    @Test
    void bloquear_usaOrdenGlobalYSqlPesimistaEnLasTresEntidades() {
        Usuario cliente = datos.cliente("cliente");
        Barbero primero = datos.barbero("uno");
        Barbero segundo = datos.barbero("dos");
        Reserva reserva = reservar(cliente, primero, datos.servicio(), AHORA, EstadoReserva.CONFIRMADA);
        SqlPruebas.limpiar();
        transaccion.executeWithoutResult(tx -> {
            assertThat(usuarios.bloquearPorId(cliente.getId())).isPresent();
            assertThat(barberos.bloquearPorIds(List.of(segundo.getId(), primero.getId())))
                    .extracting(Barbero::getId).containsExactly(primero.getId(), segundo.getId());
            assertThat(reservas.bloquearPorId(reserva.getId())).isPresent();
        });
        List<String> sqlBloqueo = SqlPruebas.sentencias().stream()
                .filter(sql -> sql.contains("for no key update") || sql.contains("for update")).toList();
        assertThat(sqlBloqueo).hasSize(3);
        assertThat(sqlBloqueo.get(1)).contains("order by").contains(".id");
        sqlBloqueo.forEach(sql -> System.out.println("SQL de bloqueo verificado: " + sql));
    }

    @Test
    void bloquearPorIds_segundaTransaccionEsperaHastaElCommit() throws Exception {
        Barbero barbero = datos.barbero("barbero");
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        BlockingQueue<Integer> pidB = new ArrayBlockingQueue<>(1);
        BlockingQueue<String> xidA = new ArrayBlockingQueue<>(1);
        AtomicLong inicioB = new AtomicLong();
        AtomicLong antesCommitA = new AtomicLong();
        AtomicLong finB = new AtomicLong();
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Future<?> a = ejecutor.submit(() -> transaccion.executeWithoutResult(tx -> {
                jdbc.execute("SET LOCAL lock_timeout = '10s'");
                xidA.add(jdbc.queryForObject("select pg_current_xact_id()::text", String.class));
                barberos.bloquearPorIds(List.of(barbero.getId()));
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        antesCommitA.set(System.nanoTime());
                    }
                });
                bloqueado.countDown();
                esperar(liberar);
            }));
            Future<?> b = null;
            try {
                assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
                String xid = xidA.poll(10, TimeUnit.SECONDS);
                b = ejecutor.submit(() -> transaccion.executeWithoutResult(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    pidB.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    inicioB.set(System.nanoTime());
                    barberos.bloquearPorIds(List.of(barbero.getId()));
                    // Comprueba el commit en PostgreSQL, sin depender del orden de callbacks Java.
                    assertThat(jdbc.queryForObject("select pg_xact_status(cast(? as xid8))",
                            String.class, xid)).isEqualTo("committed");
                    finB.set(System.nanoTime());
                }));
                Integer pid = pidB.poll(10, TimeUnit.SECONDS);
                assertThat(pid).isNotNull();
                comprobarEsperaEnPostgres(pid);
                assertThat(b.isDone()).isFalse();
                liberar.countDown();
                a.get(15, TimeUnit.SECONDS);
                b.get(15, TimeUnit.SECONDS);
                assertThat(inicioB.get()).isLessThan(antesCommitA.get());
                assertThat(finB.get()).isGreaterThan(antesCommitA.get());
                System.out.println("Espera pesimista verificada: " +
                        Duration.ofNanos(finB.get() - inicioB.get()).toMillis() + " ms; A confirmada antes de B.");
            } finally {
                liberar.countDown();
                a.get(15, TimeUnit.SECONDS);
                if (b != null) {
                    b.get(15, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void bloquearPorIds_conTimeoutMientrasOtraTransaccionRetieneFila_fallaCon55P03() throws Exception {
        Barbero barbero = datos.barbero("barbero");
        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
            Future<?> a = ejecutor.submit(() -> transaccion.executeWithoutResult(tx -> {
                barberos.bloquearPorIds(List.of(barbero.getId()));
                bloqueado.countDown();
                esperar(liberar);
            }));
            try {
                assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
                Future<Throwable> b = ejecutor.submit(() -> catchThrowable(() ->
                        transaccion.executeWithoutResult(tx -> {
                            jdbc.execute("SET LOCAL lock_timeout = '300ms'");
                            barberos.bloquearPorIds(List.of(barbero.getId()));
                        })));
                Throwable error = b.get(10, TimeUnit.SECONDS);
                assertThat(error).isNotNull();
                SQLException postgres = causaSql(error);
                assertThat(postgres.getSQLState()).isEqualTo("55P03");
                assertThat(a.isDone()).isFalse();
                System.out.println("Timeout pesimista verificado: SQLState " + postgres.getSQLState());
            } finally {
                liberar.countDown();
                a.get(15, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void version_conEntidadDesactualizada_rechazaLaActualizacion() {
        Usuario cliente = datos.cliente("cliente");
        Reserva original = reservar(cliente, datos.barbero("barbero"), datos.servicio(), AHORA,
                EstadoReserva.CONFIRMADA);
        Reserva desactualizada = reservas.findById(original.getId()).orElseThrow();
        transaccion.executeWithoutResult(tx -> {
            Reserva actual = reservas.findById(original.getId()).orElseThrow();
            actual.cambiarEstado(EstadoReserva.EN_ATENCION, AHORA.plusSeconds(10));
            reservas.saveAndFlush(actual);
        });
        desactualizada.cambiarEstado(EstadoReserva.CANCELADA, AHORA.plusSeconds(20));
        assertThatThrownBy(() -> reservas.saveAndFlush(desactualizada))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(reservas.findById(original.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.EN_ATENCION);
    }

    private static Map<String, Object> datosConNull() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("estado", "PENDIENTE");
        datos.put("motivo", null);
        return datos;
    }

    private Reserva reservar(Usuario cliente, Barbero barbero, Servicio servicio, Instant inicio,
            EstadoReserva estado) {
        return reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, inicio, estado, cliente, AHORA));
    }

    private void comprobarEsperaEnPostgres(int pid) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from pg_stat_activity
                        where pid = ? and wait_event_type = 'Lock')
                    """, Boolean.class, pid))) {
                return;
            }
            // Solo regula el sondeo: la sincronización real son los latches y el estado del servidor.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("PostgreSQL no informó que la segunda transacción esperaba el bloqueo.");
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Se agotó la espera de coordinación.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Prueba interrumpida.", error);
        }
    }

    private static SQLException causaSql(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql;
            }
        }
        throw new AssertionError("Falta la causa SQL del timeout.", error);
    }
}
