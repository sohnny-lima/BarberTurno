package pe.barberturno.scheduling;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.reservations.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
class BarberoIT {
    private static final String PASSWORD = "ClavePersonal123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired BarberoRepository barberos;
    @Autowired UsuarioRepository usuarios;
    @Autowired ReservaRepository reservas;
    @Autowired ServicioRepository servicios;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    private String hash;

    @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
        hash = passwords.encode(PASSWORD);
    }
    @AfterEach void limpiar() { LimpiezaBaseDatos.limpiar(jdbc); }

    @Test void crear_cuentaTemporal_cambiaPasswordYAccedeNormalmente() throws Exception {
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        var datos = datos("Profesional ficticio", "NUEVO@EJEMPLO.TEST");
        datos.put("telefono", "999111222");
        long id = id(escribir("POST", 0, admin, datos).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Profesional ficticio"))
                .andExpect(jsonPath("$.correo").value("nuevo@ejemplo.test"))
                .andExpect(jsonPath("$.telefono").value("999111222"))
                .andExpect(jsonPath("$.activo").value(true)).andReturn());
        Usuario u = usuarios.findByCorreo("nuevo@ejemplo.test").orElseThrow();
        assertThat(u.getRol()).isEqualTo(Rol.BARBERO);
        assertThat(u.isDebeCambiarPassword()).isTrue();
        assertThat(u.getPrivacidadAceptadaEn()).isNull();
        assertThat(u.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwords.matches(PASSWORD, u.getPasswordHash())).isTrue();
        assertThat(u.getCreadoEn()).isEqualTo(reloj.instant());
        Cookie temporal = login(u);
        mvc.perform(get("/api/auth/sesion").cookie(temporal)).andExpect(status().isOk())
                .andExpect(jsonPath("$.barberoId").value(id))
                .andExpect(jsonPath("$.debeCambiarPassword").value(true));
        mvc.perform(get("/api/barberos").cookie(temporal)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
        var cambio = mvc.perform(conCsrf(put("/api/auth/password"), temporal)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("passwordActual", PASSWORD, "passwordNueva", "NuevaPersonal456"))))
                .andExpect(status().isNoContent()).andReturn();
        Cookie nueva = cambio.getResponse().getCookie("BT_SESION");
        mvc.perform(get("/api/auth/sesion").cookie(temporal)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/sesion").cookie(nueva)).andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(false));
        mvc.perform(get("/api/barberos").cookie(nueva)).andExpect(status().isOk());
    }

    @Test void editar_desactivarYReactivar_conservaCuentaEHistorialYRevocaSesion() throws Exception {
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        Barbero b = guardar("profesional");
        Usuario u = usuarios.findByCorreo("profesional@ejemplo.test").orElseThrow();
        Cookie anterior = login(u);
        reloj.adelantar(Duration.ofMinutes(1));
        escribir("PUT", b.getId(), admin, Map.of("nombre", "Nombre editado", "telefono", "987654321", "especialidad", "Barba"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Nombre editado"))
                .andExpect(jsonPath("$.especialidad").value("Barba"));
        Usuario editado = usuarios.findById(u.getId()).orElseThrow();
        assertThat(editado.getNombre()).isEqualTo("Nombre editado");
        assertThat(editado.getTelefono()).isEqualTo("987654321");
        assertThat(editado.getCorreo()).isEqualTo(u.getCorreo());
        assertThat(editado.getActualizadoEn()).isEqualTo(reloj.instant());
        assertThat(barberos.findById(b.getId()).orElseThrow().getActualizadoEn()).isEqualTo(reloj.instant());
        escribir("PATCH", b.getId(), admin, Map.of("activo", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.barbero.activo").value(false))
                .andExpect(jsonPath("$.reservasFuturasVigentes").value(0));
        Usuario inactivo = usuarios.findById(u.getId()).orElseThrow();
        assertThat(inactivo.isActivo()).isFalse();
        assertThat(inactivo.getTokenVersion()).isEqualTo(u.getTokenVersion() + 1);
        mvc.perform(get("/api/auth/sesion").cookie(anterior)).andExpect(status().isUnauthorized());
        solicitarLogin(u.getCorreo(), PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/barberos")).andExpect(jsonPath("$.length()").value(0));
        escribir("PATCH", b.getId(), admin, Map.of("activo", false)).andExpect(status().isOk());
        assertThat(usuarios.findById(u.getId()).orElseThrow().getTokenVersion()).isEqualTo(inactivo.getTokenVersion());
        escribir("PUT", b.getId(), admin, Map.of("nombre", "Perfil inactivo", "especialidad", "Cortes"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        assertThat(usuarios.findById(u.getId()).orElseThrow().getTelefono()).isNull();
        escribir("PATCH", b.getId(), admin, Map.of("activo", true)).andExpect(status().isOk());
        assertThat(usuarios.findById(u.getId()).orElseThrow().isActivo()).isTrue();
        Cookie restaurada = login(u);
        mvc.perform(get("/api/auth/sesion").cookie(restaurada)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/sesion").cookie(anterior)).andExpect(status().isUnauthorized());
        assertThat(barberos.count()).isEqualTo(1);
        assertThat(usuarios.count()).isEqualTo(2);
    }

    @Test void vincular_adminSesionExistente_muestraPerfilYConservaAccesoAlDesactivar() throws Exception {
        Usuario admin = crearUsuario("admin", Rol.ADMIN);
        Cookie sesion = login(admin);
        long id = id(escribir("POST", 0, sesion, Map.of("usuarioId", admin.getId(), "especialidad", "Cortes"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value(admin.getNombre())).andReturn());
        assertThat(usuarios.count()).isEqualTo(1);
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN")).andExpect(jsonPath("$.barberoId").value(id));
        escribir("PATCH", id, sesion, Map.of("activo", false)).andExpect(status().isOk());
        Usuario conservado = usuarios.findById(admin.getId()).orElseThrow();
        assertThat(conservado.isActivo()).isTrue();
        assertThat(conservado.getTokenVersion()).isEqualTo(admin.getTokenVersion());
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.barberoId").value(id));
        login(admin);
        escribir("PATCH", id, sesion, Map.of("activo", true)).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"PUBLICO", "CLIENTE", "BARBERO", "ADMIN"})
    void listado_contactosEInactivosSoloAdmin_ordenadoPorNombre(String actor) throws Exception {
        Barbero z = guardar("Zeta");
        guardar("alfa");
        guardar("Beta");
        z.desactivar(reloj.instant());
        barberos.saveAndFlush(z);
        Cookie sesion = actor.equals("PUBLICO") ? null : login(crearUsuario("actor", Rol.valueOf(actor)));
        for (boolean incluir : List.of(false, true)) {
            var solicitud = get("/api/barberos").param("incluirInactivos", Boolean.toString(incluir));
            if (sesion != null) solicitud.cookie(sesion);
            var resultado = mvc.perform(solicitud).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(incluir && actor.equals("ADMIN") ? 3 : 2))
                    .andExpect(jsonPath("$[0].nombre").value("alfa"))
                    .andExpect(jsonPath("$[1].nombre").value("Beta")).andReturn();
            var lista = json.readTree(resultado.getResponse().getContentAsString());
            for (var perfil : lista) {
                assertThat(perfil.has("correo")).isEqualTo(actor.equals("ADMIN"));
                assertThat(perfil.has("telefono")).isEqualTo(actor.equals("ADMIN"));
                assertThat(perfil.size()).isEqualTo(actor.equals("ADMIN") ? 6 : 4);
            }
        }
    }

    @Test void limite_onceActivos_rechazaAltaYReactivacionPeroPermiteRepetirActivo() throws Exception {
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        Barbero inactivo = guardar("inactivo");
        escribir("PATCH", inactivo.getId(), admin, Map.of("activo", false)).andExpect(status().isOk());
        for (int i = 0; i < 10; i++) guardar("activo" + i);
        escribir("POST", 0, admin, datos("Nuevo ficticio", "nuevo@ejemplo.test"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("LIMITE_BARBEROS_ACTIVOS"));
        escribir("PATCH", inactivo.getId(), admin, Map.of("activo", true))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("LIMITE_BARBEROS_ACTIVOS"));
        assertThat(usuarios.findByCorreo("nuevo@ejemplo.test")).isEmpty();
        assertThat(barberos.countByActivoTrue()).isEqualTo(10);
        assertThat(usuarios.findByCorreo("inactivo@ejemplo.test").orElseThrow().isActivo()).isFalse();
        long activo = barberos.listar(false).getFirst().getId();
        escribir("PATCH", activo, admin, Map.of("activo", true)).andExpect(status().isOk());
        escribir("PATCH", activo, admin, Map.of("activo", false)).andExpect(status().isOk());
        escribir("PATCH", inactivo.getId(), admin, Map.of("activo", true)).andExpect(status().isOk());
        assertThat(barberos.countByActivoTrue()).isEqualTo(10);
    }

    @ParameterizedTest @ValueSource(strings = {"altas", "reactivaciones", "mixto"})
    void limite_dosOperacionesConcurrentesConNueveActivos_soloUnaTieneExito(String caso) throws Exception {
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        for (int i = 0; i < 9; i++) guardar("activo" + i);
        Barbero a = guardar("inactivoA");
        Barbero b = guardar("inactivoB");
        a.desactivar(reloj.instant()); b.desactivar(reloj.instant());
        barberos.saveAndFlush(a); barberos.saveAndFlush(b);
        Cookie csrfA = csrf(); Cookie csrfB = csrf();
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<MvcResult>> futuros = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int indice = i;
                futuros.add(executor.submit(() -> {
                    preparados.countDown();
                    if (!salida.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó la carrera.");
                    boolean alta = caso.equals("altas") || (caso.equals("mixto") && indice == 0);
                    var solicitud = alta ? post("/api/barberos") : patch("/api/barberos/{id}/estado", indice == 0 ? a.getId() : b.getId());
                    Cookie token = indice == 0 ? csrfA : csrfB;
                    Object cuerpo = alta ? datos("Concurrente ficticio", "concurrente" + indice + "@ejemplo.test") : Map.of("activo", true);
                    return mvc.perform(solicitud.cookie(admin, token).header("X-XSRF-TOKEN", token.getValue())
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo))).andReturn();
                }));
            }
            try {
                assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue();
            } finally { salida.countDown(); }
            List<Integer> estados = new ArrayList<>();
            for (var futuro : futuros) {
                var resultado = futuro.get(20, TimeUnit.SECONDS);
                int estado = resultado.getResponse().getStatus();
                estados.add(estado);
                if (estado == 422) assertThat(json.readTree(resultado.getResponse().getContentAsString()).get("codigo").asString())
                        .isEqualTo("LIMITE_BARBEROS_ACTIVOS");
            }
            assertThat(estados.stream().filter(s -> s == 200 || s == 201).count()).isEqualTo(1);
            assertThat(estados.stream().filter(s -> s == 422).count()).isEqualTo(1);
        }
        assertThat(barberos.countByActivoTrue()).isEqualTo(10);
        assertThat(usuarios.count()).isEqualTo(barberos.count() + 1);
    }

    @Test void cp08_reservasFuturasVigentes_contadorCorrectoYFilasIntactas() throws Exception {
        var fabrica = new DatosPrueba(usuarios, barberos, servicios);
        Usuario cliente = fabrica.cliente("cliente");
        Barbero b = guardar("profesional");
        var servicio = fabrica.servicio();
        for (int i = 0; i < 5; i++) {
            EstadoReserva estado = List.of(EstadoReserva.PENDIENTE, EstadoReserva.CANCELADA,
                    EstadoReserva.CONFIRMADA, EstadoReserva.NO_ASISTIO, EstadoReserva.COMPLETADA).get(i);
            var inicio = reloj.instant().plus(Duration.ofDays(i == 2 ? -1 : i + 1));
            reservas.saveAndFlush(new Reserva(cliente, b, servicio, inicio, estado, cliente, reloj.instant()));
        }
        reservas.saveAndFlush(new Reserva(cliente, b, servicio, reloj.instant(), EstadoReserva.EN_ATENCION, cliente, reloj.instant()));
        var antes = jdbc.queryForList("select * from reserva order by id");
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        escribir("PUT", b.getId(), admin, Map.of("nombre", "Nuevo nombre", "especialidad", "Barbas"))
                .andExpect(status().isOk());
        for (boolean activo : List.of(false, true)) {
            escribir("PATCH", b.getId(), admin, Map.of("activo", activo)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservasFuturasVigentes").value(3));
            assertThat(jdbc.queryForList("select * from reserva order by id")).isEqualTo(antes);
        }
    }

    @ParameterizedTest @CsvSource({"POST,CLIENTE", "PUT,CLIENTE", "PATCH,CLIENTE", "POST,BARBERO", "PUT,BARBERO", "PATCH,BARBERO"})
    void escritura_rolNoAutorizado_devuelve403SinCambios(String metodo, Rol rol) throws Exception {
        Barbero b = guardar("objetivo");
        Cookie sesion = login(crearUsuario("actor", rol));
        var anterior = filas();
        escribir(metodo, b.getId(), sesion, cuerpo(metodo)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(filas()).isEqualTo(anterior);
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void escritura_sinSesionConCsrf_devuelve401(String metodo) throws Exception {
        Barbero b = guardar("objetivo");
        escribir(metodo, b.getId(), null, cuerpo(metodo)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @CsvSource({"POST,ausente", "PUT,ausente", "PATCH,ausente", "POST,incorrecto", "PUT,incorrecto", "PATCH,incorrecto"})
    void escritura_sesionValidaSinCsrfOCsrfIncorrecto_devuelve403(String metodo, String caso) throws Exception {
        Barbero b = guardar("objetivo");
        Cookie sesion = login(crearUsuario("admin", Rol.ADMIN));
        var anterior = filas();
        var solicitud = solicitud(metodo, b.getId()).cookie(sesion).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo(metodo)));
        if (caso.equals("incorrecto")) solicitud.cookie(csrf()).header("X-XSRF-TOKEN", "incorrecto");
        mvc.perform(solicitud).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(filas()).isEqualTo(anterior);
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void escritura_passwordTemporal_devuelve403(String metodo) throws Exception {
        Barbero b = guardar("objetivo");
        Usuario u = crearUsuario("admin", Rol.ADMIN);
        u.cambiarPassword(hash, true, reloj.instant()); usuarios.saveAndFlush(u);
        escribir(metodo, b.getId(), login(u), cuerpo(metodo)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }

    @ParameterizedTest @ValueSource(strings = {"PUT", "PATCH"})
    void escritura_inexistente_devuelve404(String metodo) throws Exception {
        escribir(metodo, 999, login(crearUsuario("admin", Rol.ADMIN)), cuerpo(metodo))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test void crear_correoDuplicadoInclusoInactivo_rechazaSinCrearPerfil() throws Exception {
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        Usuario u = crearUsuario("existente", Rol.CLIENTE);
        u.desactivar(reloj.instant()); usuarios.saveAndFlush(u);
        escribir("POST", 0, admin, datos("Nuevo ficticio", "EXISTENTE@EJEMPLO.TEST"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
        assertThat(barberos.count()).isZero();
        assertThat(usuarios.count()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"CLIENTE", "BARBERO", "ADMIN_INACTIVO", "ADMIN_VINCULADO", "ADMIN_VINCULADO_INACTIVO"})
    void vincular_usuarioIncompatible_devuelve409(String caso) throws Exception {
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        Usuario u = crearUsuario("vinculo", caso.startsWith("ADMIN") ? Rol.ADMIN : Rol.valueOf(caso));
        if (caso.equals("ADMIN_INACTIVO")) { u.desactivar(reloj.instant()); usuarios.saveAndFlush(u); }
        if (caso.startsWith("ADMIN_VINCULADO")) {
            Barbero perfil = new Barbero(u, "Cortes", reloj.instant());
            if (caso.endsWith("INACTIVO")) perfil.desactivar(reloj.instant());
            barberos.saveAndFlush(perfil);
        }
        long cantidad = barberos.count();
        escribir("POST", 0, admin, Map.of("usuarioId", u.getId(), "especialidad", "Cortes"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLICTO"));
        assertThat(barberos.count()).isEqualTo(cantidad);
    }

    @Test void vincular_usuarioInexistente_devuelve404() throws Exception {
        escribir("POST", 0, login(crearUsuario("admin", Rol.ADMIN)), Map.of("usuarioId", 999, "especialidad", "Cortes"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"nombre", "correo", "telefono", "passwordTemporal"})
    void crear_variantesMezcladas_rechaza400(String campo) throws Exception {
        Usuario admin = crearUsuario("admin", Rol.ADMIN);
        var datos = new HashMap<String, Object>(Map.of("usuarioId", admin.getId(), "especialidad", "Cortes"));
        datos.put(campo, datos("Nombre ficticio", "nuevo@ejemplo.test").getOrDefault(campo, "987654321"));
        escribir("POST", 0, login(admin), datos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(barberos.count()).isZero();
    }

    static java.util.stream.Stream<Arguments> invalidos() {
        return java.util.stream.Stream.of(
                Arguments.of("nombre", null), Arguments.of("nombre", ""), Arguments.of("nombre", "A"),
                Arguments.of("nombre", "A".repeat(101)), Arguments.of("nombre", " Nombre"),
                Arguments.of("nombre", "Nombre  doble"), Arguments.of("correo", null), Arguments.of("correo", ""),
                Arguments.of("correo", "sin-arroba"), Arguments.of("correo", "x".repeat(244) + "@ejemplo.test"),
                Arguments.of("telefono", ""), Arguments.of("telefono", "12345678"), Arguments.of("telefono", "12345678a"),
                Arguments.of("especialidad", null), Arguments.of("especialidad", " "), Arguments.of("especialidad", "x".repeat(101)),
                Arguments.of("passwordTemporal", null), Arguments.of("passwordTemporal", ""), Arguments.of("passwordTemporal", "Abc123"),
                Arguments.of("passwordTemporal", "abcdefgh"), Arguments.of("passwordTemporal", "12345678"),
                Arguments.of("passwordTemporal", "a1".repeat(37)), Arguments.of("passwordTemporal", "á".repeat(36) + "1"),
                Arguments.of("usuarioId", 0), Arguments.of("usuarioId", -1));
    }

    @ParameterizedTest @MethodSource("invalidos")
    void crear_datosInvalidos_rechazaSinPersistir(String campo, Object valor) throws Exception {
        Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        var datos = datos("Nombre ficticio", "nuevo@ejemplo.test"); datos.put(campo, valor);
        escribir("POST", 0, admin, datos).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[?(@.campo == '" + campo + "')].mensaje").isNotEmpty());
        assertThat(barberos.count()).isZero(); assertThat(usuarios.count()).isEqualTo(1);
    }

    @ParameterizedTest @CsvSource({"nombre,", "nombre,A", "nombre,' Nombre'", "telefono,123", "especialidad,"})
    void editar_datosInvalidos_rechazaSinCambios(String campo, String valor) throws Exception {
        Barbero b = guardar("objetivo"); Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        var anterior = filas();
        var datos = new HashMap<String, Object>(Map.of("nombre", "Nombre ficticio", "especialidad", "Cortes"));
        datos.put(campo, valor);
        escribir("PUT", b.getId(), admin, datos).andExpect(status().isBadRequest());
        assertThat(filas()).isEqualTo(anterior);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"activo\":null}"})
    void estado_ausenteONulo_rechaza400(String cuerpo) throws Exception {
        Barbero b = guardar("objetivo"); Cookie admin = login(crearUsuario("admin", Rol.ADMIN));
        mvc.perform(conCsrf(patch("/api/barberos/{id}/estado", b.getId()), admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("activo"));
    }

    @ParameterizedTest @ValueSource(strings = {"GET /api/barberos/1", "DELETE /api/barberos/1", "POST /api/barberos/1/estado", "PUT /api/barberos/1/estado", "PATCH /api/barberos/1", "POST /api/disponibilidad"})
    void rutasNoImplementadas_permanecenCerradas(String caso) throws Exception {
        String[] partes = caso.split(" ");
        mvc.perform(conCsrf(request(HttpMethod.valueOf(partes[0]), partes[1]), login(crearUsuario("admin", Rol.ADMIN))))
                .andExpect(status().isForbidden());
    }

    private Usuario crearUsuario(String identificador, Rol rol) {
        return usuarios.saveAndFlush(new Usuario(identificador, identificador.toLowerCase(Locale.ROOT) + "@ejemplo.test", "999111222",
                hash, rol, rol == Rol.CLIENTE ? reloj.instant() : null, reloj.instant()));
    }
    private Barbero guardar(String identificador) {
        return barberos.saveAndFlush(new Barbero(crearUsuario(identificador, Rol.BARBERO), "Cortes", reloj.instant()));
    }
    private List<?> filas() {
        return List.of(jdbc.queryForList("select * from usuario order by id"), jdbc.queryForList("select * from barbero order by id"));
    }
    private Map<String, Object> datos(String nombre, String correo) {
        return new HashMap<>(Map.of("nombre", nombre, "correo", correo, "especialidad", "Cortes", "passwordTemporal", PASSWORD));
    }
    private Object cuerpo(String metodo) {
        return switch (metodo) {
            case "POST" -> datos("Nombre ficticio", "nuevo@ejemplo.test");
            case "PUT" -> Map.of("nombre", "Nombre ficticio", "especialidad", "Barbas");
            default -> Map.of("activo", false);
        };
    }
    private long id(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString()).get("id").longValue();
    }
    private MockHttpServletRequestBuilder solicitud(String metodo, long id) {
        return switch (metodo) {
            case "POST" -> post("/api/barberos");
            case "PUT" -> put("/api/barberos/{id}", id);
            case "PATCH" -> patch("/api/barberos/{id}/estado", id);
            default -> throw new IllegalArgumentException("Método no previsto.");
        };
    }
    private ResultActions escribir(String metodo, long id, Cookie sesion, Object datos) throws Exception {
        return mvc.perform(conCsrf(solicitud(metodo, id), sesion).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos)));
    }
    private Cookie csrf() throws Exception {
        return mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }
    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder solicitud, Cookie sesion) throws Exception {
        Cookie token = csrf();
        if (sesion != null) solicitud.cookie(sesion);
        return solicitud.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }
    private ResultActions solicitarLogin(String correo, String password) throws Exception {
        return mvc.perform(conCsrf(post("/api/auth/login"), null).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "password", password))));
    }
    private Cookie login(Usuario usuario) throws Exception {
        return solicitarLogin(usuario.getCorreo(), PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("BT_SESION");
    }
}
