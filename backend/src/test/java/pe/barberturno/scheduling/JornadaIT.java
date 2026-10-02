package pe.barberturno.scheduling;

import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.reservations.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
class JornadaIT {
    private static final String PASSWORD = "ClaveJornada123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @MockitoSpyBean BarberoRepository barberos;
    @Autowired JornadaRepository jornadas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReservaRepository reservas;
    @Autowired ServicioRepository servicios;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    @Autowired PlatformTransactionManager transacciones;
    private Cookie admin;
    private Barbero barbero;
    private String hash;
    private DatosPrueba datos;

    @BeforeEach void preparar() throws Exception {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
        hash = passwords.encode(PASSWORD);
        datos = new DatosPrueba(usuarios, barberos, servicios);
        admin = login(usuario("admin", Rol.ADMIN));
        barbero = guardar("profesional");
        jornadas.saveAndFlush(new Jornada(barbero, (short) 4, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @AfterEach void limpiar() { LimpiezaBaseDatos.limpiar(jdbc); SqlPruebas.limpiar(); }

    @Test void reemplazar_semanaEnteraOrdenada_contiguosYDiasDistintosPermitidos() throws Exception {
        Barbero otro = guardar("otro");
        jornadas.saveAndFlush(new Jornada(otro, (short) 4, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        var anteriorOtro = filas(otro.getId());
        var semana = List.of(intervalo(7, "10:00", "11:00"), intervalo(1, "10:00", "12:00"),
                intervalo(1, "09:00", "10:00"), intervalo(2, "09:00", "10:00"));
        escribir(barbero.getId(), admin, semana).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].diaSemana").value(1))
                .andExpect(jsonPath("$[0].horaInicio").value("09:00"))
                .andExpect(jsonPath("$[1].horaInicio").value("10:00"))
                .andExpect(jsonPath("$[2].diaSemana").value(2))
                .andExpect(jsonPath("$[3].diaSemana").value(7));
        assertThat(filas(otro.getId())).isEqualTo(anteriorOtro);
        mvc.perform(get(ruta(barbero.getId())).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        assertThat(jornadas.listarPorBarbero(barbero.getId())).noneMatch(j -> j.getDiaSemana() == 4);
        SqlPruebas.limpiar();
        escribir(barbero.getId(), admin, semana).andExpect(status().isOk());
        var sql = SqlPruebas.sentencias();
        int bloqueo = indice(sql, "for no key update");
        int lectura = indice(sql, "from reserva");
        int borrado = indice(sql, "delete from jornada");
        int insercion = indice(sql, "insert into jornada");
        assertThat(bloqueo).isGreaterThanOrEqualTo(0);
        assertThat(lectura).isGreaterThan(bloqueo);
        assertThat(borrado).isGreaterThan(lectura);
        assertThat(insercion).isGreaterThan(borrado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[{\"diaSemana\":4,\"horaInicio\":\"11:00\",\"horaFin\":\"10:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"10:00\",\"horaFin\":\"10:00\"}]",
        "[{\"diaSemana\":0,\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\"}]",
        "[{\"diaSemana\":8,\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\"}]",
        "[{\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\"}]",
        "[{\"diaSemana\":4,\"horaFin\":\"10:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"09:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"09:00:00\",\"horaFin\":\"10:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"09:00\",\"horaFin\":\"10:00:01\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"9:00\",\"horaFin\":\"10:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"24:00\",\"horaFin\":\"25:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"09:60\",\"horaFin\":\"10:00\"}]",
        "[null]",
        "[{\"diaSemana\":4,\"horaInicio\":\"09:00\",\"horaFin\":\"11:00\"},{\"diaSemana\":4,\"horaInicio\":\"10:00\",\"horaFin\":\"12:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"09:00\",\"horaFin\":\"12:00\"},{\"diaSemana\":4,\"horaInicio\":\"10:00\",\"horaFin\":\"11:00\"}]",
        "[{\"diaSemana\":4,\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\"},{\"diaSemana\":4,\"horaInicio\":\"09:00\",\"horaFin\":\"10:00\"}]"
    })
    void cp16_semanaInvalida_devuelve400ConIndiceYConservaFilas(String cuerpo) throws Exception {
        var anterior = filas(barbero.getId());
        Cookie token = csrf();
        mvc.perform(put(ruta(barbero.getId())).cookie(admin, token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JORNADA_INVALIDA"))
                .andExpect(jsonPath("$.errores[0].campo", org.hamcrest.Matchers.startsWith("[0]")));
        assertThat(filas(barbero.getId())).isEqualTo(anterior);
    }

    @Test void intervaloInvalidoEnSegundaPosicion_señalaIndiceOriginal() throws Exception {
        var antes = filas(barbero.getId());
        escribir(barbero.getId(), admin, List.of(intervalo(1, "09:00", "10:00"), intervalo(4, "12:00", "11:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("JORNADA_INVALIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("[1].horaFin"));
        assertThat(filas(barbero.getId())).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"inicio", "fin", "descanso", "dia", "vacia"})
    void cp07_bt101NoCabeEntera_conflictoConIdSinCambios(String caso) throws Exception {
        Usuario cliente = datos.cliente("cliente");
        var servicio = datos.servicio();
        reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, reloj.instant().minusSeconds(3600),
                EstadoReserva.COMPLETADA, cliente, reloj.instant()));
        Reserva bt101 = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio,
                Instant.parse("2026-10-01T15:00:00Z"), EstadoReserva.CONFIRMADA, cliente, reloj.instant()));
        assertThat(bt101.getId()).isEqualTo(101L);
        var semana = switch (caso) {
            case "inicio" -> List.of(intervalo(4, "10:10", "18:00"));
            case "fin" -> List.of(intervalo(4, "09:00", "10:20"));
            case "descanso" -> List.of(intervalo(4, "09:00", "10:10"), intervalo(4, "10:10", "18:00"));
            case "dia" -> List.of(intervalo(5, "09:00", "18:00"));
            default -> List.of();
        };
        var antes = filas(barbero.getId());
        var citas = jdbc.queryForList("select * from reserva order by id");
        escribir(barbero.getId(), admin, semana).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_CON_RESERVAS"))
                .andExpect(jsonPath("$.reservas[0]").value(101))
                .andExpect(jsonPath("$.reservas.length()").value(1));
        assertThat(filas(barbero.getId())).isEqualTo(antes);
        assertThat(jdbc.queryForList("select * from reserva order by id")).isEqualTo(citas);
    }

    @Test void reemplazar_reservaEnLimitesExactos_permiteSinModificarReserva() throws Exception {
        reservar("2026-10-01T15:00:00Z", EstadoReserva.CONFIRMADA);
        var antes = jdbc.queryForList("select * from reserva order by id");
        escribir(barbero.getId(), admin, List.of(intervalo(4, "10:00", "10:30")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForList("select * from reserva order by id")).isEqualTo(antes);
    }

    @ParameterizedTest @EnumSource(value = EstadoReserva.class, names = "CANCELADA", mode = EnumSource.Mode.EXCLUDE)
    void reemplazar_todoEstadoQueOcupaInclusoTerminalFuturo_bloquea(EstadoReserva estado) throws Exception {
        Reserva r = reservar("2026-10-01T15:00:00Z", estado);
        escribir(barbero.getId(), admin, List.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reservas[0]").value(r.getId().intValue()));
    }

    @Test void reemplazar_vaciaSinFuturas_eliminaSemanaYDevuelveListaVacia() throws Exception {
        escribir(barbero.getId(), admin, List.of()).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(filas(barbero.getId())).isEmpty();
        mvc.perform(get(ruta(barbero.getId())).cookie(admin)).andExpect(content().json("[]"));
    }

    @Test void reemplazar_pasadasCanceladasYAhora_noBloqueanYConservaReservas() throws Exception {
        reservar("2026-09-28T13:00:00Z", EstadoReserva.CONFIRMADA);
        reservar("2026-09-28T14:00:00Z", EstadoReserva.EN_ATENCION);
        reservar("2026-10-01T15:00:00Z", EstadoReserva.CANCELADA);
        var antes = jdbc.queryForList("select * from reserva order by id");
        escribir(barbero.getId(), admin, List.of()).andExpect(status().isOk());
        assertThat(jdbc.queryForList("select * from reserva order by id")).isEqualTo(antes);
    }

    @Test void reemplazar_diaLimaDifiereDeUtc_yCruceMedianocheSeRechaza() throws Exception {
        // UTC viernes, Lima jueves: 20:00–20:30.
        reservar("2026-10-02T01:00:00Z", EstadoReserva.CONFIRMADA);
        escribir(barbero.getId(), admin, List.of(intervalo(4, "20:00", "20:30"))).andExpect(status().isOk());
        Reserva cruce = reservar("2026-10-02T04:50:00Z", EstadoReserva.CONFIRMADA);
        var antes = filas(barbero.getId());
        escribir(barbero.getId(), admin, List.of(intervalo(4, "00:00", "23:59"), intervalo(5, "00:00", "01:00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reservas[0]").value(cruce.getId().intValue()));
        assertThat(filas(barbero.getId())).isEqualTo(antes);
    }

    @Test void reemplazar_variosConflictosOrdenados_noIncluyeOtroBarbero() throws Exception {
        Reserva primera = reservar("2026-10-01T15:00:00Z", EstadoReserva.CONFIRMADA);
        Reserva segunda = reservar("2026-10-02T15:00:00Z", EstadoReserva.CONFIRMADA);
        var otro = guardar("otro");
        var cliente = datos.cliente("otro-cliente");
        reservas.saveAndFlush(new Reserva(cliente, otro, servicios.findAll().getFirst(), primera.getInicio(),
                EstadoReserva.CONFIRMADA, cliente, reloj.instant()));
        escribir(barbero.getId(), admin, List.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.reservas.length()").value(2))
                .andExpect(jsonPath("$.reservas[0]").value(primera.getId().intValue()))
                .andExpect(jsonPath("$.reservas[1]").value(segunda.getId().intValue()));
    }

    @Test void barbero_leeSoloLaAsignada_yNoPuedeEscribir() throws Exception {
        Cookie sesion = login(usuarios.findById(barbero.getUsuario().getId()).orElseThrow());
        mvc.perform(get(ruta(barbero.getId())).cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].horaInicio").value("09:00"));
        Barbero otro = guardar("otro");
        for (long id : List.of(otro.getId(), 999L)) {
            mvc.perform(get(ruta(id)).cookie(sesion)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
        var antes = filas(barbero.getId());
        escribir(barbero.getId(), sesion, List.of()).andExpect(status().isForbidden());
        assertThat(filas(barbero.getId())).isEqualTo(antes);
    }

    @Test void barberoSinPerfil_noLeeJornadaAjena() throws Exception {
        mvc.perform(get(ruta(barbero.getId())).cookie(login(usuario("sin-perfil", Rol.BARBERO))))
                .andExpect(status().isNotFound());
    }

    @Test void adminPerfilInactivo_puedeConfigurarSinReactivar() throws Exception {
        barbero.desactivar(reloj.instant()); barberos.saveAndFlush(barbero);
        escribir(barbero.getId(), admin, List.of(intervalo(1, "09:15", "10:45"))).andExpect(status().isOk());
        mvc.perform(get(ruta(barbero.getId())).cookie(admin)).andExpect(status().isOk());
        assertThat(barberos.findById(barbero.getId()).orElseThrow().isActivo()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void admin_recursoInexistente_devuelve404(String metodo) throws Exception {
        if (metodo.equals("GET")) mvc.perform(get(ruta(999)).cookie(admin)).andExpect(status().isNotFound());
        else escribir(999, admin, List.of()).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void publico_sinSesion_devuelve401(String metodo) throws Exception {
        if (metodo.equals("GET")) mvc.perform(get(ruta(barbero.getId()))).andExpect(status().isUnauthorized());
        else escribir(barbero.getId(), null, List.of()).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void cliente_rolNoAutorizado_devuelve403(String metodo) throws Exception {
        Cookie sesion = login(usuario("cliente", Rol.CLIENTE));
        if (metodo.equals("GET")) mvc.perform(get(ruta(barbero.getId())).cookie(sesion)).andExpect(status().isForbidden());
        else escribir(barbero.getId(), sesion, List.of()).andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"ausente", "incorrecto"})
    void put_sesionValidaSinCsrfOCsrfIncorrecto_devuelve403SinCambios(String caso) throws Exception {
        var antes = filas(barbero.getId());
        var peticion = put(ruta(barbero.getId())).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("[]");
        if (caso.equals("incorrecto")) peticion.cookie(csrf()).header("X-XSRF-TOKEN", "incorrecto");
        mvc.perform(peticion).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(filas(barbero.getId())).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "PUT"})
    void passwordTemporal_noPermiteJornadas(String metodo) throws Exception {
        Usuario u = usuario("temporal", Rol.ADMIN);
        u.cambiarPassword(hash, true, reloj.instant()); usuarios.saveAndFlush(u);
        Cookie sesion = login(u);
        var resultado = metodo.equals("GET") ? mvc.perform(get(ruta(barbero.getId())).cookie(sesion))
                : escribir(barbero.getId(), sesion, List.of());
        resultado.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }

    @Test void dosPutConcurrentes_esperanBloqueoYUltimaSemanaQuedaCompleta() throws Exception {
        var primera = List.of(intervalo(1, "09:00", "12:00"), intervalo(3, "13:00", "16:00"));
        var ultima = List.of(intervalo(2, "10:00", "11:00"), intervalo(2, "12:00", "13:00"), intervalo(7, "15:00", "17:00"));
        CountDownLatch tomado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        BlockingQueue<Integer> pid = new ArrayBlockingQueue<>(1);
        var cuenta = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocacion -> {
            int turno = cuenta.incrementAndGet();
            if (turno == 2) pid.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            // Los repositorios Spring son interfaces: conservar el delegado real del spy.
            Object resultado = mockingDetails(barberos).getMockCreationSettings().getDefaultAnswer().answer(invocacion);
            if (turno == 1) { tomado.countDown(); esperar(liberar); }
            return resultado;
        }).when(barberos).bloquearPorIds(List.of(barbero.getId()));
        Cookie csrfA = csrf(), csrfB = csrf();
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> a = ejecutor.submit(() -> putConToken(primera, csrfA));
            Future<MvcResult> b = null;
            try {
                assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
                b = ejecutor.submit(() -> putConToken(ultima, csrfB));
                comprobarEspera(pid.poll(10, TimeUnit.SECONDS));
                assertThat(b.isDone()).isFalse();
            } finally { liberar.countDown(); }
            assertThat(a.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(b.get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        }
        mvc.perform(get(ruta(barbero.getId())).cookie(admin)).andExpect(content().json(json.writeValueAsString(ultima)));
        assertThat(filas(barbero.getId())).hasSize(3);
    }

    @Test void put_leeReservasDespuesDelBloqueo_veReservaRecienConfirmada() throws Exception {
        Usuario cliente = datos.cliente("cliente");
        var servicio = datos.servicio();
        CountDownLatch insertada = new CountDownLatch(1), liberar = new CountDownLatch(1);
        BlockingQueue<Integer> pid = new ArrayBlockingQueue<>(1);
        var idReserva = new java.util.concurrent.atomic.AtomicLong();
        doAnswer(invocacion -> {
            if (Thread.currentThread().getName().equals("put-jornada"))
                pid.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            return mockingDetails(barberos).getMockCreationSettings().getDefaultAnswer().answer(invocacion);
        }).when(barberos).bloquearPorIds(List.of(barbero.getId()));
        Cookie token = csrf();
        var antes = filas(barbero.getId());
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Future<?> reserva = ejecutor.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                barberos.bloquearPorIds(List.of(barbero.getId()));
                idReserva.set(reservas.saveAndFlush(new Reserva(cliente, barbero, servicio,
                        Instant.parse("2026-10-01T15:00:00Z"), EstadoReserva.CONFIRMADA, cliente, reloj.instant())).getId());
                insertada.countDown(); esperar(liberar);
            }));
            Future<MvcResult> cambio = null;
            try {
                assertThat(insertada.await(10, TimeUnit.SECONDS)).isTrue();
                cambio = ejecutor.submit(() -> {
                    Thread.currentThread().setName("put-jornada");
                    return putConToken(List.of(), token);
                });
                comprobarEspera(pid.poll(10, TimeUnit.SECONDS));
            } finally { liberar.countDown(); }
            reserva.get(20, TimeUnit.SECONDS);
            var respuesta = cambio.get(20, TimeUnit.SECONDS).getResponse();
            assertThat(respuesta.getStatus()).isEqualTo(409);
            assertThat(json.readTree(respuesta.getContentAsString()).get("reservas").get(0).longValue()).isEqualTo(idReserva.get());
        }
        assertThat(filas(barbero.getId())).isEqualTo(antes);
    }

    private void comprobarEspera(Integer pid) throws Exception {
        assertThat(pid).isNotNull();
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL no registró la espera por el bloqueo ②.");
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó la transacción.");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private MvcResult putConToken(Object semana, Cookie token) throws Exception {
        return mvc.perform(put(ruta(barbero.getId())).cookie(admin, token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(semana))).andReturn();
    }

    private int indice(List<String> sql, String fragmento) {
        for (int i = 0; i < sql.size(); i++) if (sql.get(i).contains(fragmento)) return i;
        return -1;
    }

    private Reserva reservar(String inicio, EstadoReserva estado) {
        Usuario cliente = usuarios.findByCorreo("reserva@ejemplo.test").orElseGet(() -> datos.cliente("reserva"));
        var servicio = servicios.findAll().stream().findFirst().orElseGet(datos::servicio);
        return reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, Instant.parse(inicio), estado, cliente, reloj.instant()));
    }

    private Barbero guardar(String nombre) { return barberos.saveAndFlush(new Barbero(usuario(nombre, Rol.BARBERO), "Cortes", reloj.instant())); }
    private Usuario usuario(String nombre, Rol rol) {
        return usuarios.saveAndFlush(new Usuario("Nombre ficticio", nombre + "@ejemplo.test", "999000001", hash,
                rol, rol == Rol.CLIENTE ? reloj.instant() : null, reloj.instant()));
    }
    private Map<String, Object> intervalo(int dia, String inicio, String fin) { return Map.of("diaSemana", dia, "horaInicio", inicio, "horaFin", fin); }
    private List<?> filas(long id) { return jdbc.queryForList("select * from jornada where barbero_id = ? order by id", id); }
    private String ruta(long id) { return "/api/barberos/" + id + "/jornadas"; }
    private Cookie csrf() throws Exception { return mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN"); }
    private ResultActions escribir(long id, Cookie sesion, Object semana) throws Exception {
        Cookie token = csrf();
        var peticion = put(ruta(id)).cookie(token).header("X-XSRF-TOKEN", token.getValue());
        if (sesion != null) peticion.cookie(sesion);
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(semana)));
    }
    private Cookie login(Usuario usuario) throws Exception {
        Cookie token = csrf();
        return mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo", usuario.getCorreo(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("BT_SESION");
    }
}
