package pe.barberturno.common.tenancy;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.MembresiaRepository;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.support.JdbcNegocioPruebas;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import pe.barberturno.users.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** CE-8 por TCP: conserva el request-target y observa MVC sin sustituir filtros ni controladores. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=1", "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"})
@Import({RelojAjustable.Configuracion.class, RutasServidorRealIT.Observacion.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RutasServidorRealIT {
    private static final String CLAVE = "ClaveFicticia123";
    private static final String ALTA = """
            {"nombre":"No debe escribirse","descripcion":"Ficticio","duracionMin":30,"precio":20}
            """;
    @LocalServerPort int puerto;
    @Autowired JdbcNegocioPruebas sql;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment entorno;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EjecutorEnNegocio ejecutor;
    @Autowired DirectorioNegocios directorio;
    @Autowired ContextoNegocio contexto;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired PasswordEncoder passwords;
    @Autowired RelojAjustable reloj;
    @Autowired JsonMapper mapper;
    @Autowired Observacion observacion;
    private final List<Sesion> sesiones = new ArrayList<>();

    @BeforeEach void preparar() throws Exception {
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
        try (var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"));
                var sentencia = conexion.prepareStatement("""
                    insert into negocio(slug,nombre,creado_en,actualizado_en)
                    values (?, ?, CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """)) {
            assertThat(conexion.getCatalog()).isEqualTo("barberturno_v2_test");
            for (String slug : List.of("a", "b")) {
                sentencia.setString(1, slug); sentencia.setString(2, "Negocio ficticio " + slug);
                sentencia.executeUpdate();
            }
        }
        for (String slug : List.of("a", "b")) {
            en(slug, () -> jdbc.update("""
                    insert into servicio(nombre,descripcion,duracion_min,precio,creado_en,actualizado_en)
                    values (?, 'Ficticio', 30, 20, CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, "Corte " + slug));
            var cuenta = usuarios.saveAndFlush(new Usuario("Propietario ficticio " + slug,
                    "rutas-http-" + slug + "@ejemplo.test", "999000001", passwords.encode(CLAVE),
                    Rol.CLIENTE, reloj.instant(), reloj.instant()));
            membresias.save(new Membresia(directorio.buscar(slug).orElseThrow(), cuenta.getId(),
                    RolNegocio.PROPIETARIO, reloj.instant()));
        }
    }

    @AfterEach void limpiar() {
        try {
            for (var sesion : sesiones) {
                sesion.cliente().close();
                sesion.cookies().getCookieStore().removeAll();
            }
            assertThat(contexto.delimitado()).isFalse();
        } finally {
            LimpiezaBaseDatos.limpiar(sql);
            observacion.trazas.clear();
        }
    }

    @Test void variantes_noCruzanLaFronteraNiEjecutanControladores_yElServidorConservaElAislamiento()
            throws Exception {
        var sesionA = login("a");
        var sesionB = login("b");
        catalogo(sesionA, "a");
        catalogo(sesionA, "b");
        catalogo(sesionB, "b");
        assertThat(http(sesionA, "GET", "/api/perfil", null, null).codigo()).isEqualTo(200);
        assertThat(http(sesionA, "GET", "/api/n/a/usuarios", null, null).codigo()).isEqualTo(200);
        assertThat(http(sesionB, "GET", "/api/n/b/usuarios", null, null).codigo()).isEqualTo(200);
        assertThat(http(sesionA, "POST", "/api/n/b/servicios", ALTA, token(sesionA)).codigo()).isEqualTo(403);
        assertThat(http(sesionA, "POST", "/api/n/a/servicios", ALTA, null).codigo()).isEqualTo(403);
        assertThat(http(sesionA, "POST", "/api/n/a/servicios", ALTA, "incorrecto").codigo()).isEqualTo(403);
        var inicial = huella();
        int conexion = conexionLimpia();

        // Un único caso secuencial se detiene en la primera aceptación o llegada inesperada a MVC.
        for (String ruta : variantes()) {
            for (var sesion : List.of(sesionA, sesionB)) {
                for (String metodo : List.of("GET", ruta.contains("password") ? "PUT" : "POST")) {
                    var resultado = cruda(sesion, metodo, ruta);
                    String componente = componente(resultado);
                    System.out.printf("RUTA_HTTP_REAL | %s | %s | propietario %s | %d | %s%n",
                            metodo, ruta, sesion.slug(), resultado.codigo(), componente);
                    assertThat(resultado.codigo()).as("%s %s: detener ante aceptación", metodo, ruta)
                            .isIn(400, 404);
                    assertThat(resultado.traza().controladores).as("%s %s: ningún controlador", metodo, ruta)
                            .isEmpty();
                    assertThat(componente).as("Origen identificado para %s", ruta)
                            .isIn("Tomcat", "StrictHttpFirewall", "ResolutorNegocio");
                    assertThat(resultado.cuerpo()).doesNotContain("Corte a", "Corte b", "passwordHash");
                    assertThat(huella().equals(inicial)).as("Sin efectos en A, B, alias ni identidad: %s", ruta).isTrue();
                    assertThat(conexionLimpia()).isEqualTo(conexion);
                    catalogo(sesion, "b");
                    assertThat(conexionLimpia()).isEqualTo(conexion);
                }
            }
        }
    }

    private static List<String> variantes() {
        return List.of("/api/n/a/%70erfil", "/api/n/a/%50erfil", "/api/n/a/perfil",
                "/api/n/a/auth/sesion", "/api/n/a/%61uth/password", "/api/n/a/mis-negocios",
                "/api/%70erfil", "/api/%61uth/sesion", "/api/%6E/a/servicios",
                "/api/n/%61/servicios", "/api/n/a%2Fb/servicios", "/%61pi/n/a/servicios",
                "/%61pi/servicios", "/api/n/a/%73ervicios", "/api/%73ervicios",
                "/api/%61uth/password", "/api/n/a/servicios%2F", "/api/servicios%2F",
                "/api/n/a/%2E/servicios", "/api/%2E/perfil", "/api/n/a/%2e%2e/servicios",
                "/api/n/a/%25servicios", "/api/%25perfil", "/api/n/a/%zz/servicios",
                "/api/n/a/servicios%", "/api/%zz", "/api/perfil%",
                "/api/n/a//servicios", "/api//perfil", "/api/n/a/./servicios", "/api/./perfil",
                "/api/n/a/../servicios", "/api/../perfil", "/api/n/a/servicios;jsessionid=x",
                "/api/n/a/servicios;param", "/api/perfil;param", "/api/n/a/servicios\\perfil",
                "/api/servicios\\perfil", "/api/n/a/servicios%5Cperfil", "/api/n/a/servi cios",
                "/API/n/a/servicios", "/API/perfil", "/api/n/a/SERVICIOS", "/api/N/a/servicios",
                "/api/n/A/servicios", "/api/n/a/servicios/", "/api/servicios/", "/api/perfil/",
                "/api/n/a/perfil/", "/api/n/a/auth/password/", "/api/auth/password/",
                "/api/%70lataforma/negocios", "/api/plataforma/%6eegocios", "/api/n/a/%70lataforma/negocios",
                "/api/n/a/plataforma/negocios", "/api/PLATAFORMA/negocios", "/api/plataforma/negocios/");
    }

    private Sesion login(String slug) throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var sesion = new Sesion(slug, cookies, HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
        sesiones.add(sesion);
        assertThat(http(sesion, "GET", "/api/auth/sesion", null, null).codigo()).isEqualTo(401);
        assertThat(http(sesion, "POST", "/api/auth/login", """
                {"correo":"rutas-http-%s@ejemplo.test","password":"%s"}
                """.formatted(slug, CLAVE), token(sesion)).codigo()).isEqualTo(200);
        assertThat(cookies.getCookieStore().getCookies().stream()
                .anyMatch(c -> c.getName().equals("BT_SESION") && !c.getValue().isBlank())).isTrue();
        return sesion;
    }

    private void catalogo(Sesion sesion, String slug) throws Exception {
        var resultado = http(sesion, "GET", "/api/n/" + slug + "/servicios", null, null);
        assertThat(resultado.codigo()).isEqualTo(200);
        var datos = mapper.readTree(resultado.cuerpo());
        assertThat(datos.isArray() && datos.size() == 1).isTrue();
        assertThat(datos.get(0).get("nombre").asText()).isEqualTo("Corte " + slug);
        assertThat(resultado.traza().controladores).hasSize(1);
    }

    private Resultado http(Sesion sesion, String metodo, String ruta, String cuerpo, String csrf) throws Exception {
        String id = UUID.randomUUID().toString();
        var traza = new Traza();
        observacion.trazas.put(id, traza);
        try {
            var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                    .timeout(Duration.ofSeconds(15)).header("X-Prueba-Ruta", id);
            if (csrf != null) peticion.header("X-XSRF-TOKEN", csrf);
            if (cuerpo != null) peticion.header("Content-Type", "application/json");
            var respuesta = sesion.cliente().send(peticion.method(metodo, cuerpo == null
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                    HttpResponse.BodyHandlers.ofString());
            completada(traza);
            return new Resultado(respuesta.statusCode(), respuesta.body(), traza);
        } finally {
            observacion.trazas.remove(id);
        }
    }

    private Resultado cruda(Sesion sesion, String metodo, String ruta) throws Exception {
        String id = UUID.randomUUID().toString();
        var traza = new Traza();
        observacion.trazas.put(id, traza);
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", puerto), 10000);
            socket.setSoTimeout(15000);
            String cookies = String.join("; ", sesion.cookies().getCookieStore().getCookies().stream()
                    .map(c -> c.getName() + "=" + c.getValue()).toList());
            String cuerpo = metodo.equals("GET") ? "" : ALTA;
            // No URI, HttpClient ni proxy interviene en esta línea: Tomcat recibe estos bytes exactos.
            String peticion = metodo + " " + ruta + " HTTP/1.1\r\nHost: 127.0.0.1:" + puerto
                    + "\r\nConnection: close\r\nX-Prueba-Ruta: " + id + "\r\nCookie: " + cookies
                    + "\r\nX-XSRF-TOKEN: " + token(sesion) + "\r\nContent-Type: application/json"
                    + "\r\nContent-Length: " + cuerpo.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + cuerpo;
            socket.getOutputStream().write(peticion.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            String respuesta = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(respuesta.startsWith("HTTP/1.1 ")).as("Respuesta HTTP real para %s", ruta).isTrue();
            int estado = Integer.parseInt(respuesta.substring(9, 12));
            String contenido = respuesta.substring(respuesta.indexOf("\r\n\r\n") + 4);
            if (traza.entrada) completada(traza);
            return new Resultado(estado, contenido, traza);
        } finally {
            observacion.trazas.remove(id);
        }
    }

    private void completada(Traza traza) throws InterruptedException {
        assertThat(traza.fin.await(5, TimeUnit.SECONDS)).as("El filtro observador terminó").isTrue();
        assertThat(traza.contextoLimpio).as("Holder limpio en el hilo del servidor").isTrue();
    }

    private String componente(Resultado resultado) {
        if (!resultado.traza().entrada && resultado.codigo() == 400) return "Tomcat";
        if (resultado.cuerpo().contains("La ruta de la petición no es válida.")) return "StrictHttpFirewall";
        if (resultado.cuerpo().contains("La ruta de la API no admite codificación porcentual.")
                || resultado.cuerpo().contains("No se encontró la ruta del negocio.")) return "ResolutorNegocio";
        return "SIN CLASIFICAR";
    }

    private String token(Sesion sesion) {
        return sesion.cookies().getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
    }

    private <T> T en(String slug, Supplier<T> accion) {
        return ejecutor.ejecutar(slug, () -> new TransactionTemplate(gestor).execute(tx -> accion.get()));
    }

    private Map<String, Object> huella() {
        var resultado = new LinkedHashMap<String, Object>();
        for (String slug : List.of("a", "b", "barberturno")) {
            en(slug, () -> {
                for (String tabla : List.of("servicio", "barbero", "jornada", "bloqueo", "reserva",
                        "auditoria_reserva", "notificacion", "cliente")) {
                    resultado.put(slug + ":" + tabla, jdbc.queryForList("select * from " + tabla + " order by id"));
                }
                return null;
            });
        }
        for (String tabla : List.of("usuario", "membresia", "negocio", "vinculo_cliente")) {
            resultado.put(tabla, jdbc.queryForList("select * from " + tabla + " order by 1,2"));
        }
        return resultado;
    }

    private int conexionLimpia() {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) conexion -> {
            try (var sentencia = conexion.createStatement(); var fila = sentencia.executeQuery("""
                    select pg_backend_pid(), nullif(current_setting('app.negocio_id', true), ''),
                        current_user, current_database()
                    """)) {
                fila.next();
                assertThat(fila.getString(2)).isNull();
                assertThat(fila.getString(3)).isEqualTo("barberturno_v2_app");
                assertThat(fila.getString(4)).isEqualTo("barberturno_v2_test");
                return fila.getInt(1);
            }
        });
    }

    private record Sesion(String slug, CookieManager cookies, HttpClient cliente) { }
    private record Resultado(int codigo, String cuerpo, Traza traza) { }
    private static class Traza {
        volatile boolean entrada;
        volatile boolean contextoLimpio;
        final List<String> controladores = new ArrayList<>();
        final CountDownLatch fin = new CountDownLatch(1);
    }

    /** Observa entrada y handler sin modificar las peticiones, los rechazos ni el contexto. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Observacion implements WebMvcConfigurer {
        private final Map<String, Traza> trazas = new ConcurrentHashMap<>();

        @Bean FilterRegistrationBean<Filter> observarEntrada(ContextoNegocio contexto) {
            Filter filtro = (peticion, respuesta, cadena) -> {
                var traza = trazas.get(((HttpServletRequest) peticion).getHeader("X-Prueba-Ruta"));
                if (traza == null) { cadena.doFilter(peticion, respuesta); return; }
                traza.entrada = true;
                try { cadena.doFilter(peticion, respuesta); }
                finally {
                    traza.contextoLimpio = !contexto.delimitado();
                    traza.fin.countDown();
                }
            };
            var registro = new FilterRegistrationBean<>(filtro);
            registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registro;
        }

        @Override public void addInterceptors(InterceptorRegistry registro) {
            registro.addInterceptor(new HandlerInterceptor() {
                @Override public boolean preHandle(HttpServletRequest peticion,
                        jakarta.servlet.http.HttpServletResponse respuesta, Object handler) {
                    var traza = trazas.get(peticion.getHeader("X-Prueba-Ruta"));
                    if (traza != null && handler instanceof HandlerMethod metodo) {
                        traza.controladores.add(metodo.getBeanType().getSimpleName() + "." + metodo.getMethod().getName());
                    }
                    return true;
                }
            });
        }
    }
}
