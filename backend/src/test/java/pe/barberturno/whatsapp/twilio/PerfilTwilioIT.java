package pe.barberturno.whatsapp.twilio;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pe.barberturno.auth.JwtService;
import pe.barberturno.common.config.ConfiguracionProduccion;
import pe.barberturno.common.security.SecurityConfig;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import pe.barberturno.users.UsuarioRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración del perfil {@code twilio} y sus restricciones de seguridad (CT-7).
 * Valida que con el perfil activo se carguen los beans del paquete, que las rutas
 * {@code POST /api/twilio/webhook} y {@code POST /api/twilio/estado} estén exentas de CSRF,
 * que cualquier otra escritura siga requiriendo CSRF (la exención no se amplía),
 * y que la combinación {@code prod} + {@code twilio} sea rechazada de forma estricta al arrancar.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=",
        "barberturno.admin.password=",
        "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "barberturno.whatsapp.twilio.account-sid=AC0123456789abcdef0123456789abcdef",
        "barberturno.whatsapp.twilio.auth-token=token-ficticio-twilio-it",
        "barberturno.whatsapp.twilio.canales[0].numero=+14155238886",
        "barberturno.whatsapp.twilio.canales[0].negocio-slug=barberturno",
        "barberturno.whatsapp.twilio.numero=+14155238886",
        "barberturno.whatsapp.twilio.negocio-slug=barberturno",
        "barberturno.whatsapp.twilio.respuesta-twiml=false",
        "barberturno.whatsapp.twilio.url-publica=https://pruebas.barberturno.test"
})
@ActiveProfiles({"test", "twilio"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PerfilTwilioIT {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private UsuarioRepository usuarios;

    @Test
    void beansTwilio_existenEnContextoConPerfilActivo() {
        assertThat(context.getBeanNamesForType(WebhookTwilioController.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(ClienteTwilioWhatsApp.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(RegistroCanalTwilioService.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(TwilioPropiedades.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(FirmaTwilio.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(TraductorEntradaTwilio.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(CorrespondenciaMenusTwilio.class)).isNotEmpty();
    }

    @Test
    void exencionCsrf_permiteWebhookTwilioSinTokenCsrf() throws Exception {
        // Al estar exento de CSRF, la petición alcanza el controlador (responde 401 por falta de firma, no 403 por CSRF)
        mvc.perform(post("/api/twilio/webhook")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .content("Body=hola"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exencionCsrf_permiteEstadoTwilioSinTokenCsrf() throws Exception {
        // La ruta de estado también está exenta de CSRF y responde 401 por firma ausente, no 403
        mvc.perform(post("/api/twilio/estado")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .content("MessageStatus=delivered"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exencionCsrf_noSeAmpliaAOtrasEscrituras_devuelve403Forbidden() throws Exception {
        // Cualquier escritura protegida fuera de los dos webhooks exactos debe exigir CSRF y dar 403
        // /api/auth/login existe y es una escritura con CSRF obligatorio (las rutas legadas dan 404).
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@ejemplo.test\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exencionCsrf_escrituraConSesionValidaSinCsrf_devuelve403Forbidden() throws Exception {
        Usuario usuario = usuarios.saveAndFlush(new Usuario("Usuario CSRF", "usuario-csrf@ejemplo.test",
                "999000004", "hash-ficticio", Rol.CLIENTE, Instant.now(), Instant.now()));
        try {
            String sesionJwt = jwt.emitir(usuario);

            // Escritura protegida existente con sesión válida pero sin CSRF: debe dar 403 Forbidden
            mvc.perform(post("/api/n/barberturno/reservas")
                            .cookie(new jakarta.servlet.http.Cookie("BT_SESION", sesionJwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Juan\"}"))
                    .andExpect(status().isForbidden());
        } finally {
            usuarios.delete(usuario);
        }
    }

    @Test
    void exencionCsrf_escrituraConCsrfInvalido_devuelve403Forbidden() throws Exception {
        // Con token CSRF no coincidente entre cookie y cabecera
        mvc.perform(post("/api/auth/login")
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "token-esperado"))
                        .header("X-XSRF-TOKEN", "token-distinto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@ejemplo.test\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} {1} → exenta: {2}")
    @CsvSource({
            "POST, /api/twilio/webhook, true",
            "POST, /api/twilio/estado, true",
            "GET, /api/twilio/webhook, false",
            "GET, /api/twilio/estado, false",
            "PUT, /api/twilio/webhook, false",
            "PATCH, /api/twilio/webhook, false",
            "DELETE, /api/twilio/webhook, false",
            "POST, /api/twilio/webhook/, false",
            "POST, /api/twilio/webhook/otra, false",
            "POST, /api/twilio/webhookx, false",
            "POST, /api/twilio/estado/, false",
            "POST, /api/twilio/estado/otra, false",
            "POST, /api/twilio/estadox, false",
            "POST, /api/twilio, false",
            "POST, /api/n/barberturno/twilio/webhook, false",
            "POST, /API/twilio/webhook, false",
            "post, /api/twilio/webhook, false"
    })
    void esWebhookTwilio_soloLosPostExactos(String metodo, String ruta, boolean exenta) throws Exception {
        var peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setServletPath(ruta);
        Method metodoEsWebhook = SecurityConfig.class.getDeclaredMethod(
                "esWebhookTwilio", jakarta.servlet.http.HttpServletRequest.class);
        metodoEsWebhook.setAccessible(true);
        boolean resultado = (boolean) metodoEsWebhook.invoke(null, peticion);
        assertThat(resultado).isEqualTo(exenta);
    }

    @Test
    void exencionCsrf_noSeAmpliaASubrutasDeTwilio() throws Exception {
        // Una variante de ruta como /api/twilio/webhook/otra no está exenta ni es global (404/403)
        mvc.perform(post("/api/twilio/webhook/otra")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void configuracionProduccion_prohibePerfilProdCombinadoConTwilio() {
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("BT_DB_URL", "jdbc:postgresql://localhost:5433/barberturno")
                .withProperty("BT_DB_USER", "barberturno")
                .withProperty("BT_DB_PASSWORD", "clave-ficticia")
                .withProperty("BT_JWT_SECRET", Base64.getEncoder().encodeToString(new byte[32]))
                .withProperty("BT_ADMIN_CORREO", "admin@ejemplo.test")
                .withProperty("BT_ADMIN_PASSWORD", "clave-ficticia")
                .withProperty("BT_ADMIN_NOMBRE", "Admin Demo")
                .withProperty("BT_COOKIE_SECURE", "true");
        entorno.setActiveProfiles("prod", "twilio");

        assertThatThrownBy(() ->
                ConfiguracionProduccion.validarVariablesProduccion(entorno)
                        .postProcessBeanFactory(new DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El perfil prod no puede combinarse con twilio.");
    }
}
