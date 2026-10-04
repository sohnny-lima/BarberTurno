package pe.barberturno.common.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CabecerasSeguridadIT {
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'self'; "
            + "form-action 'self'; object-src 'none'; frame-ancestors 'none'";
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/agenda", "/index.html", "/main-prueba.js", "/api/servicios", "/api/perfil",
            "/actuator/health", "/ausente.js"})
    void responder_enHttpLocal_imponeCabecerasSinHsts(String ruta) throws Exception {
        mvc.perform(get(ruta).accept("*/*"))
                .andExpect(header().string("Content-Security-Policy", CSP))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/agenda", "/api/servicios", "/api/perfil", "/actuator/health"})
    void responder_enHttps_imponeHstsYCsp(String ruta) throws Exception {
        mvc.perform(get(ruta).secure(true).accept("*/*"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("Content-Security-Policy", CSP));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reservas", "/api/usuarios/1/estado"})
    void rechazarCsrf_tambienIncluyeCabeceras(String ruta) throws Exception {
        mvc.perform(post(ruta)).andExpect(status().isForbidden())
                .andExpect(header().string("Content-Security-Policy", CSP))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}
