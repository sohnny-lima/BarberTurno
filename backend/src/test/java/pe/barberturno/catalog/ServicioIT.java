package pe.barberturno.catalog;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.reservations.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
class ServicioIT {
    private static final String PASSWORD = "ClaveCatalogo123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @MockitoSpyBean ServicioRepository servicios;
    @Autowired UsuarioRepository usuarios;
    @Autowired BarberoRepository barberos;
    @Autowired ReservaRepository reservas;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;

    @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
    }
    @AfterEach void limpiar() { LimpiezaBaseDatos.limpiar(jdbc); }

    @Test void crud_adminConLoginReal_creaEditaDesactivaYReactivaConClock() throws Exception {
        Cookie sesion = login(Rol.ADMIN, false);
        var creado = escribir("POST", 0, sesion, datos("Corte", 30, "20.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Corte"))
                .andExpect(jsonPath("$.descripcion").value("Descripción ficticia"))
                .andExpect(jsonPath("$.duracionMin").value(30))
                .andExpect(jsonPath("$.precio").value(20))
                .andExpect(jsonPath("$.activo").value(true)).andReturn();
        long id = json.readTree(creado.getResponse().getContentAsString()).get("id").asLong();
        assertThat(json.readTree(creado.getResponse().getContentAsString()).size()).isEqualTo(6);
        Servicio inicial = servicios.findById(id).orElseThrow();
        assertThat(inicial.getCreadoEn()).isEqualTo(reloj.instant());
        assertThat(inicial.getActualizadoEn()).isEqualTo(reloj.instant());

        reloj.adelantar(Duration.ofMinutes(1));
        escribir("PUT", id, sesion, datos("Corte clásico", 40, "25.50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duracionMin").value(40))
                .andExpect(jsonPath("$.precio").value(25.5));
        Servicio editado = servicios.findById(id).orElseThrow();
        assertThat(editado.getActualizadoEn()).isEqualTo(reloj.instant());
        assertThat(editado.getCreadoEn()).isEqualTo(inicial.getCreadoEn());

        reloj.adelantar(Duration.ofMinutes(1));
        escribir("PATCH", id, sesion, Map.of("activo", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        assertThat(servicios.findById(id).orElseThrow().getActualizadoEn()).isEqualTo(reloj.instant());
        mvc.perform(get("/api/servicios")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/servicios").cookie(sesion).param("incluirInactivos", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));

        escribir("PUT", id, sesion, datos("Corte inactivo", 50, "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        reloj.adelantar(Duration.ofMinutes(1));
        escribir("PATCH", id, sesion, Map.of("activo", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
        Servicio reactivado = servicios.findById(id).orElseThrow();
        assertThat(reactivado.getActualizadoEn()).isEqualTo(reloj.instant());
        assertThat(reactivado.getPrecio()).isEqualByComparingTo("0.00");
        mvc.perform(get("/api/servicios")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        assertThat(servicios.count()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"PUBLICO", "CLIENTE", "BARBERO", "ADMIN"})
    void listado_inactivosSoloParaAdminYOrdenPorNombre(String actor) throws Exception {
        Servicio z = guardar("Zeta");
        guardar("alfa");
        guardar("Beta");
        z.desactivar(reloj.instant());
        servicios.saveAndFlush(z);
        Cookie sesion = actor.equals("PUBLICO") ? null : login(Rol.valueOf(actor), false);
        var normal = get("/api/servicios");
        if (sesion != null) normal.cookie(sesion);
        mvc.perform(normal).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("alfa"))
                .andExpect(jsonPath("$[1].nombre").value("Beta"));
        var ampliado = get("/api/servicios").param("incluirInactivos", "true");
        if (sesion != null) ampliado.cookie(sesion);
        mvc.perform(ampliado).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(actor.equals("ADMIN") ? 3 : 2))
                .andExpect(jsonPath("$[0].nombre").value("alfa"));
    }

    @ParameterizedTest @CsvSource({
            "POST,CLIENTE", "PUT,CLIENTE", "PATCH,CLIENTE",
            "POST,BARBERO", "PUT,BARBERO", "PATCH,BARBERO"})
    void escritura_rolNoAutorizado_devuelve403SinCambios(String metodo, Rol rol) throws Exception {
        Servicio servicio = guardar("Corte");
        Cookie sesion = login(rol, false);
        var anterior = filaServicio(servicio.getId());
        escribir(metodo, servicio.getId(), sesion, cuerpo(metodo))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(filaServicio(servicio.getId())).isEqualTo(anterior);
        assertThat(servicios.count()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void escritura_sinSesionConCsrf_devuelve401(String metodo) throws Exception {
        Servicio servicio = guardar("Corte");
        escribir(metodo, servicio.getId(), null, cuerpo(metodo)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        assertThat(servicios.count()).isEqualTo(1);
    }

    @ParameterizedTest @CsvSource({
            "POST,ausente", "PUT,ausente", "PATCH,ausente",
            "POST,incorrecto", "PUT,incorrecto", "PATCH,incorrecto"})
    void escritura_sesionValidaSinCsrfOCsrfIncorrecto_devuelve403(String metodo, String caso) throws Exception {
        Servicio servicio = guardar("Corte");
        Cookie sesion = login(Rol.ADMIN, false);
        var anterior = filaServicio(servicio.getId());
        var solicitud = solicitud(metodo, servicio.getId()).cookie(sesion)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo(metodo)));
        if (caso.equals("incorrecto")) solicitud.cookie(csrf()).header("X-XSRF-TOKEN", "incorrecto");
        mvc.perform(solicitud).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(filaServicio(servicio.getId())).isEqualTo(anterior);
        assertThat(servicios.count()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void escritura_adminConPasswordTemporal_devuelve403(String metodo) throws Exception {
        Servicio servicio = guardar("Corte");
        escribir(metodo, servicio.getId(), login(Rol.ADMIN, true), cuerpo(metodo))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }

    @ParameterizedTest @ValueSource(strings = {"PUT", "PATCH"})
    void escritura_inexistente_devuelve404(String metodo) throws Exception {
        escribir(metodo, 999, login(Rol.ADMIN, false), cuerpo(metodo)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT"})
    void guardar_nombreDuplicadoEnOtroCaso_devuelve409InclusoInactivo(String metodo) throws Exception {
        Servicio existente = guardar("Corte");
        existente.desactivar(reloj.instant());
        servicios.saveAndFlush(existente);
        Servicio otro = guardar("Barba");
        var anterior = filaServicio(otro.getId());
        escribir(metodo, otro.getId(), login(Rol.ADMIN, false), datos("cOrTe", 30, "15.00"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"));
        assertThat(servicios.count()).isEqualTo(2);
        assertThat(filaServicio(otro.getId())).isEqualTo(anterior);
    }

    @Test void editar_mismoNombreConOtroCaso_noSeConsideraDuplicado() throws Exception {
        Servicio servicio = guardar("Corte");
        escribir("PUT", servicio.getId(), login(Rol.ADMIN, false), datos("CORTE", 30, "15.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("CORTE"));
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT"})
    void guardar_colisionDespuesDeComprobacion_traduceIndiceRealDePostgres(String metodo) throws Exception {
        guardar("Corte");
        Servicio otro = guardar("Barba");
        Cookie sesion = login(Rol.ADMIN, false);
        // Simula la carrera: la comprobación aún no ve el nombre que ya protege el índice.
        // La escritura y la excepción de PostgreSQL son reales; no se simula el error.
        doReturn(Optional.empty()).when(servicios).buscarPorNombre("cOrTe");
        escribir(metodo, otro.getId(), sesion, datos("cOrTe", 30, "15.00"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"))
                .andExpect(jsonPath("$.detail").value("Nombre ya registrado."));
        assertThat(servicios.count()).isEqualTo(2);
        assertThat(servicios.findById(otro.getId()).orElseThrow().getNombre()).isEqualTo("Barba");
    }

    static java.util.stream.Stream<Arguments> invalidos() {
        return java.util.stream.Stream.of(
                Arguments.of("nombre", null), Arguments.of("nombre", ""), Arguments.of("nombre", "A"),
                Arguments.of("nombre", "A".repeat(81)), Arguments.of("nombre", " Corte"),
                Arguments.of("nombre", "Corte "), Arguments.of("nombre", "Corte  clásico"),
                Arguments.of("nombre", "Corte\tclásico"), Arguments.of("nombre", "Corte\u00a0clásico"),
                Arguments.of("descripcion", null), Arguments.of("descripcion", "x".repeat(301)),
                Arguments.of("duracionMin", null), Arguments.of("duracionMin", 0),
                Arguments.of("duracionMin", 190), Arguments.of("duracionMin", 25),
                Arguments.of("precio", null), Arguments.of("precio", new BigDecimal("-0.01")),
                Arguments.of("precio", new BigDecimal("20.123")), Arguments.of("precio", new BigDecimal("1000000")));
    }

    @ParameterizedTest @MethodSource("invalidos")
    void crear_datosInvalidos_devuelve400ConErroresDeCampo(String campo, Object valor) throws Exception {
        Cookie sesion = login(Rol.ADMIN, false);
        var datos = datos("Corte", 30, "20.00");
        datos.put(campo, valor);
        escribir("POST", 0, sesion, datos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[?(@.campo == '" + campo + "')].mensaje").isNotEmpty());
        assertThat(servicios.count()).isZero();
    }

    @Test void editar_duracion25_rechazaSinCambios() throws Exception {
        Servicio servicio = guardar("Corte");
        var anterior = filaServicio(servicio.getId());
        escribir("PUT", servicio.getId(), login(Rol.ADMIN, false), datos("Otro corte", 25, "35.00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("duracionMin"));
        assertThat(filaServicio(servicio.getId())).isEqualTo(anterior);
    }

    @ParameterizedTest @CsvSource({"10,0", "180,999999.99"})
    void crear_limitesValidos_aceptaDuracionYPrecio(int minutos, String precio) throws Exception {
        var datos = datos("Corte", minutos, precio);
        datos.put("descripcion", "");
        escribir("POST", 0, login(Rol.ADMIN, false), datos).andExpect(status().isCreated());
        assertThat(servicios.findAll().getFirst().getPrecio()).isEqualByComparingTo(precio);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"activo\":null}"})
    void estado_ausenteONulo_devuelve400(String cuerpo) throws Exception {
        Servicio servicio = guardar("Corte");
        var anterior = filaServicio(servicio.getId());
        Cookie sesion = login(Rol.ADMIN, false);
        mvc.perform(conCsrf(patch("/api/servicios/{id}/estado", servicio.getId()), sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("activo"));
        assertThat(filaServicio(servicio.getId())).isEqualTo(anterior);
    }

    @Test void cp08_editarPrecioYDuracionYDesactivar_conservaTodaLaReservaExistente() throws Exception {
        var fabrica = new DatosPrueba(usuarios, barberos, servicios);
        Usuario cliente = fabrica.cliente("reserva");
        Barbero barbero = fabrica.barbero("reserva");
        Servicio servicio = guardar("Corte");
        Reserva reserva = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio,
                reloj.instant().plus(Duration.ofDays(1)), EstadoReserva.PENDIENTE, cliente, reloj.instant()));
        var anterior = jdbc.queryForMap("select * from reserva where id = ?", reserva.getId());
        Cookie sesion = login(Rol.ADMIN, false);
        reloj.adelantar(Duration.ofMinutes(5));
        escribir("PUT", servicio.getId(), sesion, datos("Corte nuevo", 60, "99.99")).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select * from reserva where id = ?", reserva.getId())).isEqualTo(anterior);
        reloj.adelantar(Duration.ofMinutes(5));
        escribir("PATCH", servicio.getId(), sesion, Map.of("activo", false)).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select * from reserva where id = ?", reserva.getId())).isEqualTo(anterior);
        Reserva conservada = reservas.findById(reserva.getId()).orElseThrow();
        assertThat(conservada.getPrecioRef()).isEqualByComparingTo("20.00");
        assertThat(conservada.getDuracionRefMin()).isEqualTo((short) 30);
        mvc.perform(get("/api/servicios")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(servicios.count()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {
            "GET /api/servicios/1", "DELETE /api/servicios/1", "POST /api/servicios/1/estado",
            "PUT /api/servicios/1/estado", "PATCH /api/servicios/1", "GET /api/prueba/no-implementada"})
    void rutasNoAutorizadas_seMantienenCerradas(String caso) throws Exception {
        String[] partes = caso.split(" ");
        var solicitud = request(HttpMethod.valueOf(partes[0]), partes[1]);
        mvc.perform(conCsrf(solicitud, login(Rol.ADMIN, false))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    private Servicio guardar(String nombre) {
        return servicios.saveAndFlush(new Servicio(nombre, "Descripción ficticia",
                (short) 30, new BigDecimal("20.00"), reloj.instant()));
    }
    private Map<String, Object> filaServicio(long id) {
        return jdbc.queryForMap("select * from servicio where id = ?", id);
    }
    private Map<String, Object> datos(String nombre, int duracion, String precio) {
        var datos = new HashMap<String, Object>();
        datos.put("nombre", nombre);
        datos.put("descripcion", "Descripción ficticia");
        datos.put("duracionMin", duracion);
        datos.put("precio", new BigDecimal(precio));
        return datos;
    }
    private Object cuerpo(String metodo) {
        return metodo.equals("PATCH") ? Map.of("activo", false) : datos("Otro corte", 40, "25.00");
    }
    private MockHttpServletRequestBuilder solicitud(String metodo, long id) {
        return switch (metodo) {
            case "POST" -> post("/api/servicios");
            case "PUT" -> put("/api/servicios/{id}", id);
            case "PATCH" -> patch("/api/servicios/{id}/estado", id);
            default -> throw new IllegalArgumentException("Método no previsto.");
        };
    }
    private ResultActions escribir(String metodo, long id, Cookie sesion, Object datos) throws Exception {
        return mvc.perform(conCsrf(solicitud(metodo, id), sesion).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos)));
    }
    private Cookie csrf() throws Exception {
        return mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }
    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder solicitud, Cookie sesion) throws Exception {
        Cookie csrf = csrf();
        if (sesion != null) solicitud.cookie(sesion);
        return solicitud.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }
    private Cookie login(Rol rol, boolean temporal) throws Exception {
        String correo = rol.name().toLowerCase(Locale.ROOT) + "@ejemplo.test";
        Usuario usuario = new Usuario("Usuario ficticio", correo, rol == Rol.CLIENTE ? "999111222" : null,
                passwords.encode(PASSWORD), rol, rol == Rol.CLIENTE ? reloj.instant() : null, reloj.instant());
        if (temporal) usuario.cambiarPassword(usuario.getPasswordHash(), true, reloj.instant());
        usuarios.saveAndFlush(usuario);
        if (rol == Rol.BARBERO) barberos.saveAndFlush(new Barbero(usuario, "Cortes", reloj.instant()));
        var resultado = mvc.perform(conCsrf(post("/api/auth/login"), null).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        Cookie sesion = resultado.getResponse().getCookie("BT_SESION");
        assertThat(sesion).isNotNull();
        return sesion;
    }
}
