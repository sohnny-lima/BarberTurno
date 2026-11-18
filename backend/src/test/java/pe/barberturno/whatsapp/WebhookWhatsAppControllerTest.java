package pe.barberturno.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookWhatsAppControllerTest {
    final RecepcionWebhookService recepcion = mock(RecepcionWebhookService.class);

    WebhookWhatsAppController controlador(String token, String secreto) {
        return new WebhookWhatsAppController(new FirmaWebhook(new WhatsAppPropiedades(token, secreto, null)), recepcion);
    }

    static MockHttpServletRequest peticion(byte[] cuerpo) {
        var peticion = new MockHttpServletRequest("POST", "/api/whatsapp/webhook");
        peticion.setContent(cuerpo);
        return peticion;
    }

    @Test void sinConfiguracion_verificacion403YNotificacion401SinGuardarNada() throws Exception {
        var sinNada = controlador("", "");
        assertThat(sinNada.verificar("subscribe", "", "123").getStatusCode().value()).isEqualTo(403);
        assertThat(sinNada.verificar("subscribe", null, "123").getStatusCode().value()).isEqualTo(403);
        byte[] cuerpo = FirmaWebhookTest.CUERPO;
        assertThat(sinNada.recibir(peticion(cuerpo), "sha256=" + "0".repeat(64)).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(recepcion);
    }

    @Test void verificacion_soloSubscribeConTokenYDesafioSeguro() {
        var configurado = controlador("token-ficticio", FirmaWebhookTest.SECRETO);
        var correcta = configurado.verificar("subscribe", "token-ficticio", "1158201444");
        assertThat(correcta.getStatusCode().value()).isEqualTo(200);
        assertThat(correcta.getBody()).isEqualTo("1158201444");
        assertThat(correcta.getHeaders().getContentType()).hasToString("text/plain");
        assertThat(configurado.verificar("unsubscribe", "token-ficticio", "1").getStatusCode().value()).isEqualTo(403);
        assertThat(configurado.verificar(null, "token-ficticio", "1").getStatusCode().value()).isEqualTo(403);
        assertThat(configurado.verificar("subscribe", "otro", "1").getStatusCode().value()).isEqualTo(403);
        assertThat(configurado.verificar("subscribe", "token-ficticio", null).getStatusCode().value()).isEqualTo(403);
        assertThat(configurado.verificar("subscribe", "token-ficticio", "<script>").getStatusCode().value()).isEqualTo(403);
        assertThat(configurado.verificar("subscribe", "token-ficticio", "x".repeat(257)).getStatusCode().value()).isEqualTo(403);
    }

    @Test void cuerpoMayorDeUnMiB_413AntesDeLaFirmaYSinGuardar() throws Exception {
        var configurado = controlador("token-ficticio", FirmaWebhookTest.SECRETO);
        byte[] grande = new byte[WebhookWhatsAppController.LIMITE_BYTES + 1];
        assertThat(configurado.recibir(peticion(grande), FirmaWebhookTest.firmar(FirmaWebhookTest.SECRETO, grande))
                .getStatusCode().value()).isEqualTo(413);
        // Sin Content-Length (transferencia por bloques): la lectura acotada también lo detecta.
        var sinLongitud = new MockHttpServletRequest("POST", "/api/whatsapp/webhook") {
            @Override public long getContentLengthLong() { return -1; }
        };
        sinLongitud.setContent(grande);
        assertThat(configurado.recibir(sinLongitud, null).getStatusCode().value()).isEqualTo(413);
        byte[] limite = new byte[WebhookWhatsAppController.LIMITE_BYTES];
        assertThat(configurado.recibir(peticion(limite), null).getStatusCode().value()).as("en el límite: firma").isEqualTo(401);
        verifyNoInteractions(recepcion);
    }

    @Test void firmaValida_delegaLosBytesExactosY200() throws Exception {
        var configurado = controlador("token-ficticio", FirmaWebhookTest.SECRETO);
        byte[] cuerpo = FirmaWebhookTest.CUERPO;
        assertThat(configurado.recibir(peticion(cuerpo), FirmaWebhookTest.firmar(FirmaWebhookTest.SECRETO, cuerpo))
                .getStatusCode().value()).isEqualTo(200);
        verify(recepcion).recibir(cuerpo);
    }
}
