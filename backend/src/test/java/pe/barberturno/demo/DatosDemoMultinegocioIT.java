package pe.barberturno.demo;

import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pe.barberturno.BarberTurnoApplication;
import pe.barberturno.common.tenancy.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;

/** Demo real en PostgreSQL v2 con rol de aplicación, dos ámbitos, reinicio y reservas por HTTP. */
class DatosDemoMultinegocioIT {
    static final String CLAVE = "ClaveDemoPrueba32";

    @Test void escenarioIdempotente_dosNegociosCincoPapeles_catalogoJornadasYReservasReales() throws Exception {
        try (var c = new SpringApplicationBuilder(BarberTurnoApplication.class).run(
                "--spring.profiles.active=test,demo", "--server.port=0", "--spring.main.banner-mode=off",
                "--logging.level.root=WARN", "--barberturno.admin.correo=", "--barberturno.admin.password=",
                "--barberturno.admin.nombre=", "--BT_DEMO_PASSWORD=", "--barberturno.reloj-fijo=2026-09-28T09:00:00-05:00")) {
            var sql = c.getBean(JdbcNegocioPruebas.class);
            assertThat(sql.entorno().getRequiredProperty("spring.datasource.url")).endsWith("/barberturno_v2_test");
            assertThat(sql.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
            var ejecutor = c.getBean(EjecutorEnNegocio.class);
            var usuarios = c.getBean(UsuarioRepository.class);
            var datos = c.getBean(DatosDemoMultinegocioService.class);
            var runner = new DatosDemoMultinegocioRunner(datos, ejecutor, "barberturno", CLAVE);
            LimpiezaBaseDatos.limpiar(sql);
            try {
                assertThat(sql.queryForObject("select count(*) from negocio where slug='sur'", Long.class)).isZero();
                c.getBean(DatosDemoMultinegocioRunner.class).run(new DefaultApplicationArguments());
                assertThat(usuarios.findByCorreo("plataforma@ejemplo.test")).isEmpty();
                assertThatThrownBy(() -> datos.preparar("invalida")).isInstanceOf(IllegalStateException.class).hasMessageContaining("RN-25");
                assertThat(sql.queryForObject("select count(*) from negocio where slug='sur'", Long.class)).isZero();
                ejecutor.ejecutar("barberturno", () -> { c.getBean(DatosDemoService.class).cargar(CLAVE); return null; });
                runner.run(new DefaultApplicationArguments());
                var antes = huella(sql, ejecutor);
                runner.run(new DefaultApplicationArguments());
                new DatosDemoMultinegocioRunner(datos, ejecutor, "barberturno", "OtraClavePrueba32")
                        .run(new DefaultApplicationArguments());
                assertThat(huella(sql, ejecutor)).isEqualTo(antes);
                assertThat(sql.queryForList("select slug from negocio order by slug", String.class)).containsExactly("barberturno", "sur");
                assertThat(sql.queryForObject("select horizonte_dias from negocio where slug='barberturno'", Integer.class)).isEqualTo(30);
                assertThat(sql.queryForObject("select horizonte_dias from negocio where slug='sur'", Integer.class)).isEqualTo(14);
                assertThat(sql.queryForObject("select confirmacion_manual from negocio where slug='barberturno'", Boolean.class)).isFalse();
                assertThat(sql.queryForObject("select confirmacion_manual from negocio where slug='sur'", Boolean.class)).isTrue();
                var membresias = c.getBean(pe.barberturno.platform.MembresiaRepository.class);
                var directorio = c.getBean(DirectorioNegocios.class);
                for (var esperado : Map.of("multirol", List.of("PROPIETARIO", "BARBERO"),
                        "personal-cliente", List.of("BARBERO", "-"), "encargado", List.of("ENCARGADO", "PROPIETARIO"),
                        "plataforma", List.of("-", "-"), "cliente", List.of("-", "-")).entrySet()) {
                    var persona = usuarios.findByCorreo(esperado.getKey() + "@ejemplo.test").orElseThrow();
                    assertThat(c.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
                            .matches(CLAVE, persona.getPasswordHash())).isTrue();
                    for (int i = 0; i < 2; i++) {
                        var rol = membresias.findByNegocioIdAndUsuarioId(directorio.buscar(i == 0 ? "barberturno" : "sur").orElseThrow(), persona.getId())
                                .filter(pe.barberturno.platform.Membresia::isActiva).map(m -> m.getRol().name()).orElse("-");
                        assertThat(rol).isEqualTo(esperado.getValue().get(i));
                    }
                }
                assertThat(usuarios.findByCorreo("plataforma@ejemplo.test").orElseThrow().getRolPlataforma())
                        .isEqualTo(pe.barberturno.platform.RolPlataforma.ADMIN_PLATAFORMA);
                assertThat(sql.queryForObject("select count(*) from usuario where correo not like '%@ejemplo.test'", Long.class)).isZero();
                assertThat(ejecutor.<Long>ejecutar("barberturno", () -> sql.queryForObject("select count(*) from cliente where usuario_id=(select id from usuario where correo='personal-cliente@ejemplo.test') and activo", Long.class))).isOne();
                for (String slug : List.of("barberturno", "sur")) {
                    assertThat(ejecutor.<Long>ejecutar(slug, () -> sql.queryForObject("select count(*) from cliente where usuario_id=(select id from usuario where correo='cliente@ejemplo.test') and activo", Long.class))).isOne();
                }
                int puerto = c.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
                reservar(c.getBean(tools.jackson.databind.json.JsonMapper.class), puerto, "barberturno", "personal-cliente", true);
                reservar(c.getBean(tools.jackson.databind.json.JsonMapper.class), puerto, "sur", "cliente", false);
                // Un reinicio no deshace bajas ni cambios posteriores de configuración.
                var m = membresias.findByNegocioIdAndUsuarioId(directorio.buscar("sur").orElseThrow(), usuarios.findByCorreo("multirol@ejemplo.test").orElseThrow().getId()).orElseThrow();
                m.revocar(pe.barberturno.platform.MotivoRevocacion.SALIDA, java.time.Instant.parse("2026-09-28T14:00:00Z")); membresias.save(m);
                sql.update("update negocio set horizonte_dias=9 where slug='sur'");
                antes = huella(sql, ejecutor);
                runner.run(new DefaultApplicationArguments());
                assertThat(huella(sql, ejecutor)).isEqualTo(antes);
            } finally {
                LimpiezaBaseDatos.limpiar(sql);
            }
        }
    }

    Map<String, Object> huella(JdbcNegocioPruebas sql, EjecutorEnNegocio ejecutor) {
        var r = new LinkedHashMap<String, Object>();
        for (String tabla : List.of("usuario", "negocio", "membresia")) r.put(tabla, sql.queryForList("select * from " + tabla + " order by id"));
        for (String slug : List.of("barberturno", "sur")) {
            for (String tabla : List.of("cliente", "servicio", "barbero", "jornada", "reserva", "auditoria_reserva", "notificacion")) {
                r.put(slug + tabla, ejecutor.ejecutar(slug, () -> sql.queryForList("select * from " + tabla + " order by id")));
            }
        }
        return r;
    }

    void reservar(tools.jackson.databind.json.JsonMapper mapper, int puerto, String slug, String alias, boolean papel) throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (var http = HttpClient.newBuilder().cookieHandler(cookies).build()) {
            String base = "http://127.0.0.1:" + puerto;
            http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/sesion")).GET().build(), HttpResponse.BodyHandlers.ofString());
            String csrf = token(cookies);
            var login = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login")).header("X-XSRF-TOKEN", csrf)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                            "{\"correo\":\"" + alias + "@ejemplo.test\",\"password\":\"" + CLAVE + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
            String prefijo = base + "/api/n/" + slug;
            var catalogo = http.send(HttpRequest.newBuilder(URI.create(prefijo + "/servicios")).GET().build(), HttpResponse.BodyHandlers.ofString());
            long servicio = mapper.readTree(catalogo.body()).get(0).get("id").asLong();
            var disp = http.send(HttpRequest.newBuilder(URI.create(prefijo + "/disponibilidad?fecha=2026-10-02&servicioId=" + servicio)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(disp.statusCode()).isEqualTo(200);
            var franjas = mapper.readTree(disp.body()).get("franjas");
            assertThat(franjas).isNotEmpty();
            var franja = franjas.get(0);
            var p = HttpRequest.newBuilder(URI.create(prefijo + "/reservas")).header("X-XSRF-TOKEN", token(cookies)).header("Content-Type", "application/json");
            if (papel) p.header("X-BT-Papel", "CLIENTE");
            var reserva = http.send(p.POST(HttpRequest.BodyPublishers.ofString("{\"servicioId\":" + servicio
                    + ",\"barberoId\":" + franja.get("barberoIds").get(0).asLong() + ",\"inicio\":\"" + franja.get("inicio").asString() + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(reserva.statusCode()).as(reserva.body()).isEqualTo(201);
            assertThat(mapper.readTree(reserva.body()).get("estado").asString()).isEqualTo(slug.equals("sur") ? "PENDIENTE" : "CONFIRMADA");
        } finally { cookies.getCookieStore().removeAll(); }
    }

    String token(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElseThrow();
    }

    @Test void sinDemo_oConProd_noExistenLosBeansDeCarga() {
        for (String perfiles : List.of("test", "dev", "demo,prod")) {
            new ApplicationContextRunner().withUserConfiguration(DatosDemoMultinegocioService.class, DatosDemoMultinegocioRunner.class)
                    .withInitializer(c -> c.getEnvironment().setActiveProfiles(perfiles.split(",")))
                    .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(DatosDemoMultinegocioService.class).doesNotHaveBean(DatosDemoMultinegocioRunner.class));
        }
    }
}
