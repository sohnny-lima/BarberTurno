package pe.barberturno.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * La exención de CSRF se limita a {@code POST /api/whatsapp/webhook} exacto (V2-DA-52; hallazgo
 * H-124-01 de Codex): una ruta con sufijo, con prefijo de negocio, con barra final o con otro método no
 * queda exenta. Prueba directa del criterio, porque desde V2-07 esas rutas se rechazan antes con 404 y la
 * prueba HTTP ya no distingue el criterio de exención.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class ExencionCsrfWebhookTest {

    @ParameterizedTest(name = "{0} {1} → exenta: {2}")
    @CsvSource({
            "POST, /api/whatsapp/webhook, true",
            "GET, /api/whatsapp/webhook, false",
            "PUT, /api/whatsapp/webhook, false",
            "PATCH, /api/whatsapp/webhook, false",
            "DELETE, /api/whatsapp/webhook, false",
            "POST, /api/whatsapp/webhook/, false",
            "POST, /api/whatsapp/webhook/otra, false",
            "POST, /api/whatsapp/webhookx, false",
            "POST, /api/whatsapp, false",
            "POST, /api/n/a/whatsapp/webhook, false",
            "POST, /API/whatsapp/webhook, false",
            "post, /api/whatsapp/webhook, false"
    })
    void esWebhookWhatsApp_soloElPostExacto(String metodo, String ruta, boolean exenta) {
        var peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setServletPath(ruta);
        assertThat(SecurityConfig.esWebhookWhatsApp(peticion)).isEqualTo(exenta);
    }
}
