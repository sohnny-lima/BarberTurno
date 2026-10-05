package pe.barberturno.common.web;

import java.util.List;
import java.util.Optional;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.users.Rol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SpaForwardIT {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/agenda", "/reservar", "/login", "/admin/usuarios", "/mis-citas"})
    void navegar_conHtml_reenviaAEntradaAngular(String ruta) throws Exception {
        mvc.perform(get(ruta).accept("text/html"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void navegar_conContextPath_conservaLaRutaDeReenvio() throws Exception {
        mvc.perform(get("/barberturno/agenda").contextPath("/barberturno").accept("text/html"))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/html;q=0", "valor-invalido"})
    void consultarRuta_sinAceptarHtml_noReenvia(String accept) throws Exception {
        mvc.perform(get("/agenda").header("Accept", accept))
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @Test
    void consultarRuta_sinAccept_noReenvia() throws Exception {
        mvc.perform(get("/agenda"))
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @Test
    void consultarArchivos_sirveHtmlYJavascriptSinReenvio() throws Exception {
        mvc.perform(get("/index.html").accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Entrada Angular de prueba")))
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
        mvc.perform(get("/main-prueba.js").accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("console.log(")))
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
        mvc.perform(head("/main-prueba.js")).andExpect(status().isOk());
        mvc.perform(get("/ausente.js").accept("text/html"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/x", "/api", "/api/perfil", "/api/reservas", "/api/reservas/mias",
            "/api/notificaciones", "/api/usuarios", "/api/reportes/resumen", "/actuator/env",
            "/v3/api-docs", "/swagger-ui", "/swagger-ui/index.html"})
    void consultarRutaProtegida_sinSesionConHtml_sigueCerrada(String ruta) throws Exception {
        mvc.perform(get(ruta).accept("text/html"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/%61pi/perfil", "/%61pi/usuarios", "/%61ctuator/env", "/v3/%61pi-docs"})
    void consultarPrefijoCodificado_noEludeLaAutorizacion(String ruta) throws Exception {
        mvc.perform(get(URI.create(ruta)).accept("text/html"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/usuarios", "/api/reportes/resumen", "/api/reservas"})
    @WithMockUser(roles = "CLIENTE")
    void consultarGestion_conClienteConHtml_conservaLaAutorizacion(String ruta) throws Exception {
        mvc.perform(get(ruta).accept("text/html")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @WithMockUser(roles = "ADMIN")
    void escribirRutaAngular_conCsrfYAdmin_noAbreEscrituras(String metodo) throws Exception {
        // CSRF real (cookie + cabecera): el postprocesador csrf() sustituiría el repositorio del
        // contexto compartido por uno de sesión y rompería las clases que se ejecutan después.
        var xsrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        mvc.perform(request(HttpMethod.valueOf(metodo), "/agenda").cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()).accept("text/html"))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @Test
    void consultarHealth_sigueSiendoJsonSinDetalles() throws Exception {
        mvc.perform(get("/actuator/health").accept("application/json"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(result -> assertThat(result.getResponse().getForwardedUrl()).isNull());
    }

    @Test
    void cargarSpa_conPasswordTemporal_permitaCambiarPasswordSinAbrirApi() throws Exception {
        var usuario = new UsuarioAutenticado(1L, Rol.BARBERO, Optional.empty(), true);
        var auth = new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_BARBERO")));
        mvc.perform(get("/agenda").accept("text/html").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/main-prueba.js").with(authentication(auth))).andExpect(status().isOk());
        mvc.perform(get("/api/reservas").with(authentication(auth)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }
}
