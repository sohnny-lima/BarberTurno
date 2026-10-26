package pe.barberturno.common.tenancy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriUtils;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.RolNegocio;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CE-8: rutas crudas conservadas, filtros reales y reutilización del pool de una conexión. */
class RutasCanonicasIT extends PeticionNegocioPruebaBase {
    static Stream<Arguments> variantes() {
        var codificadas = Stream.of(
                "/api/n/a/%70erfil", "/api/n/a/%61uth/password", "/api/%6E/a/servicios",
                "/api/n/%61/servicios", "/api/%70erfil", "/%61pi/n/a/servicios",
                "/%61pi/servicios", "/api/%61uth/password", "/api/n/a/%73ervicios",
                "/api/%73ervicios", "/api/%70lataforma/negocios", "/api/plataforma/%6eegocios",
                "/api/n/a/%70lataforma/negocios", "/api/n/a/plataforma/%6eegocios",
                "/api/n/a/servicios%2F", "/api/servicios%2F", "/api/n/a/%2E/perfil",
                "/api/%2E/perfil", "/api/n/a/%25perfil", "/api/%25perfil")
                .map(ruta -> Arguments.of(ruta, 400));
        var firewall = Stream.of("/api/n/a//perfil", "/api//perfil", "/api/n/a/./perfil",
                "/api/./perfil", "/api/n/a/../perfil", "/api/../perfil", "/api/n/a/perfil;param",
                "/api/perfil;param", "/api/n/a/servicios\\perfil", "/api/servicios\\perfil")
                .map(ruta -> Arguments.of(ruta, 400));
        var noCanonicas = Stream.of("/api/n/a/PERFIL", "/api/PERFIL", "/API/n/a/perfil", "/API/perfil",
                "/api/N/a/servicios", "/api/n/A/servicios", "/api/n/a/servicios/", "/api/servicios/",
                "/api/n/a/perfil/", "/api/perfil/", "/api/n/a/auth/password/", "/api/auth/password/",
                "/api/PLATAFORMA/negocios", "/api/plataforma/negocios/", "/api/n/a/plataforma/negocios")
                .map(ruta -> Arguments.of(ruta, 404));
        return Stream.of(codificadas, firewall, noCanonicas).flatMap(s -> s);
    }

    @ParameterizedTest(name = "{0} → {1}") @MethodSource("variantes")
    void variante_rechazaSinControladorNiEfectos_yLaSiguientePeticionNoHeredaContexto(String ruta, int estado)
            throws Exception {
        var actor = personal("rutas", "a", RolNegocio.PROPIETARIO);
        membresias.save(new Membresia(directorio.buscar("barberturno").orElseThrow(), actor.getId(),
                RolNegocio.PROPIETARIO, reloj.instant()));
        var cookie = sesion(actor);
        servicio("a"); servicio("b"); servicio("barberturno");
        int conexion = conexionLimpia();

        // Acredita que la sesión y el rol permiten la forma canónica por prefijo y por alias.
        mvc.perform(get("/api/n/a/usuarios").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").cookie(cookie)).andExpect(status().isOk());
        for (HttpMethod metodo : new HttpMethod[]{HttpMethod.GET,
                ruta.contains("password") ? HttpMethod.PUT : HttpMethod.POST}) {
            // Conserva barras invertidas sin que URI.create las rechace en el cliente de pruebas.
            var solicitud = csrf(request(metodo, URI.create("/")).contentType("application/json")
                    .content("{\"nombre\":\"No debe escribirse\",\"descripcion\":\"Ficticio\",\"duracionMin\":30,\"precio\":20}"), cookie)
                    .with(req -> {
                        req.setRequestURI(ruta);
                        req.setServletPath(UriUtils.decode(ruta, StandardCharsets.UTF_8));
                        return req;
                    });
            var resultado = mvc.perform(solicitud).andExpect(status().is(estado))
                        .andExpect(content().contentType("application/problem+json"))
                        .andExpect(jsonPath("$.codigo").value(estado == 400 ? "VALIDACION" : "NO_ENCONTRADO"))
                        .andExpect(jsonPath("$.status").value(estado)).andReturn();
                assertThat(resultado.getRequest().getRequestURI()).isEqualTo(ruta);
                assertThat(resultado.getHandler()).as("No debe ejecutarse MVC para %s", ruta).isNull();
            assertThat(contexto.delimitado()).isFalse();
            assertThat(conexionLimpia()).isEqualTo(conexion);

            mvc.perform(get("/api/n/b/servicios").cookie(cookie)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("Corte b"));
            mvc.perform(get("/api/perfil").cookie(cookie)).andExpect(status().isOk());
            assertThat(conexionLimpia()).isEqualTo(conexion);
        }
        assertThat(this.<Long>en("a", servicios::count)).isEqualTo(1);
        assertThat(this.<Long>en("b", servicios::count)).isEqualTo(1);
        assertThat(this.<Long>en("barberturno", servicios::count)).isEqualTo(1);
    }

    private int conexionLimpia() {
        assertThat(contexto.delimitado()).isFalse();
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
}
