package pe.barberturno.reservations;

import jakarta.servlet.http.Cookie;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.scheduling.*;
import pe.barberturno.scheduling.dto.CrearBloqueoDto;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrenciaReservaIT extends ReservaPruebaBase {
    @Autowired BloqueoService servicioBloqueos;
    @Autowired PlatformTransactionManager transacciones;
    private static long iniciado;

    @BeforeAll static void cronometrar() { iniciado = System.nanoTime(); }
    @AfterAll static void informarDuracion() {
        System.out.printf(Locale.ROOT, "ConcurrenciaReservaIT: %.3f s; repeticiones CP-03=%d%n",
                (System.nanoTime() - iniciado) / 1e9, Integer.getInteger("barberturno.concurrencia.repeticiones", 20));
    }

    static IntStream repeticiones() {
        int repeticiones = Integer.getInteger("barberturno.concurrencia.repeticiones", 20);
        if (repeticiones < 1) throw new IllegalArgumentException("Las repeticiones deben ser positivas.");
        return IntStream.rangeClosed(1, repeticiones);
    }

    @ParameterizedTest @MethodSource("repeticiones")
    void cp03_diezClientesMismaFranja_unExitoYNueveConflictos(int repeticion) throws Exception {
        List<Callable<String>> tareas = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var clienteHilo = datos.cliente("hilo-" + i);
            tareas.add(() -> crear(clienteHilo, barbero, "10:00"));
        }
        assertThat(carrera(tareas)).as("CP-03 repetición %s", repeticion)
                .containsExactlyInAnyOrder("OK", "FRANJA_NO_DISPONIBLE", "FRANJA_NO_DISPONIBLE",
                        "FRANJA_NO_DISPONIBLE", "FRANJA_NO_DISPONIBLE", "FRANJA_NO_DISPONIBLE",
                        "FRANJA_NO_DISPONIBLE", "FRANJA_NO_DISPONIBLE", "FRANJA_NO_DISPONIBLE", "FRANJA_NO_DISPONIBLE");
        comprobarUnicaReserva();
    }

    @Test void solapeParcial_10Y1010_unExito() throws Exception {
        var otro = datos.cliente("otro");
        assertThat(carrera(List.of(() -> crear(cliente, barbero, "10:00"),
                () -> crear(otro, barbero, "10:10")))).containsExactlyInAnyOrder("OK", "FRANJA_NO_DISPONIBLE");
        comprobarUnicaReserva();
    }

    @Test void mismoClienteDosBarberos_serializaRn04() throws Exception {
        var segundo = perfil("dos");
        assertThat(carrera(List.of(() -> crear(cliente, barbero, "10:00"),
                () -> crear(cliente, segundo, "10:00")))).containsExactlyInAnyOrder("OK", "CLIENTE_CON_RESERVA_SOLAPADA");
        comprobarUnicaReserva();
    }

    @Test void mismoClienteConDosFuturas_carreraPorElUltimoCupo() throws Exception {
        servicioReservas.crear(cmd(barbero, "11:00"), actor(cliente));
        servicioReservas.crear(cmd(barbero, "12:00"), actor(cliente));
        var segundo = perfil("dos");
        assertThat(carrera(List.of(() -> crear(cliente, barbero, "10:00"),
                () -> crear(cliente, segundo, "13:00")))).containsExactlyInAnyOrder("OK", "LIMITE_RESERVAS_ACTIVAS");
        assertThat(cantidad("reserva")).isEqualTo(3);
        assertThat(cantidad("auditoria_reserva")).isEqualTo(3);
        assertThat(cantidad("notificacion")).isEqualTo(6);
    }

    @Test void reservaFrenteABloqueo_salidaSimultanea_unSoloExito() throws Exception {
        var admin = administrador();
        var resultados = carrera(List.of(() -> crear(cliente, barbero, "10:00"), () -> bloquear(admin)));
        assertThat(resultados).contains("OK");
        assertThat(resultados.stream().filter("OK"::equals)).hasSize(1);
        assertThat(resultados).anyMatch(s -> s.equals("FRANJA_NO_DISPONIBLE") || s.equals("CONFLICTO_CON_RESERVAS"));
        comprobarSinCruces();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void reservaFrenteABloqueo_ambosOrdenesConEsperaReal_veElCommit(boolean reservaPrimero) throws Exception {
        var admin = administrador();
        CountDownLatch insertada = new CountDownLatch(1), liberar = new CountDownLatch(1);
        BlockingQueue<Integer> pid = new ArrayBlockingQueue<>(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primero = ejecutor.submit(() -> new TransactionTemplate(transacciones).execute(tx -> {
                // El propietario respeta ① ② incluso cuando retiene su transacción para observar la espera.
                String resultado = reservaPrimero ? crear(cliente, barbero, "10:00") : bloquear(admin);
                insertada.countDown();
                esperar(liberar);
                return resultado;
            }));
            Future<String> segundo = null;
            try {
                assertThat(insertada.await(10, TimeUnit.SECONDS)).isTrue();
                segundo = ejecutor.submit(() -> new TransactionTemplate(transacciones).execute(tx -> {
                    pid.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    String resultado = reservaPrimero ? bloquear(admin) : crear(cliente, barbero, "10:00");
                    if (!resultado.equals("OK")) tx.setRollbackOnly();
                    return resultado;
                }));
                comprobarEspera(pid.poll(10, TimeUnit.SECONDS));
                assertThat(segundo.isDone()).isFalse();
            } finally { liberar.countDown(); }
            assertThat(primero.get(15, TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(segundo.get(15, TimeUnit.SECONDS)).isEqualTo(
                    reservaPrimero ? "CONFLICTO_CON_RESERVAS" : "FRANJA_NO_DISPONIBLE");
        }
        comprobarSinCruces();
        assertThat(cantidad("reserva")).isEqualTo(reservaPrimero ? 1 : 0);
        assertThat(cantidad("bloqueo")).isEqualTo(reservaPrimero ? 0 : 1);
    }

    @ParameterizedTest @ValueSource(strings = {"barbero", "servicio", "reloj"})
    void revalidaDespuesDeEsperar_estadoYTiempoVigentes(String recurso) throws Exception {
        CountDownLatch tomado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        BlockingQueue<Integer> pid = new ArrayBlockingQueue<>(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var propietario = ejecutor.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                barberos.bloquearPorIds(List.of(barbero.getId()));
                tomado.countDown();
                esperar(liberar);
                switch (recurso) {
                    case "barbero" -> jdbc.update("update barbero set activo=false where id=?", barbero.getId());
                    case "servicio" -> jdbc.update("update servicio set activo=false where id=?", servicio.getId());
                    default -> reloj.adelantar(Duration.between(reloj.instant(), instante("10:00")));
                }
            }));
            Future<String> reserva = null;
            try {
                assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
                reserva = ejecutor.submit(() -> new TransactionTemplate(transacciones).execute(tx -> {
                    pid.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    String resultado = crear(cliente, barbero, "10:00");
                    if (!resultado.equals("OK")) tx.setRollbackOnly();
                    return resultado;
                }));
                comprobarEspera(pid.poll(10, TimeUnit.SECONDS));
            } finally { liberar.countDown(); }
            propietario.get(15, TimeUnit.SECONDS);
            assertThat(reserva.get(15, TimeUnit.SECONDS))
                    .isEqualTo(recurso.equals("reloj") ? "INICIO_EN_PASADO" : "RECURSO_INACTIVO");
        }
        assertThat(cantidad("reserva")).isZero();
        assertThat(cantidad("auditoria_reserva")).isZero();
        assertThat(cantidad("notificacion")).isZero();
    }

    @Test void cincoPeticionesHttp_un201YCuatro409() throws Exception {
        List<Callable<String>> peticiones = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            var usuario = datos.cliente("http-" + i);
            var peticion = conCsrf(peticion(cmd(barbero, "10:00")), sesion(usuario));
            peticiones.add(() -> {
                var respuesta = mvc.perform(peticion).andReturn().getResponse();
                if (respuesta.getStatus() == 201) return "201";
                assertThat(respuesta.getStatus()).isEqualTo(409);
                assertThat(json.readTree(respuesta.getContentAsString()).get("codigo").asString()).isEqualTo("FRANJA_NO_DISPONIBLE");
                return "409";
            });
        }
        assertThat(carrera(peticiones)).containsExactlyInAnyOrder("201", "409", "409", "409", "409");
        comprobarUnicaReserva();
    }

    @Test void jdbcOmiteServicio_exclusionBarbero23P01() {
        servicioReservas.crear(cmd(barbero, "10:00"), actor(cliente));
        var otro = datos.cliente("jdbc");
        assertThatThrownBy(() -> jdbc.update("""
                insert into reserva (cliente_id,barbero_id,servicio_id,inicio,fin,duracion_ref_min,precio_ref,
                    estado,creada_por,version,creado_en,actualizado_en)
                values (?,?,?,?,?,30,20.00,'CONFIRMADA',?,0,?,?)
                """, otro.getId(), barbero.getId(), servicio.getId(),
                TiempoNegocio.aLima(instante("10:10")), TiempoNegocio.aLima(instante("10:40")),
                otro.getId(), TiempoNegocio.aLima(reloj.instant()), TiempoNegocio.aLima(reloj.instant())))
                .isInstanceOfSatisfying(DataAccessException.class, e -> {
                    var causa = (org.postgresql.util.PSQLException) e.getMostSpecificCause();
                    assertThat(causa.getSQLState()).isEqualTo("23P01");
                    assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo("reserva_sin_solape_barbero");
                });
        comprobarUnicaReserva();
    }

    private String crear(Usuario usuario, Barbero perfil, String hora) {
        try {
            servicioReservas.crear(cmd(perfil, hora), actor(usuario));
            return "OK";
        } catch (NegocioException e) { return e.codigo().name(); }
    }

    private String bloquear(Usuario admin) {
        try {
            servicioBloqueos.crear(barbero.getId(), new CrearBloqueoDto(
                    TiempoNegocio.aLima(instante("10:10")), TiempoNegocio.aLima(instante("10:20")),
                    "Bloqueo ficticio"), actor(admin));
            return "OK";
        } catch (NegocioException e) { return e.codigo().name(); }
    }

    private Usuario administrador() {
        return usuarios.saveAndFlush(new Usuario("Administrador ficticio", "admin@ejemplo.test",
                null, "hash-ficticio", Rol.ADMIN, null, reloj.instant()));
    }

    private List<String> carrera(List<Callable<String>> tareas) throws Exception {
        CountDownLatch preparados = new CountDownLatch(tareas.size()), salida = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(tareas.size())) {
            List<Future<String>> futuros = new ArrayList<>();
            for (var tarea : tareas) futuros.add(ejecutor.submit(() -> {
                preparados.countDown();
                esperar(salida);
                return tarea.call();
            }));
            try { assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { salida.countDown(); }
            List<String> resultados = new ArrayList<>();
            for (var futuro : futuros) {
                try { resultados.add(futuro.get(20, TimeUnit.SECONDS)); }
                catch (ExecutionException e) {
                    for (Throwable causa = e; causa != null; causa = causa.getCause()) {
                        if (causa instanceof SQLException sql) {
                            assertThat(sql.getSQLState()).isNotIn("40P01", "55P03");
                        }
                    }
                    throw e;
                }
            }
            assertThat(resultados).doesNotContain("RECURSO_OCUPADO");
            return resultados;
        }
    }

    private void comprobarUnicaReserva() {
        assertThat(cantidad("reserva")).isOne();
        assertThat(cantidad("auditoria_reserva")).isOne();
        assertThat(cantidad("notificacion")).isEqualTo(2);
        comprobarSinCruces();
    }

    private void comprobarSinCruces() {
        assertThat(cantidad("reserva") + cantidad("bloqueo")).isGreaterThan(0);
        assertThat(jdbc.queryForObject("""
                select count(*) from reserva r join bloqueo b on b.barbero_id=r.barbero_id
                where r.estado <> 'CANCELADA' and r.inicio < b.fin and r.fin > b.inicio
                """, Integer.class)).isZero();
    }

    private void comprobarEspera(Integer pid) throws Exception {
        assertThat(pid).isNotNull();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL no registró la espera por ②.");
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó la transacción de prueba.");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
