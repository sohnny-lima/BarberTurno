package pe.barberturno.auth;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.scheduling.*;
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
class AuthIT {
    private static final String CORREO = "cliente@ejemplo.test";
    private static final String PASSWORD = "ClaveCliente123";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired BarberoRepository barberos;
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

    @Test void sesion_sinCookie_devuelve401YEmiteXsrfLegible() throws Exception {
        var r = mvc.perform(get("/api/auth/sesion")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.instance").value("/api/auth/sesion")).andReturn();
        var xsrf = r.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();
        assertThat(xsrf.getSecure()).isFalse();
        assertThat(xsrf.getPath()).isEqualTo("/");
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(r.getRequest().getSession(false)).isNull();
    }

    @Test void registro_valido_normalizaConsentimientoHashYClaimsSinExponerDatos() throws Exception {
        var r = registrar(registro("CLIENTE@EJEMPLO.TEST")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Cliente de prueba"))
                .andExpect(jsonPath("$.correo").value(CORREO))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.debeCambiarPassword").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.intentosFallidos").doesNotExist())
                .andExpect(jsonPath("$.bloqueadoHasta").doesNotExist()).andReturn();
        Usuario u = usuarios.findByCorreo(CORREO).orElseThrow();
        assertThat(u.getPrivacidadAceptadaEn()).isEqualTo(reloj.instant());
        assertThat(u.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwords.matches(PASSWORD, u.getPasswordHash())).isTrue();
        var token = decoder.decode(cookieSesion(r).getValue());
        assertThat(token.getSubject()).isEqualTo(u.getId().toString());
        assertThat(token.getClaimAsString("rol")).isEqualTo("CLIENTE");
        assertThat(((Number) token.getClaim("tv")).intValue()).isZero();
        assertThat(token.getIssuedAt()).isEqualTo(reloj.instant());
        assertThat(token.getExpiresAt()).isEqualTo(reloj.instant().plus(Duration.ofHours(8)));
        comprobarCookie(cookieSesion(r), 28800);
        assertThat(r.getRequest().getSession(false)).isNull();
    }

