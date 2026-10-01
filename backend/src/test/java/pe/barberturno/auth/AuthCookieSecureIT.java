package pe.barberturno.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pe.barberturno.support.LimpiezaBaseDatos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.seguridad.cookie-secure=true",
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre="})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuthCookieSecureIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void preparar() { LimpiezaBaseDatos.limpiar(jdbc); }
    @AfterEach void limpiar() { LimpiezaBaseDatos.limpiar(jdbc); }

    @Test void cookie_seguraConfigurada_seAplicaASesionCsrfCambioPasswordYLogout() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/auth/sesion").secure(true))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getSecure()).isTrue();
        assertThat(xsrf.isHttpOnly()).isFalse();
        var r = mvc.perform(post("/api/auth/registro").secure(true).cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content("""
                        {"nombre":"Cliente de prueba","correo":"seguro@ejemplo.test","telefono":"999111222",
                         "password":"ClaveCliente123","aceptaPrivacidad":true}
                        """)).andExpect(status().isCreated()).andReturn();
        Cookie sesion = r.getResponse().getCookie("BT_SESION");
        assertThat(sesion).isNotNull();
        assertThat(sesion.getSecure()).isTrue();
        assertThat(sesion.isHttpOnly()).isTrue();
        assertThat(sesion.getMaxAge()).isEqualTo(28800);
        assertThat(sesion.getPath()).isEqualTo("/");
        assertThat(sesion.getAttribute("SameSite")).isEqualTo("Strict");
        r = mvc.perform(put("/api/auth/password").secure(true).cookie(xsrf, sesion)
                .header("X-XSRF-TOKEN", xsrf.getValue()).contentType("application/json")
                .content("""
                        {"passwordActual":"ClaveCliente123","passwordNueva":"OtraClave456"}
                        """)).andExpect(status().isNoContent()).andReturn();
        sesion = r.getResponse().getCookie("BT_SESION");
        assertThat(sesion).isNotNull();
        assertThat(sesion.getSecure()).isTrue();
        assertThat(sesion.isHttpOnly()).isTrue();
        assertThat(sesion.getMaxAge()).isEqualTo(28800);
        assertThat(sesion.getPath()).isEqualTo("/");
        assertThat(sesion.getAttribute("SameSite")).isEqualTo("Strict");
        mvc.perform(get("/api/perfil").secure(true).cookie(sesion)).andExpect(status().isOk());
        r = mvc.perform(post("/api/auth/logout").secure(true).cookie(xsrf, sesion)
                .header("X-XSRF-TOKEN", xsrf.getValue())).andExpect(status().isNoContent()).andReturn();
        Cookie borrada = r.getResponse().getCookie("BT_SESION");
        assertThat(borrada.getMaxAge()).isZero();
        assertThat(borrada.getSecure()).isTrue();
        assertThat(borrada.isHttpOnly()).isTrue();
        assertThat(borrada.getPath()).isEqualTo("/");
        assertThat(borrada.getAttribute("SameSite")).isEqualTo("Strict");
    }
}