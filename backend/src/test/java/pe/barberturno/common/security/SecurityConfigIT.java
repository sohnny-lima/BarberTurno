package pe.barberturno.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIT {
    @Autowired private MockMvc mockMvc;

    @Test
    void consultarHealth_sinAutenticacion_devuelveUpSinDetallesNiSesion() throws Exception {
        var resultado = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();
        assertThat(resultado.getResponse().getContentAsString()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(resultado.getRequest().getSession(false)).isNull();
        assertThat(resultado.getResponse().getCookie("JSESSIONID")).isNull();
        mockMvc.perform(head("/actuator/health")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"})
    void solicitarApi_sinAutenticacionConCsrf_devuelve401SinCrearSesion(String metodo) throws Exception {
        var xsrf = mockMvc.perform(get("/api/auth/sesion")).andReturn()
                .getResponse().getCookie("XSRF-TOKEN");
        var resultado = mockMvc.perform(request(HttpMethod.valueOf(metodo), "/api/cualquier-ruta")
                .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO")).andReturn();
        assertThat(resultado.getRequest().getSession(false)).isNull();
        assertThat(resultado.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void solicitarEscritura_sinCsrf_devuelve403(String metodo) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(metodo), "/api/cualquier-ruta"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html",
            "/swagger-ui/index.html", "/actuator/env", "/actuator/info", "/actuator/health/db", "/login"})
    void solicitarRutaNoHabilitada_sinAutenticacion_devuelve401(String ruta) throws Exception {
        mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void solicitarSesion_sinCookie_exponeProblemDetailYCsrf() throws Exception {
        mockMvc.perform(get("/api/auth/sesion")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.instance").value("/api/auth/sesion"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void solicitarApi_conUsuarioSimulado_seDeniegaConProblemDetailHastaImplementarRuta() throws Exception {
        mockMvc.perform(get("/api/cualquier-ruta")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.instance").value("/api/cualquier-ruta"));
    }
}
