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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void consultarHealth_sinAutenticacion_devuelveUpSinDetallesNiSesion() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(resultado.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"})
    void solicitarApi_sinAutenticacion_devuelve401SinCrearSesion(String metodo) throws Exception {
        MvcResult resultado = mockMvc.perform(request(HttpMethod.valueOf(metodo), "/api/cualquier-ruta"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html",
            "/swagger-ui/index.html", "/actuator/env", "/actuator/info", "/actuator/health/db", "/login"})
    void solicitarRutaNoHabilitada_sinAutenticacion_devuelve401(String ruta) throws Exception {
        mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    }

    @Test
    void solicitarApi_real_sinSesion_exponePendienteProblemDetailParaT10() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/auth/sesion"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(resultado.getResponse().getContentAsString()).isEmpty();
        assertThat(resultado.getResponse().getContentType()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void solicitarApi_conUsuarioSimulado_tambienSeDeniegaDuranteElEsqueleto() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/cualquier-ruta"))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(resultado.getResponse().getContentAsString()).isEmpty();
        assertThat(resultado.getResponse().getContentType()).isNull();
    }
}
