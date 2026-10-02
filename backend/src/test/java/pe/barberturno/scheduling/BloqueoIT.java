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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.reservations.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
class BloqueoIT {
    private static final String PASSWORD = "ClaveBloqueo123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @MockitoSpyBean BarberoRepository barberos;
    @Autowired BloqueoRepository bloqueos;
    @Autowired JornadaRepository jornadas;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReservaRepository reservas;
    @Autowired ServicioRepository servicios;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    @Autowired PlatformTransactionManager transacciones;
    private Cookie admin;
    private Usuario actor;
    private Barbero barbero;
    private String hash;
    private DatosPrueba datos;

    @BeforeEach void preparar() throws Exception {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
        hash = passwords.encode(PASSWORD);
        datos = new DatosPrueba(usuarios, barberos, servicios);
        actor = usuario("admin", Rol.ADMIN);
        admin = login(actor);
        barbero = guardar("profesional");
        jornadas.saveAndFlush(new Jornada(barbero, (short) 4, LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @AfterEach void limpiar() { LimpiezaBaseDatos.limpiar(jdbc); SqlPruebas.limpiar(); }

    @Test void cp07_bt101CruzaBloqueo_conflictoConId_yFranjaLibreCrea() throws Exception {
        reservar(barbero, "2026-09-28T13:00:00Z", EstadoReserva.COMPLETADA);
        Reserva bt101 = reservar(barbero, "2026-10-01T15:00:00Z", EstadoReserva.CONFIRMADA);
        assertThat(bt101.getId()).isEqualTo(101L);
        var citas = jdbc.queryForList("select * from reserva order by id");
        crear("2026-10-01T10:10:00-05:00", "2026-10-01T10:20:00-05:00")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLICTO_CON_RESERVAS"))
                .andExpect(jsonPath("$.reservas.length()").value(1))
                .andExpect(jsonPath("$.reservas[0]").value(101))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(bloqueos.count()).isZero();
        crear("2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.barberoId").value(barbero.getId().intValue()))
                .andExpect(jsonPath("$.inicio").value("2026-10-01T16:00:00-05:00"))
                .andExpect(jsonPath("$.fin").value("2026-10-01T17:00:00-05:00"))
                .andExpect(jsonPath("$.motivo").value("Feriado"));
        assertThat(jdbc.queryForObject("select creado_por from bloqueo", Long.class)).isEqualTo(actor.getId());
        assertThat(jdbc.queryForObject("select creado_en from bloqueo", java.sql.Timestamp.class).toInstant())
                .isEqualTo(reloj.instant());
        assertThat(jdbc.queryForList("select * from reserva order by id")).isEqualTo(citas);
        var respuesta = listar(barbero.getId(), admin, "2026-10-01", "2026-10-01")
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(respuesta.getContentAsString()).get(0).properties()).hasSize(5);
    }

    @ParameterizedTest @CsvSource({"09:30,10:00", "10:30,11:00"})
    void crear_contiguoAReserva_permite(String inicio, String fin) throws Exception {
        reservar(barbero, "2026-10-01T15:00:00Z", EstadoReserva.CONFIRMADA);
        crear("2026-10-01T" + inicio + ":00-05:00", "2026-10-01T" + fin + ":00-05:00")
                .andExpect(status().isCreated());
    }

    @ParameterizedTest @EnumSource(value = EstadoReserva.class, names = "CANCELADA", mode = EnumSource.Mode.EXCLUDE)
    void crear_todoEstadoQueOcupaIncluidosTerminales_rechaza(EstadoReserva estado) throws Exception {
        Reserva r = reservar(barbero, "2026-10-01T15:00:00Z", estado);
        crear("2026-10-01T10:10:00-05:00", "2026-10-01T10:20:00-05:00")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reservas[0]").value(r.getId().intValue()));
        assertThat(bloqueos.count()).isZero();
    }

    @Test void crear_canceladaNoImpide_yBloqueosPuedenSolaparse() throws Exception {
        reservar(barbero, "2026-10-01T15:00:00Z", EstadoReserva.CANCELADA);
        for (int i = 0; i < 2; i++) crear("2026-10-01T10:10:00-05:00", "2026-10-01T10:20:00-05:00")
                .andExpect(status().isCreated());
        assertThat(bloqueos.count()).isEqualTo(2);
        assertThat(reservas.findAll().getFirst().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test void crear_reservaEnAtencionQueEmpezoAntesDeAhora_sigueOcupando() throws Exception {
        Reserva r = reservar(barbero, "2026-09-28T13:50:00Z", EstadoReserva.EN_ATENCION);
        crear("2026-09-28T09:00:00-05:00", "2026-09-28T09:10:00-05:00")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reservas[0]").value(r.getId().intValue()));
    }

    @Test void crear_variosConflictosOrdenados_sinReservasDeOtroBarbero() throws Exception {
        Reserva primera = reservar(barbero, "2026-10-01T15:00:00Z", EstadoReserva.CONFIRMADA);
        Reserva segunda = reservar(barbero, "2026-10-01T16:00:00Z", EstadoReserva.PENDIENTE);
        reservar(guardar("otro"), "2026-10-01T15:00:00Z", EstadoReserva.CONFIRMADA);
        crear("2026-10-01T09:00:00-05:00", "2026-10-01T12:00:00-05:00")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reservas.length()").value(2))
                .andExpect(jsonPath("$.reservas[0]").value(primera.getId().intValue()))
                .andExpect(jsonPath("$.reservas[1]").value(segunda.getId().intValue()));
        assertThat(bloqueos.count()).isZero();
    }

    @Test void crear_inicioIgualAhora_yDesfaseDistinto_permiteYNormalizaLima() throws Exception {
        crear("2026-09-28T16:00:00+02:00", "2026-09-28T16:30:00+02:00")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.inicio").value("2026-09-28T09:00:00-05:00"));
    }

    @Test void crear_inicioQuedaEnPasadoDuranteBloqueo_revalidaRelojSinInsertar() throws Exception {
        doAnswer(invocacion -> {
            Object resultado = mockingDetails(barberos).getMockCreationSettings().getDefaultAnswer().answer(invocacion);
            reloj.adelantar(Duration.ofSeconds(1));
            return resultado;
        }).when(barberos).bloquearPorIds(List.of(barbero.getId()));
        crear("2026-09-28T09:00:00-05:00", "2026-09-28T09:30:00-05:00")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("INICIO_EN_PASADO"));
        assertThat(bloqueos.count()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"individual", "lote"})
    void crear_inicioPasado_rechazaSinEscrituras(String tipo) throws Exception {
        var cuerpo = cuerpo("2026-09-28T08:59:59-05:00", "2026-09-28T09:30:00-05:00", "Feriado");
        if (tipo.equals("lote")) cuerpo.put("barberoIds", List.of(barbero.getId()));
        escribir(post(tipo.equals("lote") ? "/api/bloqueos/lote" : ruta(barbero.getId())), admin, cuerpo)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("INICIO_EN_PASADO"));
        assertThat(bloqueos.count()).isZero();
    }

    @ParameterizedTest @CsvSource({"individual,igual", "individual,invertido", "lote,igual", "lote,invertido"})
    void crear_intervaloInvalido_rechaza400(String tipo, String caso) throws Exception {
        var cuerpo = cuerpo("2026-10-01T11:00:00-05:00",
                caso.equals("igual") ? "2026-10-01T11:00:00-05:00" : "2026-10-01T10:00:00-05:00", "Feriado");
        if (tipo.equals("lote")) cuerpo.put("barberoIds", List.of(barbero.getId()));
        escribir(post(tipo.equals("lote") ? "/api/bloqueos/lote" : ruta(barbero.getId())), admin, cuerpo)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("INTERVALO_INVALIDO"));
        assertThat(bloqueos.count()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"", "  ", "ab", "largo", "nulo", "sinInicio", "sinFin", "sinDesfase", "json"})
    void crear_cuerpoInvalido_rechazaBeanValidationOFormato(String caso) throws Exception {
        var cuerpo = cuerpo("2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00", caso);
        switch (caso) {
            case "largo" -> cuerpo.put("motivo", "a".repeat(201));
            case "nulo" -> cuerpo.put("motivo", null);
            case "sinInicio" -> cuerpo.remove("inicio");
            case "sinFin" -> cuerpo.remove("fin");
            case "sinDesfase" -> cuerpo.put("inicio", "2026-10-01T16:00:00");
            default -> { }
        }
        Cookie token = csrf();
        mvc.perform(post(ruta(barbero.getId())).cookie(admin, token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(caso.equals("json") ? "{" : json.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(bloqueos.count()).isZero();
    }

    @ParameterizedTest @ValueSource(ints = {3, 200})
    void crear_motivoEnLimites_permite(int longitud) throws Exception {
        escribir(post(ruta(barbero.getId())), admin, cuerpo("2026-10-01T16:00:00-05:00",
                "2026-10-01T17:00:00-05:00", "a".repeat(longitud))).andExpect(status().isCreated());
    }

    @Test void listar_rangoInclusivoLima_solapesOrdenadosExcluyeContiguosYOtroPerfil() throws Exception {
        Bloqueo cruce = guardarBloqueo(barbero, "2026-09-30T23:30:00-05:00", "2026-10-01T00:30:00-05:00");
        Bloqueo tarde = guardarBloqueo(barbero, "2026-10-01T23:00:00-05:00", "2026-10-02T01:00:00-05:00");
        Bloqueo empate = guardarBloqueo(barbero, "2026-10-01T23:00:00-05:00", "2026-10-02T02:00:00-05:00");
        guardarBloqueo(barbero, "2026-09-30T23:00:00-05:00", "2026-10-01T00:00:00-05:00");
        guardarBloqueo(barbero, "2026-10-02T00:00:00-05:00", "2026-10-02T01:00:00-05:00");
        guardarBloqueo(guardar("otro"), "2026-10-01T09:00:00-05:00", "2026-10-01T10:00:00-05:00");
        listar(barbero.getId(), admin, "2026-10-01", "2026-10-01").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(cruce.getId().intValue()))
                .andExpect(jsonPath("$[1].id").value(tarde.getId().intValue()))
                .andExpect(jsonPath("$[2].id").value(empate.getId().intValue()));
        listar(barbero.getId(), admin, "2026-10-03", "2026-10-03").andExpect(content().json("[]"));
    }

    @Test void listar_366DiasInclusivos_permite() throws Exception {
        listar(barbero.getId(), admin, "2026-01-01", "2027-01-01").andExpect(status().isOk());
    }

    @ParameterizedTest @CsvSource({"2026-10-02,2026-10-01", "2026-01-01,2027-01-02", "+999999999-12-31,+999999999-12-31"})
    void listar_rangoInvalido_rechaza400(String desde, String hasta) throws Exception {
        listar(barbero.getId(), admin, desde, hasta).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @ParameterizedTest @ValueSource(strings = {"sinDesde", "sinHasta", "fecha"})
    void listar_parametrosInvalidos_rechaza400(String caso) throws Exception {
        var peticion = get(ruta(barbero.getId())).cookie(admin);
        if (!caso.equals("sinDesde")) peticion.param("desde", caso.equals("fecha") ? "01/10/2026" : "2026-10-01");
        if (!caso.equals("sinHasta")) peticion.param("hasta", "2026-10-01");
        mvc.perform(peticion).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test void barbero_leeSuyos_ocultaAjenos_ySinPerfilNoLee() throws Exception {
        guardarBloqueo(barbero, "2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00");
        Cookie sesion = login(usuarios.findById(barbero.getUsuario().getId()).orElseThrow());
        listar(barbero.getId(), sesion, "2026-10-01", "2026-10-01").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        for (long id : List.of(guardar("otro").getId(), 999L))
            listar(id, sesion, "2026-10-01", "2026-10-01").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        listar(barbero.getId(), login(usuario("sin-perfil", Rol.BARBERO)), "2026-10-01", "2026-10-01")
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @CsvSource({"CLIENTE,GET", "CLIENTE,POST", "CLIENTE,LOTE", "CLIENTE,DELETE",
            "BARBERO,POST", "BARBERO,LOTE", "BARBERO,DELETE"})
    void rolNoAutorizado_rechaza403SinCambios(Rol rol, String metodo) throws Exception {
        Cookie sesion = login(usuario("sin-permiso", rol));
        Bloqueo b = guardarBloqueo(barbero, "2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00");
        var antes = filas();
        if (metodo.equals("GET")) listar(barbero.getId(), sesion, "2026-10-01", "2026-10-01").andExpect(status().isForbidden());
        else escribir(peticion(metodo, b.getId()), sesion, lote(List.of(barbero.getId())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(filas()).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "POST", "LOTE", "DELETE"})
    void publico_sinSesion_rechaza401(String metodo) throws Exception {
        if (metodo.equals("GET")) listar(barbero.getId(), null, "2026-10-01", "2026-10-01").andExpect(status().isUnauthorized());
        else escribir(peticion(metodo, 999), null, lote(List.of(barbero.getId()))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @CsvSource({"POST,ausente", "POST,incorrecto", "LOTE,ausente", "LOTE,incorrecto", "DELETE,ausente", "DELETE,incorrecto"})
    void escritura_sesionValidaSinCsrfOCsrfIncorrecto_rechaza403(String metodo, String caso) throws Exception {
        Bloqueo b = guardarBloqueo(barbero, "2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00");
        var antes = filas();
        var peticion = peticion(metodo, b.getId()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(lote(List.of(barbero.getId()))));
        if (caso.equals("incorrecto")) peticion.cookie(csrf()).header("X-XSRF-TOKEN", "incorrecto");
        mvc.perform(peticion).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(filas()).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "POST", "LOTE", "DELETE"})
    void passwordTemporal_noPermiteBloqueos(String metodo) throws Exception {
        Usuario u = usuario("temporal", Rol.ADMIN);
        u.cambiarPassword(hash, true, reloj.instant()); usuarios.saveAndFlush(u);
        Cookie sesion = login(u);
        var resultado = metodo.equals("GET") ? listar(barbero.getId(), sesion, "2026-10-01", "2026-10-01")
                : escribir(peticion(metodo, 999), sesion, lote(List.of(barbero.getId())));
        resultado.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "POST", "LOTE", "DELETE"})
    void admin_inexistente_rechaza404(String metodo) throws Exception {
        var resultado = switch (metodo) {
            case "GET" -> listar(999, admin, "2026-10-01", "2026-10-01");
            case "POST" -> escribir(post(ruta(999)), admin, lote(List.of(barbero.getId())));
            case "LOTE" -> escribir(post("/api/bloqueos/lote"), admin, lote(List.of(barbero.getId(), 999L)));
            default -> escribir(delete("/api/bloqueos/999"), admin, Map.of());
        };
        resultado.andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThat(bloqueos.count()).isZero();
    }

    @Test void admin_inactivo_permiteSinReactivar() throws Exception {
        barbero.desactivar(reloj.instant()); barberos.saveAndFlush(barbero);
        crear("2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00").andExpect(status().isCreated());
        listar(barbero.getId(), admin, "2026-10-01", "2026-10-01").andExpect(status().isOk());
        assertThat(barberos.findById(barbero.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test void eliminar_borraFisicamente_soloElSolicitado_yRepetido404() throws Exception {
        Bloqueo b = guardarBloqueo(barbero, "2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00");
        Bloqueo otro = guardarBloqueo(barbero, "2026-10-02T16:00:00-05:00", "2026-10-02T17:00:00-05:00");
        var citas = jdbc.queryForList("select * from jornada order by id");
        SqlPruebas.limpiar();
        escribir(delete("/api/bloqueos/" + b.getId()), admin, Map.of()).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        var sql = SqlPruebas.sentencias();
        assertThat(indice(sql, "for no key update")).isGreaterThanOrEqualTo(0);
        assertThat(indice(sql, "delete from bloqueo")).isGreaterThan(indice(sql, "for no key update"));
        assertThat(bloqueos.findAll()).extracting(Bloqueo::getId).containsExactly(otro.getId());
        assertThat(jdbc.queryForList("select * from jornada order by id")).isEqualTo(citas);
        escribir(delete("/api/bloqueos/" + b.getId()), admin, Map.of()).andExpect(status().isNotFound());
    }

    @Test void lote_conflictoEnUnPerfil_noCreaNinguno_yAgrupaTodosLosConflictos() throws Exception {
        Barbero libre = guardar("libre");
        Barbero otro = guardar("otro");
        Reserva r = reservar(barbero, "2026-10-01T21:00:00Z", EstadoReserva.CONFIRMADA);
        var previo = guardarBloqueo(libre, "2026-10-02T10:00:00-05:00", "2026-10-02T11:00:00-05:00");
        var antes = filas();
        var ids = List.of(otro.getId(), libre.getId(), barbero.getId());
        var resultado = escribir(post("/api/bloqueos/lote"), admin, lote(ids)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_CON_RESERVAS")).andReturn().getResponse();
        assertThat(json.readTree(resultado.getContentAsString()).get("reservas").get(barbero.getId().toString()).get(0).longValue())
                .isEqualTo(r.getId());
        assertThat(filas()).isEqualTo(antes);
        Reserva otra = reservar(otro, "2026-10-01T21:00:00Z", EstadoReserva.PENDIENTE);
        var respuesta = escribir(post("/api/bloqueos/lote"), admin, lote(ids)).andExpect(status().isConflict()).andReturn().getResponse();
        var conflictos = json.readTree(respuesta.getContentAsString()).get("reservas");
        assertThat(conflictos.properties()).hasSize(2);
        assertThat(conflictos.get(otro.getId().toString()).get(0).longValue()).isEqualTo(otra.getId());
        assertThat(bloqueos.findAll()).extracting(Bloqueo::getId).containsExactly(previo.getId());
    }

    @Test void lote_sinConflictos_creaTodos_bloqueaUnaVezEnOrdenAntesDeConsultar() throws Exception {
        Barbero otro = guardar("otro");
        reservar(barbero, "2026-10-01T21:00:00Z", EstadoReserva.CANCELADA);
        clearInvocations(barberos);
        SqlPruebas.limpiar();
        escribir(post("/api/bloqueos/lote"), admin, lote(List.of(otro.getId(), barbero.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].barberoId").value(barbero.getId().intValue()))
                .andExpect(jsonPath("$[1].barberoId").value(otro.getId().intValue()));
        verify(barberos, times(1)).bloquearPorIds(List.of(barbero.getId(), otro.getId()));
        var sql = SqlPruebas.sentencias();
        int bloqueo = indice(sql, "for no key update"), lectura = indice(sql, "from reserva"), insercion = indice(sql, "insert into bloqueo");
        assertThat(bloqueo).isGreaterThanOrEqualTo(0);
        assertThat(sql.get(bloqueo)).contains("order by", "id");
        assertThat(lectura).isGreaterThan(bloqueo);
        assertThat(insercion).isGreaterThan(lectura);
        assertThat(sql.subList(0, insercion).stream().filter(s -> s.contains("from reserva"))).hasSize(2);
        assertThat(bloqueos.count()).isEqualTo(2);
        assertThat(jdbc.queryForList("select creado_por from bloqueo", Long.class)).containsOnly(actor.getId());
    }

    @ParameterizedTest @ValueSource(strings = {"duplicados", "vacio", "nulo", "elementoNulo", "cero", "negativo", "motivo", "sinInicio", "sinFin"})
    void lote_cuerpoInvalido_rechaza400SinCambios(String caso) throws Exception {
        var cuerpo = lote(List.of(barbero.getId()));
        switch (caso) {
            case "duplicados" -> cuerpo.put("barberoIds", List.of(barbero.getId(), barbero.getId()));
            case "vacio" -> cuerpo.put("barberoIds", List.of());
            case "nulo" -> cuerpo.remove("barberoIds");
            case "elementoNulo" -> cuerpo.put("barberoIds", Arrays.asList(barbero.getId(), null));
            case "cero" -> cuerpo.put("barberoIds", List.of(0));
            case "negativo" -> cuerpo.put("barberoIds", List.of(-1));
            case "motivo" -> cuerpo.put("motivo", "");
            case "sinInicio" -> cuerpo.remove("inicio");
            case "sinFin" -> cuerpo.remove("fin");
            default -> throw new AssertionError(caso);
        }
        escribir(post("/api/bloqueos/lote"), admin, cuerpo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(bloqueos.count()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"bloqueoPrimero", "jornadaPrimero"})
    void bloqueoYReajusteJornada_simultaneos_seSerializanSinInterbloqueo(String orden) throws Exception {
        CountDownLatch tomado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        BlockingQueue<Integer> pid = new ArrayBlockingQueue<>(1);
        var cuenta = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocacion -> {
            int turno = cuenta.incrementAndGet();
            if (turno == 2) pid.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            Object resultado = mockingDetails(barberos).getMockCreationSettings().getDefaultAnswer().answer(invocacion);
            if (turno == 1) { tomado.countDown(); esperar(liberar); }
            return resultado;
        }).when(barberos).bloquearPorIds(List.of(barbero.getId()));
        Cookie csrfA = csrf(), csrfB = csrf();
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> a = ejecutor.submit(() -> cambioConcurrente(orden.equals("bloqueoPrimero"), csrfA));
            Future<MvcResult> b = null;
            try {
                assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
                b = ejecutor.submit(() -> cambioConcurrente(!orden.equals("bloqueoPrimero"), csrfB));
                comprobarEspera(pid.poll(10, TimeUnit.SECONDS));
                assertThat(b.isDone()).isFalse();
            } finally { liberar.countDown(); }
            var resultadoA = a.get(20, TimeUnit.SECONDS);
            var resultadoB = b.get(20, TimeUnit.SECONDS);
            assertThat(resultadoA.getResponse().getStatus()).as(diagnostico(resultadoA)).isEqualTo(orden.equals("bloqueoPrimero") ? 201 : 200);
            assertThat(resultadoB.getResponse().getStatus()).as(diagnostico(resultadoB)).isEqualTo(orden.equals("bloqueoPrimero") ? 200 : 201);
        }
        assertThat(bloqueos.count()).isEqualTo(1);
        assertThat(jornadas.listarPorBarbero(barbero.getId())).singleElement()
                .satisfies(j -> { assertThat(j.getHoraInicio()).isEqualTo(LocalTime.of(8, 0));
                    assertThat(j.getHoraFin()).isEqualTo(LocalTime.of(19, 0)); });
        assertThat(jdbc.queryForObject("select hora_inicio::text from jornada", String.class)).isEqualTo("08:00:00");
        assertThat(jdbc.queryForObject("select hora_fin::text from jornada", String.class)).isEqualTo("19:00:00");
    }

    @ParameterizedTest @CsvSource({"00:00,01:00", "18:30,19:30", "23:00,23:59"})
    void jornada_horasLimaConZonaJdbcUtc_conservaValoresFisicos(String inicio, String fin) throws Exception {
        escribir(put("/api/barberos/" + barbero.getId() + "/jornadas"), admin,
                List.of(Map.of("diaSemana", 4, "horaInicio", inicio, "horaFin", fin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].horaInicio").value(inicio))
                .andExpect(jsonPath("$[0].horaFin").value(fin));
        assertThat(jdbc.queryForObject("select hora_inicio::text from jornada", String.class)).isEqualTo(inicio + ":00");
        assertThat(jdbc.queryForObject("select hora_fin::text from jornada", String.class)).isEqualTo(fin + ":00");
    }

    @Test void crear_leeReservasDespuesDeEsperar_veReservaRecienConfirmada() throws Exception {
        Usuario cliente = datos.cliente("cliente");
        var servicio = datos.servicio();
        CountDownLatch insertada = new CountDownLatch(1), liberar = new CountDownLatch(1);
        BlockingQueue<Integer> pid = new ArrayBlockingQueue<>(1);
        var idReserva = new java.util.concurrent.atomic.AtomicLong();
        doAnswer(invocacion -> {
            if (Thread.currentThread().getName().equals("crear-bloqueo"))
                pid.add(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
            return mockingDetails(barberos).getMockCreationSettings().getDefaultAnswer().answer(invocacion);
        }).when(barberos).bloquearPorIds(List.of(barbero.getId()));
        Cookie token = csrf();
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Future<?> reserva = ejecutor.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                barberos.bloquearPorIds(List.of(barbero.getId()));
                idReserva.set(reservas.saveAndFlush(new Reserva(cliente, barbero, servicio,
                        Instant.parse("2026-10-01T21:00:00Z"), EstadoReserva.CONFIRMADA, cliente, reloj.instant())).getId());
                insertada.countDown(); esperar(liberar);
            }));
            Future<MvcResult> cambio = null;
            try {
                assertThat(insertada.await(10, TimeUnit.SECONDS)).isTrue();
                cambio = ejecutor.submit(() -> { Thread.currentThread().setName("crear-bloqueo"); return cambioConcurrente(true, token); });
                comprobarEspera(pid.poll(10, TimeUnit.SECONDS));
            } finally { liberar.countDown(); }
            reserva.get(20, TimeUnit.SECONDS);
            var respuesta = cambio.get(20, TimeUnit.SECONDS).getResponse();
            assertThat(respuesta.getStatus()).isEqualTo(409);
            assertThat(json.readTree(respuesta.getContentAsString()).get("reservas").get(0).longValue()).isEqualTo(idReserva.get());
        }
        assertThat(bloqueos.count()).isZero();
    }

    private MvcResult cambioConcurrente(boolean bloqueo, Cookie token) throws Exception {
        var peticion = bloqueo ? post(ruta(barbero.getId())) : put("/api/barberos/" + barbero.getId() + "/jornadas");
        Object cuerpo = bloqueo ? lote(List.of(barbero.getId()))
                : List.of(Map.of("diaSemana", 4, "horaInicio", "08:00", "horaFin", "19:00"));
        return mvc.perform(peticion.cookie(admin, token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo))).andReturn();
    }

    private String diagnostico(MvcResult resultado) {
        StringBuilder detalle = new StringBuilder();
        for (Throwable causa = resultado.getResolvedException(); causa != null; causa = causa.getCause()) {
            detalle.append(causa.getClass().getSimpleName()).append(' ');
            if (causa instanceof org.postgresql.util.PSQLException p) {
                detalle.append(p.getSQLState()).append(' ').append(p.getServerErrorMessage().getConstraint());
            }
        }
        return detalle.toString();
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

    private MockHttpServletRequestBuilder peticion(String metodo, long id) {
        return switch (metodo) {
            case "POST" -> post(ruta(barbero.getId()));
            case "LOTE" -> post("/api/bloqueos/lote");
            default -> delete("/api/bloqueos/" + id);
        };
    }
    private ResultActions crear(String inicio, String fin) throws Exception {
        return escribir(post(ruta(barbero.getId())), admin, cuerpo(inicio, fin, "Feriado"));
    }
    private ResultActions listar(long id, Cookie sesion, String desde, String hasta) throws Exception {
        var peticion = get(ruta(id)).param("desde", desde).param("hasta", hasta);
        if (sesion != null) peticion.cookie(sesion);
        return mvc.perform(peticion);
    }
    private ResultActions escribir(MockHttpServletRequestBuilder peticion, Cookie sesion, Object cuerpo) throws Exception {
        Cookie token = csrf();
        peticion.cookie(token).header("X-XSRF-TOKEN", token.getValue());
        if (sesion != null) peticion.cookie(sesion);
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }
    private Map<String, Object> cuerpo(String inicio, String fin, String motivo) {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("inicio", inicio); cuerpo.put("fin", fin); cuerpo.put("motivo", motivo); return cuerpo;
    }
    private Map<String, Object> lote(List<Long> ids) {
        var cuerpo = cuerpo("2026-10-01T16:00:00-05:00", "2026-10-01T17:00:00-05:00", "Feriado");
        cuerpo.put("barberoIds", ids); return cuerpo;
    }
    private Reserva reservar(Barbero perfil, String inicio, EstadoReserva estado) {
        Usuario cliente = datos.cliente("reserva-" + reservas.count());
        var servicio = servicios.findAll().stream().findFirst().orElseGet(datos::servicio);
        return reservas.saveAndFlush(new Reserva(cliente, perfil, servicio, Instant.parse(inicio), estado, cliente, reloj.instant()));
    }
    private Bloqueo guardarBloqueo(Barbero perfil, String inicio, String fin) {
        return bloqueos.saveAndFlush(new Bloqueo(perfil, OffsetDateTime.parse(inicio).toInstant(),
                OffsetDateTime.parse(fin).toInstant(), "Feriado", actor, reloj.instant()));
    }
    private Barbero guardar(String nombre) { return barberos.saveAndFlush(new Barbero(usuario(nombre, Rol.BARBERO), "Cortes", reloj.instant())); }
    private Usuario usuario(String nombre, Rol rol) {
        return usuarios.saveAndFlush(new Usuario("Nombre ficticio", nombre + "@ejemplo.test", "999000001", hash,
                rol, rol == Rol.CLIENTE ? reloj.instant() : null, reloj.instant()));
    }
    private int indice(List<String> sql, String fragmento) {
        for (int i = 0; i < sql.size(); i++) if (sql.get(i).contains(fragmento)) return i;
        return -1;
    }
    private List<?> filas() { return jdbc.queryForList("select * from bloqueo order by id"); }
    private String ruta(long id) { return "/api/barberos/" + id + "/bloqueos"; }
    private Cookie csrf() throws Exception { return mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN"); }
    private Cookie login(Usuario usuario) throws Exception {
        Cookie token = csrf();
        return mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("correo", usuario.getCorreo(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("BT_SESION");
    }
}