    @Test void registro_correoConOtrasMayusculas_devuelve409() throws Exception {
        registrar(registro(CORREO)).andExpect(status().isCreated());
        registrar(registro("CLIENTE@EJEMPLO.TEST")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"telefono,123", "telefono,12345678a", "password,debil", "password,12345678",
            "password,abcdefgh", "nombre,''", "correo,correo-invalido", "aceptaPrivacidad,false"})
    void registro_datosInvalidos_devuelve400SinCrearUsuario(String campo, String valor) throws Exception {
        var datos = new java.util.HashMap<>(registro(CORREO));
        datos.put(campo, campo.equals("aceptaPrivacidad") ? Boolean.FALSE : valor);
        registrar(datos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores").isArray());
        assertThat(usuarios.count()).isZero();
    }

    @Test void registro_consentimientoAusenteONuloYPasswordUtf8Larga_rechaza() throws Exception {
        var datos = new java.util.HashMap<>(registro(CORREO));
        datos.remove("aceptaPrivacidad");
        registrar(datos).andExpect(status().isBadRequest());
        datos.put("aceptaPrivacidad", null);
        registrar(datos).andExpect(status().isBadRequest());
        datos.put("aceptaPrivacidad", true);
        datos.put("password", "á".repeat(36) + "a1");
        registrar(datos).andExpect(status().isBadRequest());
        assertThat(usuarios.count()).isZero();
    }

    @Test void login_correcto_iniciaSesionYReiniciaFallos() throws Exception {
        crear(Rol.CLIENTE, false);
        login(CORREO, "incorrecta123").andExpect(status().isUnauthorized());
        var r = login("CLIENTE@EJEMPLO.TEST", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(CORREO)).andReturn();
        comprobarCookie(cookieSesion(r), 28800);
        assertThat(usuarios.findByCorreo(CORREO).orElseThrow().getIntentosFallidos()).isZero();
        mvc.perform(get("/api/auth/sesion").cookie(cookieSesion(r)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.correo").value(CORREO));
    }

    @Test void login_inexistenteIncorrectoInactivoYPasswordExcesiva_mismoDetalle() throws Exception {
        Usuario u = crear(Rol.CLIENTE, false);
        String inexistente = detalle(login("ausente@ejemplo.test", PASSWORD));
        String incorrecto = detalle(login(CORREO, "incorrecta123"));
        String largo = detalle(login(CORREO, "a".repeat(73)));
        u.desactivar(reloj.instant());
        usuarios.saveAndFlush(u);
        String inactivo = detalle(login(CORREO, PASSWORD));
        assertThat(inexistente).isEqualTo(incorrecto).isEqualTo(inactivo).isEqualTo(largo);
    }

    @Test void login_cincoFallos_bloqueaQuinceMinutosYDesbloqueaEnElLimite() throws Exception {
        crear(Rol.CLIENTE, false);
        for (int i = 1; i < 5; i++) {
            login(CORREO, "incorrecta123").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
            assertThat(usuarios.findByCorreo(CORREO).orElseThrow().getIntentosFallidos()).isEqualTo((short) i);
        }
        login(CORREO, "incorrecta123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA_TEMPORALMENTE"));
        var u = usuarios.findByCorreo(CORREO).orElseThrow();
        assertThat(u.getIntentosFallidos()).isEqualTo((short) 5);
        assertThat(u.getBloqueadoHasta()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(15)));
        reloj.adelantar(Duration.ofMinutes(15).minusSeconds(1));
        login(CORREO, PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA_TEMPORALMENTE"));
        reloj.adelantar(Duration.ofSeconds(1));
        login(CORREO, PASSWORD).andExpect(status().isOk());
        u = usuarios.findByCorreo(CORREO).orElseThrow();
        assertThat(u.getIntentosFallidos()).isZero();
        assertThat(u.getBloqueadoHasta()).isNull();
    }

    @Test void login_bloqueoVencidoYFallo_empiezaNuevaSerie() throws Exception {
        Usuario u = crear(Rol.CLIENTE, false);
        for (int i = 0; i < 5; i++) u.registrarIntentoFallido(
                reloj.instant().plus(Duration.ofMinutes(15)), reloj.instant());
        usuarios.saveAndFlush(u);
        reloj.adelantar(Duration.ofMinutes(15));
        login(CORREO, "incorrecta123").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
        u = usuarios.findByCorreo(CORREO).orElseThrow();
        assertThat(u.getIntentosFallidos()).isEqualTo((short) 1);
        assertThat(u.getBloqueadoHasta()).isNull();
    }

    @Test void sesion_tokenCaducaALasOchoHorasExactas() throws Exception {
        Cookie sesion = cookieSesion(registrar(registro(CORREO)).andReturn());
        reloj.adelantar(Duration.ofHours(8).minusSeconds(1));
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isOk());
        reloj.adelantar(Duration.ofSeconds(1));
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test void sesion_tokenManipulado_rechazaSinDetalleTecnico() throws Exception {
        Cookie sesion = cookieSesion(registrar(registro(CORREO)).andReturn());
        String token = sesion.getValue();
        int firma = token.lastIndexOf('.') + 1;
        String alterado = token.substring(0, firma) + (token.charAt(firma) == 'a' ? 'b' : 'a')
                + token.substring(firma + 1);
        mvc.perform(get("/api/auth/sesion").cookie(new Cookie("BT_SESION", alterado)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/auth/sesion").cookie(new Cookie("BT_SESION", "malformado")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"inactivo", "version", "inexistente"})
    void sesion_cookieVigentePeroIdentidadRevocada_rechaza(String caso) throws Exception {
        Cookie sesion = cookieSesion(registrar(registro(CORREO)).andReturn());
        // Inactivo sin incrementar tv comprueba cada condición por separado.
        switch (caso) {
            case "inactivo" -> jdbc.update("update usuario set activo=false where id=1");
            case "version" -> jdbc.update("update usuario set token_version=token_version+1 where id=1");
            case "inexistente" -> LimpiezaBaseDatos.limpiar(jdbc);
            default -> throw new IllegalArgumentException();
        }
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test void sesion_bearerEnCabeceraSinCookie_noAutentica() throws Exception {
        Cookie sesion = cookieSesion(registrar(registro(CORREO)).andReturn());
        mvc.perform(get("/api/auth/sesion").header("Authorization", "Bearer " + sesion.getValue()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @ParameterizedTest @ValueSource(strings = {"registro", "login", "logout"})
    void escritura_sinCsrf_devuelve403ProblemDetail(String ruta) throws Exception {
        mvc.perform(post("/api/auth/" + ruta).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(registro(CORREO))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(usuarios.count()).isZero();
    }

    @Test void login_csrfIncorrecto_rechazaAunqueTieneCookie() throws Exception {
        Cookie csrf = xsrf();
        mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", "incorrecto")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("correo", CORREO, "password", PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test void logout_autenticadoYAnonimo_borraCookieDeFormaIdempotente() throws Exception {
        Cookie sesion = cookieSesion(registrar(registro(CORREO)).andReturn());
        Cookie csrf = xsrf();
        var r = mvc.perform(post("/api/auth/logout").cookie(sesion, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isNoContent()).andReturn();
        comprobarCookie(cookieSesion(r), 0);
        assertThat(cookieSesion(r).getValue()).isEmpty();
        mvc.perform(get("/api/auth/sesion").cookie(cookieSesion(r))).andExpect(status().isUnauthorized());
        r = mvc.perform(conCsrf(post("/api/auth/logout"))).andExpect(status().isNoContent()).andReturn();
        comprobarCookie(cookieSesion(r), 0);
    }

    @ParameterizedTest @EnumSource(Rol.class)
    void rutasNoImplementadas_siguenDenegadasYCatalogoYAuthPermanecenDisponibles(Rol rol) throws Exception {
        Usuario u = crear(rol, false);
        Cookie sesion = new Cookie("BT_SESION", jwt.emitir(u));
        mvc.perform(get("/api/prueba/no-implementada").cookie(sesion)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(get("/api/servicios").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value(rol.name()));
        mvc.perform(conCsrf(put("/api/auth/password")).cookie(sesion))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mvc.perform(conCsrf(put("/api/auth/password"))).andExpect(status().isUnauthorized());
    }

    @Test void passwordTemporal_restringeRutaDePruebaYPermiteAuth() throws Exception {
        Usuario u = crear(Rol.BARBERO, true);
        Cookie sesion = new Cookie("BT_SESION", jwt.emitir(u));
        mvc.perform(get("/api/prueba/protegida").cookie(sesion)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(true));
        mvc.perform(get("/api/perfil").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("BARBERO"));
    }

    @Test void sesion_adminConPerfilBarbero_devuelvePerfilOpcionalYRolVigente() throws Exception {
        Usuario u = crear(Rol.ADMIN, false);
        var barbero = barberos.saveAndFlush(new Barbero(u, "Corte", reloj.instant()));
        Cookie sesion = new Cookie("BT_SESION", jwt.emitir(u));
        jdbc.update("update usuario set rol='BARBERO' where id=?", u.getId());
        mvc.perform(get("/api/auth/sesion").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.barberoId").value(barbero.getId()))
                .andExpect(jsonPath("$.rol").value("BARBERO"));
    }

    @Test void adminInicial_runnerIdempotente_creaUnaVezSinTelefonoNiPasswordTemporal() {
        var runner = new AdminInicialRunner(auth, "ADMIN@EJEMPLO.TEST", "ClaveAdmin123", "Admin de prueba");
        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());
        assertThat(usuarios.count()).isEqualTo(1);
        Usuario admin = usuarios.findByCorreo("admin@ejemplo.test").orElseThrow();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getTelefono()).isNull();
        assertThat(admin.isDebeCambiarPassword()).isFalse();
        assertThat(admin.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwords.matches("ClaveAdmin123", admin.getPasswordHash())).isTrue();
    }

    @Test void adminInicial_configuracionIncompleta_noCreaUsuario() {
        new AdminInicialRunner(auth, "", "", "").run(new DefaultApplicationArguments());
        assertThat(usuarios.count()).isZero();
    }

    @Test void logout_cookieInvalida_borraIgualmenteConCsrf() throws Exception {
        var r = mvc.perform(conCsrf(post("/api/auth/logout"))
                .cookie(new Cookie("BT_SESION", "sesion-invalida")))
                .andExpect(status().isNoContent()).andReturn();
        comprobarCookie(cookieSesion(r), 0);
    }

    @Test void login_cincoFallosConcurrentes_noPierdeIncrementos() throws Exception {
        crear(Rol.CLIENTE, false);
        var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(5);
        var inicio = new java.util.concurrent.CountDownLatch(1);
        try {
            var resultados = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 5; i++) {
                resultados.add(ejecutor.submit(() -> {
                    inicio.await();
                    try {
                        auth.login(new pe.barberturno.auth.dto.LoginDto(CORREO, "incorrecta123"));
                        return "ACCESO_INESPERADO";
                    } catch (pe.barberturno.common.error.NegocioException error) {
                        return error.codigo().name();
                    }
                }));
            }
            inicio.countDown();
            var codigos = new java.util.ArrayList<String>();
            for (var resultado : resultados) codigos.add(resultado.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(codigos).containsExactlyInAnyOrder("CREDENCIALES_INVALIDAS", "CREDENCIALES_INVALIDAS",
                    "CREDENCIALES_INVALIDAS", "CREDENCIALES_INVALIDAS", "CUENTA_BLOQUEADA_TEMPORALMENTE");
            var u = usuarios.findByCorreo(CORREO).orElseThrow();
            assertThat(u.getIntentosFallidos()).isEqualTo((short) 5);
            assertThat(u.getBloqueadoHasta()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(15)));
        } finally {
            inicio.countDown();
            ejecutor.shutdownNow();
            assertThat(ejecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private Map<String, Object> registro(String correo) {
        return Map.of("nombre", "Cliente de prueba", "correo", correo,
                "telefono", "999111222", "password", PASSWORD, "aceptaPrivacidad", true);
    }
    private ResultActions registrar(Map<String, Object> datos) throws Exception {
        return mvc.perform(conCsrf(post("/api/auth/registro")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(datos)));
    }
    private ResultActions login(String correo, String password) throws Exception {
        return mvc.perform(conCsrf(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "password", password))));
    }
    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder peticion) throws Exception {
        Cookie csrf = xsrf();
        return peticion.cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }
    private Cookie xsrf() throws Exception {
        return mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }
    private Cookie cookieSesion(MvcResult r) {
        Cookie cookie = r.getResponse().getCookie("BT_SESION");
        assertThat(cookie).isNotNull();
        return cookie;
    }
    private void comprobarCookie(Cookie cookie, int segundos) {
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(segundos);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
    }
    private Usuario crear(Rol rol, boolean temporal) {
        Usuario u = new Usuario("Usuario de prueba", CORREO, rol == Rol.CLIENTE ? "999111222" : null,
                passwords.encode(PASSWORD), rol, rol == Rol.CLIENTE ? reloj.instant() : null, reloj.instant());
        if (temporal) u.cambiarPassword(u.getPasswordHash(), true, reloj.instant());
        return usuarios.saveAndFlush(u);
    }
    private String detalle(ResultActions solicitud) throws Exception {
        var r = solicitud.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS")).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("detail").asString();
    }
}
