package pe.barberturno.common.tenancy;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.platform.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Negocios reales creados por HTTP, sesiones BCrypt, CSRF y rol de aplicación reales. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PlataformaPruebaBase {
    static final String CLAVE = "ClaveFicticia123";
    @LocalServerPort int puerto;
    @Autowired JdbcNegocioPruebas sql;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EjecutorEnNegocio ejecutor;
    @Autowired DirectorioNegocios directorio;
    @Autowired ContextoNegocio contexto;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    @Autowired JsonMapper mapper;
    @Autowired PlataformaService plataformaService;
    final List<Sesion> sesiones = new ArrayList<>();
    Usuario plataforma;
    Usuario propietario;
    Usuario encargado;
    Sesion admin;
    Sesion titular;
    Sesion gestorLocal;
    long a;
    long b;

    @BeforeEach void prepararPlataforma() throws Exception {
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
        plataforma = cuenta("plataforma");
        plataforma.habilitarAdministracionPlataforma();
        usuarios.saveAndFlush(plataforma);
        admin = login("plataforma");
        a = alta("a"); b = alta("b");
        propietario = cuenta("propietario");
        encargado = cuenta("encargado");
        for (long id : new long[]{a, b, directorio.buscar("barberturno").orElseThrow()}) {
            membresias.save(new Membresia(id, propietario.getId(), RolNegocio.PROPIETARIO, reloj.instant()));
            membresias.save(new Membresia(id, encargado.getId(), RolNegocio.ENCARGADO, reloj.instant()));
        }
        titular = login("propietario"); gestorLocal = login("encargado");
    }

    @AfterEach void cerrarPlataforma() {
        try {
            for (var sesion : sesiones) {
                sesion.cliente().close();
                sesion.cookies().getCookieStore().removeAll();
            }
            sesiones.clear();
            assertThat(contexto.delimitado()).isFalse();
        } finally {
            // El alias se conserva entre pruebas y debe terminar ACTIVO aunque falle una aserción.
            new TransactionTemplate(gestor).executeWithoutResult(tx ->
                    jdbc.update("update negocio set estado='ACTIVO' where slug='barberturno'"));
            LimpiezaBaseDatos.limpiar(sql);
        }
    }

    long alta(String slug) throws Exception {
        var respuesta = http(admin, "POST", "/api/plataforma/negocios", altaJson(slug), token(admin));
        assertThat(respuesta.statusCode()).isEqualTo(201);
        return mapper.readTree(respuesta.body()).get("id").asLong();
    }

    static String altaJson(String slug) {
        return "{\"nombre\":\"Negocio ficticio\",\"slug\":\"" + slug
                + "\",\"correoPropietario\":\"invitado@ejemplo.test\"}";
    }

    Usuario cuenta(String alias) {
        return usuarios.saveAndFlush(new Usuario("Persona ficticia", alias + "@ejemplo.test", "999000001",
                passwords.encode(CLAVE), Rol.CLIENTE, reloj.instant(), reloj.instant()));
    }

    <T> T en(String slug, Supplier<T> accion) {
        return ejecutor.ejecutar(slug, () -> new TransactionTemplate(gestor).execute(tx -> accion.get()));
    }

    Sesion login(String alias) throws Exception { return loginClave(alias, CLAVE); }

    Sesion loginClave(String alias, String clave) throws Exception {
        var sesion = anonima();
        assertThat(http(sesion, "GET", "/api/auth/sesion", null, null).statusCode()).isEqualTo(401);
        assertThat(http(sesion, "POST", "/api/auth/login", "{\"correo\":\"" + alias
                + "@ejemplo.test\",\"password\":\"" + clave + "\"}", token(sesion)).statusCode()).isEqualTo(200);
        assertThat(sesion.cookies().getCookieStore().getCookies()).anyMatch(c -> c.getName().equals("BT_SESION"));
        return sesion;
    }

    Sesion anonima() {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var sesion = new Sesion(cookies, HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
        sesiones.add(sesion);
        return sesion;
    }

    String token(Sesion sesion) {
        return sesion.cookies().getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue).findFirst().orElseThrow();
    }

    HttpResponse<String> http(Sesion sesion, String metodo, String ruta, String cuerpo, String csrf) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).timeout(Duration.ofSeconds(20));
        if (csrf != null) peticion.header("X-XSRF-TOKEN", csrf);
        if (cuerpo != null) peticion.header("Content-Type", "application/json");
        return sesion.cliente().send(peticion.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
    }

    void estado(long id, String estado) throws Exception {
        assertThat(http(admin, "PATCH", "/api/plataforma/negocios/" + id + "/estado",
                "{\"estado\":\"" + estado + "\"}", token(admin)).statusCode()).isEqualTo(200);
    }

    long auditorias() { return jdbc.queryForObject("select count(*) from auditoria_plataforma", Long.class); }

    Map<String, Object> huella(String slug) {
        return en(slug, () -> {
            var resultado = new LinkedHashMap<String, Object>();
            for (String tabla : List.of("cliente", "servicio", "barbero", "jornada", "bloqueo", "reserva", "auditoria_reserva", "notificacion")) {
                resultado.put(tabla, jdbc.queryForList("select * from " + tabla + " order by id"));
            }
            resultado.put("membresia", jdbc.queryForList("select * from membresia where negocio_id=? order by id", directorio.buscar(slug).orElseThrow()));
            return resultado;
        });
    }

    record Sesion(CookieManager cookies, HttpClient cliente) { }
}
