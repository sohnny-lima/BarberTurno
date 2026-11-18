package pe.barberturno.common.tenancy;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.auth.AdminInicialRunner;
import pe.barberturno.auth.AuthService;
import pe.barberturno.customers.Cliente;
import pe.barberturno.customers.ClienteRepository;
import pe.barberturno.platform.*;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.support.JdbcNegocioPruebas;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.users.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** CF-1 por Tomcat real: mismas cookies antes y después de cada cambio de pertenencia. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MotivoRevocacionIT {
    private static final String CLAVE = "ClaveFicticia123";
    @LocalServerPort int puerto;
    @Autowired JdbcNegocioPruebas sql;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment entorno;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EjecutorEnNegocio ejecutor;
    @Autowired DirectorioNegocios directorio;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired BarberoRepository barberos;
    @Autowired ClienteRepository clientes;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    @Autowired JsonMapper mapper;
    private final List<Sesion> sesiones = new ArrayList<>();
    private Usuario persona;
    private long perfil;
    private Sesion propietarioA;
    private Sesion propietarioB;

    @BeforeEach void preparar() throws Exception {
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
        try (var conexion = propietario(); var sentencia = conexion.createStatement()) {
            sentencia.executeUpdate("""
                    insert into negocio(slug,nombre,creado_en,actualizado_en) values
                    ('a','Ficticio A',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                    ('b','Ficticio B',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """);
        }
        for (String slug : List.of("a", "b")) {
            var titular = cuenta("titular-" + slug);
            membresias.save(new Membresia(id(slug), titular.getId(), RolNegocio.PROPIETARIO, reloj.instant()));
        }
        persona = cuenta("profesional");
        for (String slug : List.of("a", "b")) {
            membresias.save(new Membresia(id(slug), persona.getId(), RolNegocio.BARBERO, reloj.instant()));
            long creado = en(slug, () -> barberos.saveAndFlush(new Barbero(persona, "Corte", reloj.instant())).getId());
            if (slug.equals("a")) perfil = creado;
        }
        propietarioA = login("titular-a");
        propietarioB = login("titular-b");
    }

    @AfterEach void limpiar() {
        try {
            for (var sesion : sesiones) {
                sesion.cliente().close();
                sesion.cookies().getCookieStore().removeAll();
            }
        } finally { LimpiezaBaseDatos.limpiar(sql); }
    }

    @Test void desactivarYReactivar_soloRestauraLaBajaDelPerfil_yConservaBYCuenta() throws Exception {
        var sesion = login("profesional");
        var cuenta = huellaCuenta();
        var b = membresia("b");
        permisos(sesion, "a", 200);
        permisos(sesion, "b", 200);
        estado(propietarioA, "a", false, 200);
        assertThat(membresia("a")).containsEntry("estado", "REVOCADA")
                .containsEntry("motivo_revocacion", "PERFIL_DESACTIVADO");
        permisos(sesion, "a", 403);
        estado(propietarioA, "a", true, 200);
        assertThat(membresia("a")).containsEntry("estado", "ACTIVA").containsEntry("motivo_revocacion", null);
        permisos(sesion, "a", 200);
        permisos(sesion, "b", 200);
        assertThat(huellaCuenta()).isEqualTo(cuenta);
        assertThat(membresia("b")).isEqualTo(b);
    }

    @ParameterizedTest @EnumSource(value = MotivoRevocacion.class, names = {"SALIDA", "DECISION_GESTOR", "PLATAFORMA"})
    void otraCausa_reactivarPerfilNoRestauraPermisosNiCambiaCuenta(MotivoRevocacion motivo) throws Exception {
        // La ficha activa mantiene el ámbito ordinario; no amplía la excepción del perfil inactivo.
        en("a", () -> clientes.saveAndFlush(new Cliente(persona, reloj.instant())));
        var sesion = login("profesional");
        permisos(sesion, "a", 200);
        estado(propietarioA, "a", false, 200);
        prepararCausa(motivo);
        var cuenta = huellaCuenta();
        var baja = membresia("a");
        var b = membresia("b");
        permisos(sesion, "a", 403);
        estado(propietarioA, "a", true, 200);
        assertThat(en("a", () -> barberos.findById(perfil).orElseThrow().isActivo())).isTrue();
        permisos(sesion, "a", 403); // Petición inmediata con la misma sesión real.
        permisos(sesion, "b", 200);
        assertThat(membresia("a")).isEqualTo(baja);
        assertThat(membresia("b")).isEqualTo(b);
        assertThat(huellaCuenta()).isEqualTo(cuenta);
    }

    @ParameterizedTest @EnumSource(value = MotivoRevocacion.class, names = {"SALIDA", "DECISION_GESTOR", "PLATAFORMA"})
    void sinFicha_otraCausaNoAbreLaExcepcionDeAmbito(MotivoRevocacion motivo) throws Exception {
        estado(propietarioA, "a", false, 200);
        prepararCausa(motivo);
        var baja = membresia("a");
        var cuenta = huellaCuenta();
        estado(propietarioA, "a", true, 404);
        assertThat(en("a", () -> barberos.findById(perfil).orElseThrow().isActivo())).isFalse();
        assertThat(membresia("a")).isEqualTo(baja);
        assertThat(huellaCuenta()).isEqualTo(cuenta);
    }

    @Test void escrituraExistente_conservaCsrfRolesYAislamiento() throws Exception {
        var sesion = login("profesional");
        String ruta = "/api/n/a/barberos/" + perfil + "/estado";
        var cuenta = huellaCuenta();
        assertThat(http(propietarioA, "PATCH", ruta, "{\"activo\":false}", null).statusCode()).isEqualTo(403);
        assertThat(http(propietarioA, "PATCH", ruta, "{\"activo\":false}", "incorrecto").statusCode()).isEqualTo(403);
        estado(sesion, "a", false, 403);
        estado(propietarioB, "b", false, 404);
        assertThat(en("a", () -> barberos.findById(perfil).orElseThrow().isActivo())).isTrue();
        assertThat(huellaCuenta()).isEqualTo(cuenta);
    }

    @ParameterizedTest @EnumSource(MotivoRevocacion.class)
    void runner_repetidoConPropiedadRevocadaConservaBajaYCredenciales(MotivoRevocacion motivo) throws Exception {
        var inicial = cuenta("inicial");
        inicial.habilitarAdministracionPlataforma();
        usuarios.saveAndFlush(inicial);
        var runner = new AdminInicialRunner(auth, ejecutor, "a", inicial.getCorreo(), CLAVE, "Inicial ficticio");
        runner.run(new DefaultApplicationArguments()); // Crea si falta.
        var sesion = login("inicial");
        assertThat(http(sesion, "GET", "/api/n/a/usuarios", null, null).statusCode()).isEqualTo(200);
        var propiedad = membresias.findByNegocioIdAndUsuarioId(id("a"), inicial.getId()).orElseThrow();
        propiedad.revocar(motivo, reloj.instant());
        membresias.save(propiedad);
        var baja = en("a", () -> jdbc.queryForMap("select * from membresia where negocio_id=? and usuario_id=?", id("a"), inicial.getId()));
        var cuenta = en("a", () -> jdbc.queryForMap("select * from usuario where id=?", inicial.getId()));
        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());
        assertThat(http(sesion, "GET", "/api/n/a/usuarios", null, null).statusCode()).isEqualTo(403);
        assertThat(en("a", () -> jdbc.queryForMap("select * from membresia where negocio_id=? and usuario_id=?", id("a"), inicial.getId()))).isEqualTo(baja);
        assertThat(en("a", () -> jdbc.queryForMap("select * from usuario where id=?", inicial.getId()))).isEqualTo(cuenta);
    }

    @Test void esquema_rechazaIncoherenciasCon23514ComoAplicacion() {
        en("a", () -> {
            MotivoRevocacionEsquemaPruebas.comprobar(jdbc, id("a"), persona.getId());
            return null;
        });
    }

    private void prepararCausa(MotivoRevocacion motivo) throws Exception {
        // Excepción expresa del encargo 088: prepara un estado sin endpoint todavía.
        try (var conexion = propietario(); var sentencia = conexion.prepareStatement(
                "update membresia set motivo_revocacion=? where negocio_id=? and usuario_id=? and estado='REVOCADA'")) {
            sentencia.setString(1, motivo.name()); sentencia.setLong(2, id("a")); sentencia.setLong(3, persona.getId());
            assertThat(sentencia.executeUpdate()).isEqualTo(1);
        }
    }

    private java.sql.Connection propietario() throws Exception {
        var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"));
        assertThat(conexion.getCatalog()).isEqualTo("barberturno_v2_test");
        return conexion;
    }

    private Usuario cuenta(String alias) {
        return usuarios.saveAndFlush(new Usuario("Persona ficticia", alias + "@ejemplo.test", "999000001",
                passwords.encode(CLAVE), Rol.CLIENTE, reloj.instant(), reloj.instant()));
    }
    private long id(String slug) { return directorio.buscar(slug).orElseThrow(); }
    private <T> T en(String slug, Supplier<T> accion) {
        return ejecutor.ejecutar(slug, () -> new TransactionTemplate(gestor).execute(estado -> accion.get()));
    }
    private Map<String, Object> huellaCuenta() {
        return en("a", () -> jdbc.queryForMap("select * from usuario where id=?", persona.getId()));
    }
    private Map<String, Object> membresia(String slug) {
        return en(slug, () -> jdbc.queryForMap("select * from membresia where negocio_id=? and usuario_id=?", id(slug), persona.getId()));
    }
    private void permisos(Sesion sesion, String slug, int esperado) throws Exception {
        assertThat(http(sesion, "GET", "/api/n/" + slug + "/reservas?desde=2026-10-01&hasta=2026-10-01", null, null).statusCode()).isEqualTo(esperado);
    }
    private void estado(Sesion sesion, String slug, boolean activo, int esperado) throws Exception {
        assertThat(http(sesion, "PATCH", "/api/n/" + slug + "/barberos/" + perfil + "/estado",
                "{\"activo\":" + activo + "}", token(sesion)).statusCode()).isEqualTo(esperado);
    }
    private Sesion login(String alias) throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var sesion = new Sesion(cookies, HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
        sesiones.add(sesion);
        assertThat(http(sesion, "GET", "/api/auth/sesion", null, null).statusCode()).isEqualTo(401);
        assertThat(http(sesion, "POST", "/api/auth/login", "{\"correo\":\"" + alias
                + "@ejemplo.test\",\"password\":\"" + CLAVE + "\"}", token(sesion)).statusCode()).isEqualTo(200);
        assertThat(cookies.getCookieStore().getCookies()).anyMatch(c -> c.getName().equals("BT_SESION"));
        return sesion;
    }
    private String token(Sesion sesion) {
        return sesion.cookies().getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(java.net.HttpCookie::getValue).findFirst().orElseThrow();
    }
    private HttpResponse<String> http(Sesion sesion, String metodo, String ruta, String cuerpo, String csrf) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).timeout(Duration.ofSeconds(15));
        if (csrf != null) peticion.header("X-XSRF-TOKEN", csrf);
        if (cuerpo != null) peticion.header("Content-Type", "application/json");
        return sesion.cliente().send(peticion.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private record Sesion(CookieManager cookies, HttpClient cliente) { }
}
