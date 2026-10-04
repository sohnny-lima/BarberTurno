package pe.barberturno.scheduling;

import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.catalog.*;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.*;
import pe.barberturno.scheduling.dto.DisponibilidadDto;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
class DisponibilidadIT {
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 1);
    private static final String PASSWORD = "ClaveDisponible123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired BarberoRepository barberos;
    @Autowired JornadaRepository jornadas;
    @Autowired BloqueoRepository bloqueos;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReservaRepository reservas;
    @Autowired ServicioRepository servicios;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    @Autowired DisponibilidadService disponibilidad;
    private DatosPrueba datos;
    private Barbero carlos;
    private Usuario cliente;
    private Servicio servicio;
    private Reserva bt101;

    @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
        datos = new DatosPrueba(usuarios, barberos, servicios);
        carlos = datos.barbero("carlos");
        cliente = datos.cliente("cliente");
        servicio = datos.servicio();
        semana(carlos, 4, "09:00", "13:00", "14:00", "18:00");
        reservar(carlos, cliente, FECHA.minusDays(1), "10:00", EstadoReserva.CANCELADA);
        bt101 = reservar(carlos, cliente, FECHA, "10:00", EstadoReserva.CONFIRMADA);
        bloqueos.saveAndFlush(new Bloqueo(carlos, instante(FECHA, "16:00"), instante(FECHA, "17:00"),
                "K1 ficticio", carlos.getUsuario(), reloj.instant()));
    }

    @AfterEach void limpiar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        SqlPruebas.limpiar();
    }

    @Test void corrida_publicaSinSesion_intervalosCompletosContiguosYDesfaseLima() throws Exception {
        assertThat(bt101.getId()).isEqualTo(101L);
        assertThat(jdbc.queryForObject("select hora_inicio::text from jornada order by id limit 1", String.class)).isEqualTo("09:00:00");
        assertThat(jornadas.listarPorBarbero(carlos.getId()).getFirst().getHoraInicio()).isEqualTo(LocalTime.of(9, 0));
        var antes = jdbc.queryForList("select * from reserva order by id");
        var resultado = consultar(peticion(FECHA));
        assertThat(resultado.fecha()).isEqualTo(FECHA);
        assertThat(resultado.servicioId()).isEqualTo(servicio.getId());
        assertThat(resultado.duracionMin()).isEqualTo(30);
        assertThat(horas(resultado)).contains("09:30", "10:30", "15:30", "17:00", "17:30")
                .doesNotContain("09:40", "09:50", "10:00", "10:10", "10:20");
        assertThat(horas(resultado)).noneMatch(h -> h.compareTo("13:00") >= 0 && h.compareTo("14:00") < 0)
                .noneMatch(h -> h.compareTo("15:40") >= 0 && h.compareTo("17:00") < 0)
                .isSorted().doesNotHaveDuplicates().hasSize(31);
        assertThat(horas(resultado).getLast()).isEqualTo("17:30");
        resultado.franjas().forEach(f -> {
            assertThat(f.inicio().getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
            assertThat(f.fin().getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
            assertThat(Duration.between(f.inicio(), f.fin())).isEqualTo(Duration.ofMinutes(30));
            assertThat(f.barberoIds()).containsExactly(carlos.getId());
        });
        assertThat(jdbc.queryForList("select * from reserva order by id")).isEqualTo(antes);
    }

    @Test void validacion_todaCandidataDeRejillaCoincideConConsulta_yClasificaCodigo() throws Exception {
        Set<String> libres = new HashSet<>(horas(consultar(peticion(FECHA))));
        for (int minuto = 8 * 60; minuto <= 18 * 60; minuto += 10) {
            String hora = LocalTime.of(minuto / 60, minuto % 60).toString();
            Instant inicio = instante(FECHA, hora);
            Optional<ErrorCodigo> codigo = disponibilidad.validarFranja(carlos.getId(), inicio,
                    inicio.plusSeconds(1800), 0);
            if (libres.contains(hora)) {
                assertThat(codigo).as(hora).isEmpty();
            } else {
                boolean cabe = (minuto >= 540 && minuto <= 750) || (minuto >= 840 && minuto <= 1050);
                assertThat(codigo).as(hora).contains(cabe ? ErrorCodigo.FRANJA_NO_DISPONIBLE : ErrorCodigo.FUERA_DE_HORARIO);
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"2026-09-27", "2026-10-29"})
    void consulta_fechaFueraDeHorizonte_vaciaYValidacionConCodigo(String fecha) throws Exception {
        LocalDate dia = LocalDate.parse(fecha);
        assertThat(consultar(peticion(dia)).franjas()).isEmpty();
        assertThat(disponibilidad.validarFranja(carlos.getId(), instante(dia, "10:00"),
                instante(dia, "10:30"), 0)).contains(ErrorCodigo.FUERA_DE_HORIZONTE);
    }

    @Test void consulta_hoyYUltimoDiaInclusivos_relojYFechaLima() throws Exception {
        semana(carlos, 1, "09:00", "10:00");
        semana(carlos, 3, "09:00", "10:00");
        LocalDate hoy = TiempoNegocio.fechaLima(reloj.instant());
        assertThat(horas(consultar(peticion(hoy)))).containsExactly("09:10", "09:20", "09:30");
        assertThat(horas(consultar(peticion(hoy.plusDays(30))))).containsExactly("09:00", "09:10", "09:20", "09:30");
        // El Clock avanza a UTC martes pero Lima aún es lunes 23:50.
        reloj.adelantar(Duration.ofHours(14).plusMinutes(50));
        assertThat(disponibilidad.validarFranja(carlos.getId(), instante(hoy, "10:00"),
                instante(hoy, "10:30"), 0)).contains(ErrorCodigo.INICIO_EN_PASADO);
        assertThat(consultar(peticion(hoy)).franjas()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"08:50", "09:00"})
    void validacion_inicioPasadoOIgualAhora_precedeFueraDeHorario(String hora) {
        var hoy = TiempoNegocio.fechaLima(reloj.instant());
        assertThat(disponibilidad.validarFranja(carlos.getId(), instante(hoy, hora),
                instante(hoy, hora).plusSeconds(1800), 0)).contains(ErrorCodigo.INICIO_EN_PASADO);
    }

    @ParameterizedTest @ValueSource(strings = {"servicio", "barbero"})
    void consulta_recursoInactivo_422(String recurso) throws Exception {
        if (recurso.equals("servicio")) {
            servicio.desactivar(reloj.instant()); servicios.saveAndFlush(servicio);
        } else {
            carlos.desactivar(reloj.instant()); barberos.saveAndFlush(carlos);
            assertThat(disponibilidad.validarFranja(carlos.getId(), instante(FECHA, "09:00"),
                    instante(FECHA, "09:30"), 0)).contains(ErrorCodigo.RECURSO_INACTIVO);
            assertThat(disponibilidad.consultarFranjas(servicio.getId(), Optional.empty(), FECHA, Optional.empty()).franjas()).isEmpty();
        }
        mvc.perform(peticion(FECHA)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RECURSO_INACTIVO"));
    }

    @ParameterizedTest @ValueSource(strings = {"servicioId", "barberoId"})
    void consulta_recursoInexistente_404(String parametro) throws Exception {
        var peticion = get("/api/disponibilidad").param("servicioId", parametro.equals("servicioId") ? "999999" : servicio.getId().toString())
                .param("barberoId", parametro.equals("barberoId") ? "999999" : carlos.getId().toString())
                .param("fecha", FECHA.toString());
        mvc.perform(peticion).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThatThrownBy(() -> disponibilidad.validarFranja(999999, instante(FECHA, "09:00"),
                instante(FECHA, "09:30"), 0)).isInstanceOfSatisfying(NegocioException.class,
                        e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @ParameterizedTest @EnumSource(EstadoReserva.class)
    void ocupacion_cualquierEstadoSalvoCancelada(EstadoReserva estado) throws Exception {
        reservar(carlos, cliente, FECHA, "11:00", estado);
        var horas = horas(consultar(peticion(FECHA)));
        if (estado == EstadoReserva.CANCELADA) assertThat(horas).contains("10:40", "10:50", "11:00");
        else assertThat(horas).doesNotContain("10:40", "10:50", "11:00");
    }

    @Test void rf21_fusionaPerfilesOrdenados_omiteInactivosYNoTieneNmasUno() throws Exception {
        var miguel = datos.barbero("miguel");
        semana(miguel, 4, "09:00", "13:00", "14:00", "18:00");
        var inactivo = datos.barbero("inactivo");
        semana(inactivo, 4, "09:00", "18:00");
        inactivo.desactivar(reloj.instant()); barberos.saveAndFlush(inactivo);
        SqlPruebas.limpiar();
        var resultado = consultar(get("/api/disponibilidad").param("servicioId", servicio.getId().toString()).param("fecha", FECHA.toString()));
        assertThat(resultado.franjas()).filteredOn(f -> f.inicio().toLocalTime().equals(LocalTime.of(9, 30)))
                .singleElement().satisfies(f -> assertThat(f.barberoIds()).containsExactly(carlos.getId(), miguel.getId()));
        assertThat(resultado.franjas()).filteredOn(f -> f.inicio().toLocalTime().equals(LocalTime.of(10, 0)))
                .singleElement().satisfies(f -> assertThat(f.barberoIds()).containsExactly(miguel.getId()));
        assertThat(resultado.franjas()).noneMatch(f -> f.barberoIds().contains(inactivo.getId()));
        var sql = SqlPruebas.sentencias();
        for (String tabla : List.of("servicio", "barbero", "jornada", "bloqueo", "reserva")) {
            assertThat(sql.stream().filter(s -> s.contains(" from " + tabla + " ")).count()).as(tabla).isEqualTo(1);
        }
        assertThat(sql).hasSize(5).noneMatch(s -> s.contains("for no key update") || s.startsWith("insert") || s.startsWith("update"));
    }

    @Test void consulta_sinJornadaOSinPerfiles_disponibilidadVacia() throws Exception {
        assertThat(consultar(peticion(FECHA.plusDays(1))).franjas()).isEmpty();
        carlos.desactivar(reloj.instant()); barberos.saveAndFlush(carlos);
        assertThat(disponibilidad.consultarFranjas(servicio.getId(), Optional.empty(), FECHA, Optional.empty()).franjas()).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = Rol.class, names = {"CLIENTE", "ADMIN"})
    void exclusion_propietarioOAdmin_liberaFranjaYCoincideValidacion(Rol rol) throws Exception {
        Usuario actor = rol == Rol.CLIENTE ? cliente : datos.cliente("admin");
        jdbc.update("update usuario set rol = ?, password_hash = ? where id = ?", rol.name(), passwords.encode(PASSWORD), actor.getId());
        var resultado = consultar(peticion(FECHA).param("excluirReservaId", bt101.getId().toString()).cookie(login(actor)));
        assertThat(horas(resultado)).contains("09:40", "09:50", "10:00", "10:10", "10:20");
        for (var f : resultado.franjas()) {
            assertThat(disponibilidad.validarFranja(carlos.getId(), f.inicio().toInstant(), f.fin().toInstant(), bt101.getId())).isEmpty();
        }
        assertThat(horas(consultar(peticion(FECHA)))).doesNotContain("10:00");
    }

    @ParameterizedTest @ValueSource(strings = {"anonimo", "ajeno", "barbero", "inexistente", "adminInexistente", "ajenoFueraHorizonte"})
    void exclusion_sinPermisoOInexistente_mismo404(String caso) throws Exception {
        var peticion = peticion(caso.equals("ajenoFueraHorizonte") ? FECHA.plusDays(60) : FECHA)
                .param("excluirReservaId", caso.contains("nexistente") ? "999999" : bt101.getId().toString());
        if (!caso.equals("anonimo")) {
            var actor = datos.cliente("actor");
            String rol = caso.equals("barbero") ? "BARBERO" : caso.equals("adminInexistente") ? "ADMIN" : "CLIENTE";
            jdbc.update("update usuario set rol = ?, password_hash = ? where id = ?", rol, passwords.encode(PASSWORD), actor.getId());
            peticion.cookie(login(actor));
        }
        mvc.perform(peticion).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("Recurso no encontrado."));
    }

    @ParameterizedTest @ValueSource(strings = {
        "servicioId=", "servicioId=abc", "servicioId=0", "servicioId=-1", "servicioId=9223372036854775808",
        "fecha=", "fecha=abc", "fecha=2026-02-30", "fecha=01/10/2026",
        "barberoId=", "barberoId=abc", "barberoId=0", "barberoId=-1",
        "excluirReservaId=", "excluirReservaId=abc", "excluirReservaId=0", "excluirReservaId=-1"})
    void parametros_malFormados_400Validacion(String caso) throws Exception {
        String[] partes = caso.split("=", -1);
        var peticion = get("/api/disponibilidad");
        peticion.param("servicioId", partes[0].equals("servicioId") ? partes[1] : servicio.getId().toString());
        peticion.param("fecha", partes[0].equals("fecha") ? partes[1] : FECHA.toString());
        if (!partes[0].equals("servicioId") && !partes[0].equals("fecha")) peticion.param(partes[0], partes[1]);
        mvc.perform(peticion).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @ParameterizedTest @ValueSource(strings = {"servicioId", "fecha"})
    void parametros_obligatoriosAusentes_400Validacion(String ausente) throws Exception {
        var p = get("/api/disponibilidad");
        if (!ausente.equals("servicioId")) p.param("servicioId", servicio.getId().toString());
        if (!ausente.equals("fecha")) p.param("fecha", FECHA.toString());
        mvc.perform(p).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @ParameterizedTest @ValueSource(strings = {"09:01", "09:10:01", "09:10:00.000000001", "13:00", "17:40", "18:00"})
    void validacion_fueraJornadaORejilla_rechaza(String hora) {
        Instant inicio = instante(FECHA, hora);
        assertThat(disponibilidad.validarFranja(carlos.getId(), inicio, inicio.plusSeconds(1800), 0))
                .contains(ErrorCodigo.FUERA_DE_HORARIO);
    }

    @ParameterizedTest @ValueSource(longs = {0, -60, 1, 61, 128849018880L})
    void validacion_duracionNoEnteraOInvalida_rechaza(long segundos) {
        var inicio = instante(FECHA, "09:30");
        assertThat(disponibilidad.validarFranja(carlos.getId(), inicio, inicio.plusSeconds(segundos), 0))
                .contains(ErrorCodigo.FUERA_DE_HORARIO);
    }

    @Test void validacion_duracionConNanosegundos_rechaza() {
        var inicio = instante(FECHA, "09:30");
        assertThat(disponibilidad.validarFranja(carlos.getId(), inicio, inicio.plusSeconds(1800).plusNanos(1), 0))
                .contains(ErrorCodigo.FUERA_DE_HORARIO);
    }

    @Test void rejilla_seAnclaAInicioDelIntervalo_yDuracionDeCatalogo() throws Exception {
        reservas.deleteAllInBatch();
        jornadas.deleteAllInBatch();
        semana(carlos, 4, "09:05", "10:05");
        servicio.editar(servicio.getNombre(), servicio.getDescripcion(), (short) 40, servicio.getPrecio(), reloj.instant());
        servicios.saveAndFlush(servicio);
        var r = consultar(peticion(FECHA));
        assertThat(r.duracionMin()).isEqualTo(40);
        assertThat(horas(r)).containsExactly("09:05", "09:15", "09:25");
        for (var f : r.franjas()) assertThat(disponibilidad.validarFranja(carlos.getId(),
                f.inicio().toInstant(), f.fin().toInstant(), 0)).isEmpty();
        assertThat(disponibilidad.validarFranja(carlos.getId(), instante(FECHA, "09:10"),
                instante(FECHA, "09:50"), 0)).contains(ErrorCodigo.FUERA_DE_HORARIO);
    }

    @Test void datosDiaLima_incluyenCrucesMedianocheYExcluyenContiguos_yDiaIsoLocal() throws Exception {
        jornadas.deleteAllInBatch();
        semana(carlos, 4, "00:00", "01:00", "20:00", "23:59");
        reservar(carlos, cliente, FECHA.minusDays(1), "23:50", EstadoReserva.CONFIRMADA);
        reservar(carlos, cliente, FECHA, "20:00", EstadoReserva.COMPLETADA);
        bloqueos.saveAndFlush(new Bloqueo(carlos, instante(FECHA.minusDays(1), "23:30"),
                instante(FECHA, "00:30"), "Cruce ficticio", carlos.getUsuario(), reloj.instant()));
        bloqueos.saveAndFlush(new Bloqueo(carlos, instante(FECHA.minusDays(1), "23:00"),
                instante(FECHA, "00:00"), "Contiguo anterior", carlos.getUsuario(), reloj.instant()));
        bloqueos.saveAndFlush(new Bloqueo(carlos, instante(FECHA.plusDays(1), "00:00"),
                instante(FECHA.plusDays(1), "01:00"), "Contiguo siguiente", carlos.getUsuario(), reloj.instant()));
        var r = consultar(peticion(FECHA));
        assertThat(horas(r)).contains("00:30", "20:30", "23:20").doesNotContain("00:00", "00:10", "00:20", "20:00", "20:10", "20:20");
        var sql = SqlPruebas.sentencias();
        assertThat(sql).anyMatch(s -> s.contains(" from bloqueo "));
        assertThat(r.franjas()).anyMatch(f -> f.inicio().toInstant().atOffset(ZoneOffset.UTC).toLocalDate().equals(FECHA.plusDays(1)));
    }

    @Test void rendimiento_diezBarberosTreintaDiasTrescientasReservas_p95Menor200ms() throws Exception {
        jornadas.deleteAllInBatch();
        reservas.deleteAllInBatch();
        bloqueos.deleteAllInBatch();
        List<Barbero> perfiles = new ArrayList<>();
        perfiles.add(carlos);
        for (int n = 1; n < 10; n++) perfiles.add(datos.barbero("carga-" + n));
        var citas = new ArrayList<Reserva>();
        var semana = new ArrayList<Jornada>();
        for (int n = 0; n < perfiles.size(); n++) {
            var perfil = perfiles.get(n);
            var propietario = datos.cliente("carga-" + n);
            for (int dia = 1; dia <= 7; dia++) {
                semana.add(new Jornada(perfil, (short) dia, LocalTime.of(9, 0), LocalTime.of(13, 0)));
                semana.add(new Jornada(perfil, (short) dia, LocalTime.of(14, 0), LocalTime.of(18, 0)));
            }
            for (int dia = 0; dia < 30; dia++) {
                citas.add(new Reserva(propietario, perfil, servicio, FECHA.plusDays(dia).atTime(10, 0)
                        .atZone(TiempoNegocio.ZONA).toInstant(), EstadoReserva.CONFIRMADA, propietario, reloj.instant()));
            }
        }
        jornadas.saveAllAndFlush(semana);
        reservas.saveAllAndFlush(citas);
        assertThat(reservas.count()).isEqualTo(300);
        for (int n = 0; n < 5; n++) consultar(get("/api/disponibilidad").param("servicioId", servicio.getId().toString()).param("fecha", FECHA.toString()));
        List<Double> tiempos = new ArrayList<>();
        for (int n = 0; n < 50; n++) {
            long inicio = System.nanoTime();
            var r = consultar(get("/api/disponibilidad").param("servicioId", servicio.getId().toString())
                    .param("fecha", FECHA.plusDays(n % 27).toString()));
            tiempos.add((System.nanoTime() - inicio) / 1_000_000.0);
            assertThat(r.franjas()).isNotEmpty().allSatisfy(f -> assertThat(f.barberoIds()).hasSize(10));
        }
        Collections.sort(tiempos);
        double p95 = tiempos.get((int) Math.ceil(0.95 * tiempos.size()) - 1);
        System.out.printf(Locale.ROOT, "T-19 rendimiento: 50 GET secuenciales; p95=%.3f ms; max=%.3f ms; 10 barberos; 300 reservas%n", p95, tiempos.getLast());
        assertThat(p95).isLessThan(200.0);
    }

    @ParameterizedTest @CsvSource({"CLIENTE,true", "CLIENTE,false", "ADMIN,true", "ADMIN,false"})
    void reprogramacion_catalogoModificadoYDesactivado_conservaReferencia(Rol rol, boolean especifico) throws Exception {
        var sesion = sesionExclusion(rol);
        cambiarDuracionCatalogo();
        var creacion = consultar(consultaModo(especifico, FECHA));
        assertThat(creacion.duracionMin()).isEqualTo(40);
        assertThat(creacion.franjas()).isNotEmpty().allSatisfy(f ->
                assertThat(Duration.between(f.inicio(), f.fin())).isEqualTo(Duration.ofMinutes(40)));
        assertThat(horas(creacion)).doesNotContain("17:30");

        SqlPruebas.limpiar();
        var referencia = consultar(consultaModo(especifico, FECHA)
                .param("excluirReservaId", bt101.getId().toString()).cookie(sesion));
        var sql = SqlPruebas.sentencias();
        assertThat(sql).anyMatch(q -> q.contains("duracion_ref_min") && q.contains("servicio_id"));
        assertThat(sql).noneMatch(q -> q.contains("for no key update") || q.contains("for update")
                || q.startsWith("insert") || q.startsWith("update"));
        assertThat(referencia.duracionMin()).isEqualTo(30);
        assertThat(horas(referencia)).contains("10:00", "17:30").hasSize(36);
        assertThat(referencia.franjas()).allSatisfy(f ->
                assertThat(Duration.between(f.inicio(), f.fin())).isEqualTo(Duration.ofMinutes(30)));

        servicio.desactivar(reloj.instant());
        servicios.saveAndFlush(servicio);
        var inactivo = consultar(consultaModo(especifico, FECHA)
                .param("excluirReservaId", bt101.getId().toString()).cookie(sesion));
        assertThat(inactivo).isEqualTo(referencia);
        mvc.perform(consultaModo(especifico, FECHA)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RECURSO_INACTIVO"));
        var reserva = reservas.findById(bt101.getId()).orElseThrow();
        assertThat(reserva.getDuracionRefMin()).isEqualTo((short) 30);
        assertThat(reserva.getVersion()).isEqualTo(bt101.getVersion());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void reprogramacion_todasLasFranjasPublicadasAceptadasPorPost_yRejillaOcupadaRechazada(boolean inactivo) throws Exception {
        var sesion = sesionExclusion(Rol.CLIENTE);
        cambiarDuracionCatalogo();
        if (inactivo) {
            servicio.desactivar(reloj.instant());
            servicios.saveAndFlush(servicio);
        }
        var resultado = consultar(peticion(FECHA).param("excluirReservaId", bt101.getId().toString()).cookie(sesion));
        assertThat(resultado.duracionMin()).isEqualTo(30);
        assertThat(resultado.franjas()).hasSize(36);
        assertThat(horas(resultado)).contains("17:30").doesNotContain("16:00");
        Cookie csrf = mvc.perform(get("/api/auth/sesion").cookie(sesion)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        int version = bt101.getVersion();
        for (var f : resultado.franjas()) {
            assertThat(disponibilidad.validarFranja(carlos.getId(), f.inicio().toInstant(),
                    f.fin().toInstant(), bt101.getId())).as(f.inicio().toString()).isEmpty();
            var respuesta = mvc.perform(post("/api/reservas/{id}/reprogramacion", bt101.getId())
                    .cookie(sesion, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                            Map.of("inicio", f.inicio(), "barberoId", carlos.getId(), "version", version))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.duracionMin").value(30)).andReturn();
            var reserva = json.readTree(respuesta.getResponse().getContentAsString());
            version = reserva.get("version").asInt();
            assertThat(OffsetDateTime.parse(reserva.get("inicio").asText())).isEqualTo(f.inicio());
            assertThat(OffsetDateTime.parse(reserva.get("fin").asText())).isEqualTo(f.fin());
            assertThat(reserva.get("servicio").get("id").asLong()).isEqualTo(servicio.getId());
            assertThat(reserva.get("precioRef").decimalValue()).isEqualByComparingTo(bt101.getPrecioRef());
        }
        var antes = jdbc.queryForList("select * from reserva where id = ?", bt101.getId());
        assertThat(disponibilidad.validarFranja(carlos.getId(), instante(FECHA, "16:00"),
                instante(FECHA, "16:30"), bt101.getId())).contains(ErrorCodigo.FRANJA_NO_DISPONIBLE);
        mvc.perform(post("/api/reservas/{id}/reprogramacion", bt101.getId())
                .cookie(sesion, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("inicio", TiempoNegocio.aLima(instante(FECHA, "16:00")), "version", version))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FRANJA_NO_DISPONIBLE"));
        assertThat(jdbc.queryForList("select * from reserva where id = ?", bt101.getId())).isEqualTo(antes);
    }

    @ParameterizedTest @CsvSource({"CLIENTE,false", "CLIENTE,true", "ADMIN,false", "ADMIN,true"})
    void reprogramacion_servicioDistinto_400InclusoFueraDeHorizonte(Rol rol, boolean fueraHorizonte) throws Exception {
        var sesion = sesionExclusion(rol);
        var otro = servicios.saveAndFlush(new Servicio("Otro ficticio", "Servicio distinto", (short) 30,
                servicio.getPrecio(), reloj.instant()));
        mvc.perform(get("/api/disponibilidad").param("servicioId", otro.getId().toString())
                .param("barberoId", carlos.getId().toString())
                .param("fecha", (fueraHorizonte ? FECHA.plusDays(60) : FECHA).toString())
                .param("excluirReservaId", bt101.getId().toString()).cookie(sesion))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detail").value("El servicio solicitado debe coincidir con el de la reserva."));
    }

    @Test void reprogramacion_reservaAjenaYServicioDistinto_404AntesDeLeerReferenciaOCatalogo() throws Exception {
        var actor = datos.cliente("ajeno");
        jdbc.update("update usuario set password_hash = ? where id = ?", passwords.encode(PASSWORD), actor.getId());
        var sesion = login(actor);
        SqlPruebas.limpiar();
        mvc.perform(get("/api/disponibilidad").param("servicioId", "999999")
                .param("fecha", FECHA.plusDays(60).toString()).param("barberoId", carlos.getId().toString())
                .param("excluirReservaId", bt101.getId().toString()).cookie(sesion))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("Recurso no encontrado."));
        assertThat(SqlPruebas.sentencias()).noneMatch(q -> q.contains("duracion_ref_min") || q.contains(" from servicio "));
    }

    @Test void reprogramacion_barberoInactivo_422AunqueServicioInactivo() throws Exception {
        var sesion = sesionExclusion(Rol.CLIENTE);
        servicio.desactivar(reloj.instant());
        servicios.saveAndFlush(servicio);
        carlos.desactivar(reloj.instant());
        barberos.saveAndFlush(carlos);
        mvc.perform(peticion(FECHA).param("excluirReservaId", bt101.getId().toString()).cookie(sesion))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("RECURSO_INACTIVO"))
                .andExpect(jsonPath("$.detail").value("El barbero está inactivo."));
        var sinPerfiles = consultar(consultaModo(false, FECHA)
                .param("excluirReservaId", bt101.getId().toString()).cookie(sesion));
        assertThat(sinPerfiles.duracionMin()).isEqualTo(30);
        assertThat(sinPerfiles.franjas()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"2026-09-27", "2026-10-29"})
    void reprogramacion_fueraHorizonte_conservaDuracionReferenciaSinFranjas(String fecha) throws Exception {
        var sesion = sesionExclusion(Rol.CLIENTE);
        cambiarDuracionCatalogo();
        servicio.desactivar(reloj.instant());
        servicios.saveAndFlush(servicio);
        var resultado = consultar(peticion(LocalDate.parse(fecha))
                .param("excluirReservaId", bt101.getId().toString()).cookie(sesion));
        assertThat(resultado.duracionMin()).isEqualTo(30);
        assertThat(resultado.franjas()).isEmpty();
    }

    private void cambiarDuracionCatalogo() {
        servicio.editar(servicio.getNombre(), servicio.getDescripcion(), (short) 40, servicio.getPrecio(), reloj.instant());
        servicios.saveAndFlush(servicio);
    }

    private Cookie sesionExclusion(Rol rol) throws Exception {
        Usuario actor = rol == Rol.CLIENTE ? cliente : datos.cliente("admin-referencia");
        jdbc.update("update usuario set rol = ?, password_hash = ? where id = ?", rol.name(), passwords.encode(PASSWORD), actor.getId());
        return login(actor);
    }

    private MockHttpServletRequestBuilder consultaModo(boolean especifico, LocalDate fecha) {
        var consulta = get("/api/disponibilidad").param("servicioId", servicio.getId().toString())
                .param("fecha", fecha.toString());
        return especifico ? consulta.param("barberoId", carlos.getId().toString()) : consulta;
    }

    private MockHttpServletRequestBuilder peticion(LocalDate fecha) {
        return get("/api/disponibilidad").param("servicioId", servicio.getId().toString())
                .param("fecha", fecha.toString()).param("barberoId", carlos.getId().toString());
    }
    private DisponibilidadDto consultar(MockHttpServletRequestBuilder peticion) throws Exception {
        var r = mvc.perform(peticion).andExpect(status().isOk()).andReturn();
        return json.rebuild().disable(tools.jackson.databind.cfg.DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build().readValue(r.getResponse().getContentAsString(), DisponibilidadDto.class);
    }
    private List<String> horas(DisponibilidadDto dto) {
        return dto.franjas().stream().map(f -> f.inicio().toLocalTime().toString()).toList();
    }
    private Instant instante(LocalDate fecha, String hora) {
        return fecha.atTime(LocalTime.parse(hora)).atZone(TiempoNegocio.ZONA).toInstant();
    }
    private void semana(Barbero perfil, int dia, String... horas) {
        for (int i = 0; i < horas.length; i += 2) jornadas.saveAndFlush(new Jornada(perfil, (short) dia,
                LocalTime.parse(horas[i]), LocalTime.parse(horas[i + 1])));
    }
    private Reserva reservar(Barbero perfil, Usuario propietario, LocalDate fecha, String hora, EstadoReserva estado) {
        return reservas.saveAndFlush(new Reserva(propietario, perfil, servicio, instante(fecha, hora),
                estado, propietario, reloj.instant()));
    }
    private Cookie login(Usuario actor) throws Exception {
        Cookie token = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("correo", actor.getCorreo(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("BT_SESION");
    }
}
