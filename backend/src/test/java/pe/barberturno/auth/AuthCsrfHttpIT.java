package pe.barberturno.auth;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import pe.barberturno.auth.dto.RegistroDto;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.support.RelojAjustable;
import static org.assertj.core.api.Assertions.assertThat;

/** Regresión DA-26 con Tomcat real: comprueba las cookies recibidas por HTTP, sin imprimir valores. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthCsrfHttpIT {
    private static final String LOGIN = """
            {"correo":"http@ejemplo.test","password":"ClaveHttp123"}
            """;
    private static final String REGISTRO = """
            {"nombre":"Cliente HTTP","correo":"http@ejemplo.test","telefono":"999111222",
             "password":"ClaveHttp123","aceptaPrivacidad":true}
            """;
    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired RelojAjustable reloj;
    private CookieManager cookies;
    private HttpClient cliente;

    @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        cliente = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach void limpiar() {
        cliente.close();
        LimpiezaBaseDatos.limpiar(jdbc);
    }

    @ParameterizedTest @ValueSource(strings = {"registro", "login", "logout"})
    void cambioDeSesion_emiteAmbasCookiesYPermiteEscribirSinGetIntermedio(String ruta) throws Exception {
        if (!ruta.equals("registro")) crearCliente();
        assertThat(enviar("GET", "/api/auth/sesion", null, null).statusCode()).isEqualTo(401);
        if (ruta.equals("logout")) {
            assertThat(enviar("POST", "/api/auth/login", LOGIN, tokenActual()).statusCode()).isEqualTo(200);
        }
        String anterior = tokenActual();
        var resultado = enviar("POST", "/api/auth/" + ruta,
                ruta.equals("registro") ? REGISTRO : ruta.equals("login") ? LOGIN : null, anterior);
        assertThat(resultado.statusCode()).isEqualTo(ruta.equals("registro") ? 201 : ruta.equals("login") ? 200 : 204);
        var cabeceras = resultado.headers().allValues("Set-Cookie");
        assertThat(cabeceras.stream().filter(valor -> valor.startsWith("BT_SESION=")).count())
                .as("Set-Cookie de sesión recibido por HTTP").isEqualTo(1);
        assertThat(cabeceras.stream().filter(valor -> valor.startsWith("XSRF-TOKEN=")).count())
                .as("Set-Cookie CSRF recibido por HTTP").isEqualTo(1);
        for (String cabecera : cabeceras) {
            var cookie = HttpCookie.parse(cabecera).getFirst();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getSecure()).isFalse();
            assertThat(cabecera.contains("SameSite=Strict")).isTrue();
            if (cookie.getName().equals("BT_SESION")) {
                assertThat(cookie.isHttpOnly()).isTrue();
                assertThat(cookie.getMaxAge()).isEqualTo(ruta.equals("logout") ? 0 : 28800);
            } else {
                assertThat(cookie.isHttpOnly()).isFalse();
                assertThat(cookie.getMaxAge()).isEqualTo(-1);
            }
        }
        String nuevo = tokenActual();
        assertThat(!nuevo.isBlank() && !nuevo.equals(anterior)).as("CSRF renovado en la misma respuesta").isTrue();
        // Tras logout, el login público permite demostrar el CSRF anónimo sin otra lectura.
        String escritura = ruta.equals("logout") ? "/api/auth/login" : "/api/notificaciones/lectura";
        String cuerpo = ruta.equals("logout") ? LOGIN : null;
        assertThat(enviar("POST", escritura, cuerpo, anterior).statusCode()).isEqualTo(403);
        assertThat(enviar("POST", escritura, cuerpo, nuevo).statusCode()).isEqualTo(ruta.equals("logout") ? 200 : 204);
        String estable = tokenActual();
        for (String lectura : new String[]{"/api/notificaciones/conteo", "/api/auth/sesion"}) {
            var lecturaRespuesta = enviar("GET", lectura, null, null);
            assertThat(lecturaRespuesta.statusCode()).isEqualTo(200);
            assertThat(lecturaRespuesta.headers().allValues("Set-Cookie").stream()
                    .anyMatch(valor -> valor.startsWith("XSRF-TOKEN="))).isFalse();
            assertThat(tokenActual().equals(estable)).isTrue();
        }
    }

    @Test void loginYRegistroFallidos_noEmitenNiCambianCookies() throws Exception {
        crearCliente();
        enviar("GET", "/api/auth/sesion", null, null);
        String anterior = tokenActual();
        var fallido = enviar("POST", "/api/auth/login", LOGIN.replace("ClaveHttp123", "Incorrecta123"), anterior);
        assertThat(fallido.statusCode()).isEqualTo(401);
        assertThat(fallido.headers().allValues("Set-Cookie").isEmpty()).isTrue();
        assertThat(tokenActual().equals(anterior)).isTrue();
        var duplicado = enviar("POST", "/api/auth/registro", REGISTRO, anterior);
        assertThat(duplicado.statusCode()).isEqualTo(409);
        assertThat(duplicado.headers().allValues("Set-Cookie").isEmpty()).isTrue();
        assertThat(tokenActual().equals(anterior)).isTrue();
    }

    private void crearCliente() {
        auth.registrar(new RegistroDto("Cliente HTTP", "http@ejemplo.test", "999111222", "ClaveHttp123", true));
    }

    private String tokenActual() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .findFirst().orElseThrow().getValue();
    }

    private HttpResponse<Void> enviar(String metodo, String ruta, String cuerpo, String token) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .timeout(Duration.ofSeconds(15));
        if (token != null) peticion.header("X-XSRF-TOKEN", token);
        if (cuerpo != null) peticion.header("Content-Type", "application/json");
        peticion.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo));
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.discarding());
    }
}
