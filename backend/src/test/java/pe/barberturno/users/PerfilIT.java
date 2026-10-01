package pe.barberturno.users;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.auth.*;
import pe.barberturno.auth.dto.CambiarPasswordDto;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.support.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
class PerfilIT {
    private static final String CORREO = "perfil@ejemplo.test";
    private static final String PASSWORD = "ClavePerfil123";
    private static final String NUEVA = "OtraClave456";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    @Autowired JwtDecoder decoder;
    @Autowired JwtService jwt;
    @Autowired AuthService auth;

    @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
    }
    @AfterEach void limpiar() { LimpiezaBaseDatos.limpiar(jdbc); }

    @ParameterizedTest @EnumSource(Rol.class)
    void perfil_obtenerYActualizar_persisteSoloLosCamposEditables(Rol rol) throws Exception {
        Usuario usuario = crear(rol, false);
        Cookie sesion = sesion(usuario);
        mvc.perform(get("/api/perfil").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usuario.getId()))
                .andExpect(jsonPath("$.nombre").value("Usuario de prueba"))
                .andExpect(jsonPath("$.correo").value(CORREO))
                .andExpect(jsonPath("$.rol").value(rol.name()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist());
        reloj.adelantar(Duration.ofMinutes(1));
        escribir("/api/perfil", sesion, Map.of("nombre", "Nombre actualizado", "telefono", "987654321"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Nombre actualizado"))
                .andExpect(jsonPath("$.telefono").value("987654321"));
        mvc.perform(get("/api/perfil").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre actualizado"))
                .andExpect(jsonPath("$.telefono").value("987654321"));
        Usuario guardado = vigente();
        assertThat(guardado.getActualizadoEn()).isEqualTo(reloj.instant());
        assertThat(guardado.getCreadoEn()).isEqualTo(usuario.getCreadoEn());
        assertThat(guardado.getCorreo()).isEqualTo(CORREO);
        assertThat(guardado.getPasswordHash()).isEqualTo(usuario.getPasswordHash());
        assertThat(guardado.getTokenVersion()).isEqualTo(usuario.getTokenVersion());
        assertThat(guardado.getRol()).isEqualTo(rol);
        assertThat(guardado.getPrivacidadAceptadaEn()).isEqualTo(usuario.getPrivacidadAceptadaEn());
    }

    @ParameterizedTest @EnumSource(value = Rol.class, names = {"BARBERO", "ADMIN"})
    void perfil_personal_admiteTelefonoNuloYOmitido(Rol rol) throws Exception {
        Cookie sesion = sesion(crear(rol, false));
        escribir("/api/perfil", sesion, Map.of("nombre", "Personal actualizado", "telefono", "987654321"))
                .andExpect(status().isOk());
        var datos = new HashMap<String, Object>();
        datos.put("nombre", "Personal actualizado");
        datos.put("telefono", null);
        escribir("/api/perfil", sesion, datos).andExpect(status().isOk());
        assertThat(vigente().getTelefono()).isNull();
        datos.remove("telefono");
        escribir("/api/perfil", sesion, datos).andExpect(status().isOk());
        assertThat(vigente().getTelefono()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"", "123", "12345678a", "1234567890", " 987654321", "９８７６５４３２１"})
    void perfil_telefonoInvalido_rechazaSinCambiarDatos(String telefono) throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        escribir("/api/perfil", sesion(usuario), Map.of("nombre", "Nombre nuevo", "telefono", telefono))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("telefono"));
        comprobarSinCambios(usuario);
    }

    @ParameterizedTest @ValueSource(strings = {"nulo", "omitido", "vacio"})
    void perfil_clienteSinTelefono_rechazaSinLlegarAlCheckSQL(String caso) throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        var datos = new HashMap<String, Object>();
        datos.put("nombre", "Nombre nuevo");
        if (caso.equals("nulo")) datos.put("telefono", null);
        if (caso.equals("vacio")) datos.put("telefono", "");
        escribir("/api/perfil", sesion(usuario), datos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("telefono"));
        comprobarSinCambios(usuario);
    }

    @ParameterizedTest @ValueSource(strings = {"", "A", " Nombre", "Nombre ", "Nombre  doble", "Nombre\tNuevo", "Nombre\nNuevo", "Nombre Nuevo"})
    void perfil_nombreConEspaciosSobrantesOCorto_rechaza(String nombre) throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        escribir("/api/perfil", sesion(usuario), Map.of("nombre", nombre, "telefono", "987654321"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo").value(org.hamcrest.Matchers.hasItem("nombre")));
        comprobarSinCambios(usuario);
    }

    @Test void perfil_nombreEnLosLimites_aceptaYRechazaElExceso() throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        Cookie sesion = sesion(usuario);
        for (String nombre : List.of("Ab", "A".repeat(100))) {
            escribir("/api/perfil", sesion, Map.of("nombre", nombre, "telefono", "987654321"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value(nombre));
        }
        escribir("/api/perfil", sesion, Map.of("nombre", "A".repeat(101), "telefono", "987654321"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(vigente().getNombre()).hasSize(100);
    }

    @ParameterizedTest @EnumSource(Rol.class)
    void perfil_jsonIncluyeCorreoYCamposDeAcceso_losIgnora(Rol rol) throws Exception {
        Usuario usuario = crear(rol, false);
        escribir("/api/perfil", sesion(usuario), Map.of("nombre", "Nombre nuevo", "telefono", "987654321",
                "correo", "otro@ejemplo.test", "rol", "ADMIN", "tokenVersion", 20,
                "passwordHash", "valor-ignorado", "debeCambiarPassword", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(CORREO))
                .andExpect(jsonPath("$.rol").value(rol.name()));
        assertThat(vigente().getCorreo()).isEqualTo(CORREO);
        assertThat(vigente().getRol()).isEqualTo(rol);
        assertThat(vigente().getTokenVersion()).isEqualTo(usuario.getTokenVersion());
        assertThat(vigente().getPasswordHash()).isEqualTo(usuario.getPasswordHash());
        assertThat(vigente().isDebeCambiarPassword()).isFalse();
    }

    @Test void perfilYPassword_sinSesion_devuelven401ConCsrfValido() throws Exception {
        mvc.perform(get("/api/perfil")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        for (String ruta : List.of("/api/perfil", "/api/auth/password")) {
            escribir(ruta, null, Map.of("nombre", "Nombre nuevo", "telefono", "987654321",
                    "passwordActual", PASSWORD, "passwordNueva", NUEVA))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"/api/perfil", "/api/auth/password"})
    void escrituras_csrfAusenteOIncorrecto_rechazanAunqueHaySesion(String ruta) throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        mvc.perform(put(ruta).cookie(sesion(usuario)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nombre", "Nombre nuevo", "telefono", "987654321",
                        "passwordActual", PASSWORD, "passwordNueva", NUEVA))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        Cookie csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(put(ruta).cookie(sesion(usuario), csrf).header("X-XSRF-TOKEN", "incorrecto")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("nombre", "Nombre nuevo", "telefono", "987654321",
                                "passwordActual", PASSWORD, "passwordNueva", NUEVA))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        comprobarSinCambios(usuario);
    }

    @Test void passwordTemporal_permiteLeerPerfilYCambiarPeroNoActualizarPerfil() throws Exception {
        Usuario usuario = crear(Rol.BARBERO, true);
        Cookie antigua = sesion(usuario);
        mvc.perform(get("/api/perfil").cookie(antigua)).andExpect(status().isOk());
        escribir("/api/perfil", antigua, Map.of("nombre", "Nombre nuevo", "telefono", "987654321"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
        comprobarSinCambios(usuario);
        Cookie nueva = cookieSesion(cambiar(antigua, PASSWORD, NUEVA).andExpect(status().isNoContent()).andReturn());
        mvc.perform(get("/api/auth/sesion").cookie(nueva)).andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(false));
        escribir("/api/perfil", nueva, Map.of("nombre", "Nombre nuevo", "telefono", "987654321"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/perfil").cookie(antigua)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @EnumSource(Rol.class)
    void cp17_cambioValido_revocaAmbasCookiesAntiguasYRenuevaLaSesionActual(Rol rol) throws Exception {
        Usuario usuario = crear(rol, false);
        Cookie sesionA = cookieSesion(login(PASSWORD).andExpect(status().isOk()).andReturn());
        reloj.adelantar(Duration.ofSeconds(1));
        Cookie sesionB = cookieSesion(login(PASSWORD).andExpect(status().isOk()).andReturn());
        assertThat(sesionA.getValue()).isNotEqualTo(sesionB.getValue());
        reloj.adelantar(Duration.ofMinutes(1));
        var respuesta = cambiar(sesionA, PASSWORD, NUEVA).andExpect(status().isNoContent())
                .andExpect(content().string("")).andReturn();
        Cookie nuevaA = cookieSesion(respuesta);
        assertThat(nuevaA.isHttpOnly()).isTrue();
        assertThat(nuevaA.getSecure()).isFalse();
        assertThat(nuevaA.getPath()).isEqualTo("/");
        assertThat(nuevaA.getMaxAge()).isEqualTo(28800);
        assertThat(nuevaA.getAttribute("SameSite")).isEqualTo("Strict");
        var token = decoder.decode(nuevaA.getValue());
        assertThat(((Number) token.getClaim("tv")).intValue()).isEqualTo(usuario.getTokenVersion() + 1);
        assertThat(token.getIssuedAt()).isEqualTo(reloj.instant());
        assertThat(token.getExpiresAt()).isEqualTo(reloj.instant().plus(Duration.ofHours(8)));
        Usuario guardado = vigente();
        assertThat(guardado.getPasswordHash()).startsWith("$2a$12$").isNotEqualTo(usuario.getPasswordHash());
        assertThat(passwords.matches(NUEVA, guardado.getPasswordHash())).isTrue();
        assertThat(passwords.matches(PASSWORD, guardado.getPasswordHash())).isFalse();
        assertThat(guardado.getActualizadoEn()).isEqualTo(reloj.instant());
        assertThat(guardado.isDebeCambiarPassword()).isFalse();
        for (Cookie antigua : List.of(sesionA, sesionB)) {
            mvc.perform(get("/api/perfil").cookie(antigua)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        }
        mvc.perform(get("/api/perfil").cookie(nuevaA)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/sesion").cookie(nuevaA)).andExpect(status().isOk());
        login(NUEVA).andExpect(status().isOk());
        login(PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test void password_actualIncorrecta_rechazaSinContarIntentosNiCambiarDatos() throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        usuario.registrarIntentoFallido(null, reloj.instant());
        usuarios.saveAndFlush(usuario);
        Cookie sesion = sesion(usuario);
        reloj.adelantar(Duration.ofMinutes(1));
        for (int i = 0; i < 6; i++) {
            var respuesta = cambiar(sesion, "Incorrecta123", NUEVA).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.errores[0].campo").value("passwordActual"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("No se pudo verificar la contraseña actual."))
                    .andReturn();
            assertThat(respuesta.getResponse().getCookie("BT_SESION")).isNull();
            assertThat(respuesta.getResponse().getContentAsString()).doesNotContain("Incorrecta123", NUEVA);
        }
        comprobarSinCambios(usuario);
        assertThat(vigente().getIntentosFallidos()).isEqualTo((short) 1);
        assertThat(vigente().getBloqueadoHasta()).isNull();
        mvc.perform(get("/api/perfil").cookie(sesion)).andExpect(status().isOk());
    }

    @ParameterizedTest @CsvSource({"'',3", "corta,2", "abcdefgh,1", "12345678,1", "larga,2", "utf8,1", "nula,1"})
    void password_nuevaDebil_devuelveTodosLosIncumplimientosSinCambios(String caso, int cantidad) throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        String nueva = switch (caso) {
            case "larga" -> "a".repeat(72) + "1";
            case "utf8" -> "á".repeat(36) + "1";
            case "nula" -> null;
            default -> caso;
        };
        var resultado = cambiar(sesion(usuario), PASSWORD, nueva).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("passwordNueva")))).andReturn();
        var errores = json.readTree(resultado.getResponse().getContentAsString()).get("errores");
        assertThat(errores.size()).isEqualTo(cantidad);
        comprobarSinCambios(usuario);
    }

    @Test void password_nuevaIgualALaActual_rechazaSinRevocar() throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        Cookie sesion = sesion(usuario);
        cambiar(sesion, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("passwordNueva"));
        comprobarSinCambios(usuario);
        mvc.perform(get("/api/perfil").cookie(sesion)).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"omitida", "nula", "vacia"})
    void password_actualAusente_rechazaPorCampo(String caso) throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        var datos = new HashMap<String, Object>();
        datos.put("passwordNueva", NUEVA);
        if (caso.equals("nula")) datos.put("passwordActual", null);
        if (caso.equals("vacia")) datos.put("passwordActual", "");
        escribir("/api/auth/password", sesion(usuario), datos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("passwordActual"));
        comprobarSinCambios(usuario);
    }

    @Test void password_dosCambiosConcurrentes_soloUnoAceptaLaCredencialAnterior() throws Exception {
        Usuario usuario = crear(Rol.CLIENTE, false);
        var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var inicio = new java.util.concurrent.CountDownLatch(1);
        try {
            var resultados = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                resultados.add(ejecutor.submit(() -> {
                    inicio.await();
                    try {
                        auth.cambiarPassword(usuario.getId(), new CambiarPasswordDto(PASSWORD, NUEVA));
                        return true;
                    } catch (NegocioException error) {
                        assertThat(error.codigo().name()).isEqualTo("VALIDACION");
                        assertThat(error.errores().getFirst().campo()).isEqualTo("passwordActual");
                        return false;
                    }
                }));
            }
            inicio.countDown();
            var aceptados = new ArrayList<Boolean>();
            for (var resultado : resultados) aceptados.add(resultado.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(aceptados).containsExactlyInAnyOrder(true, false);
            assertThat(vigente().getTokenVersion()).isEqualTo(usuario.getTokenVersion() + 1);
            assertThat(passwords.matches(NUEVA, vigente().getPasswordHash())).isTrue();
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            assertThat(ejecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private Usuario crear(Rol rol, boolean temporal) {
        Usuario usuario = new Usuario("Usuario de prueba", CORREO, rol == Rol.CLIENTE ? "999111222" : null,
                passwords.encode(PASSWORD), rol, rol == Rol.CLIENTE ? reloj.instant() : null, reloj.instant());
        if (temporal) usuario.cambiarPassword(usuario.getPasswordHash(), true, reloj.instant());
        return usuarios.saveAndFlush(usuario);
    }
    private Usuario vigente() { return usuarios.findByCorreo(CORREO).orElseThrow(); }
    private Cookie sesion(Usuario usuario) { return new Cookie("BT_SESION", jwt.emitir(usuario)); }
    private Cookie cookieSesion(MvcResult resultado) {
        Cookie cookie = resultado.getResponse().getCookie("BT_SESION");
        assertThat(cookie).isNotNull();
        return cookie;
    }
    private void comprobarSinCambios(Usuario anterior) {
        Usuario guardado = vigente();
        assertThat(guardado.getNombre()).isEqualTo(anterior.getNombre());
        assertThat(guardado.getTelefono()).isEqualTo(anterior.getTelefono());
        assertThat(guardado.getCorreo()).isEqualTo(anterior.getCorreo());
        assertThat(guardado.getPasswordHash()).isEqualTo(anterior.getPasswordHash());
        assertThat(guardado.getTokenVersion()).isEqualTo(anterior.getTokenVersion());
        assertThat(guardado.isDebeCambiarPassword()).isEqualTo(anterior.isDebeCambiarPassword());
        assertThat(guardado.getIntentosFallidos()).isEqualTo(anterior.getIntentosFallidos());
        assertThat(guardado.getBloqueadoHasta()).isEqualTo(anterior.getBloqueadoHasta());
        assertThat(guardado.getActualizadoEn()).isEqualTo(anterior.getActualizadoEn());
    }
    private ResultActions cambiar(Cookie sesion, String actual, String nueva) throws Exception {
        var datos = new HashMap<String, Object>();
        datos.put("passwordActual", actual);
        datos.put("passwordNueva", nueva);
        return escribir("/api/auth/password", sesion, datos);
    }
    private ResultActions escribir(String ruta, Cookie sesion, Object datos) throws Exception {
        return mvc.perform(conCsrf(put(ruta), sesion).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos)));
    }
    private ResultActions login(String password) throws Exception {
        return mvc.perform(conCsrf(post("/api/auth/login"), null).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", CORREO, "password", password))));
    }
    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder solicitud, Cookie sesion) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        if (sesion != null) solicitud.cookie(sesion);
        return solicitud.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }
}