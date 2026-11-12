package pe.barberturno.whatsapp.twilio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.barberturno.whatsapp.RecepcionWebhookService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pruebas unitarias de {@link WebhookTwilioController} sin base de datos (CT-1, 20-v2-twilio-sandbox.md §5).
 * Valida la autenticación HMAC-SHA1 por formulario, el rechazo ante cabeceras ausentes, URL ajena,
 * token alterado, parámetros duplicados o manipulados, query string y exceso de tamaño (413),
 * así como la absorción inocua (200 sin efectos) ante destinos ajenos o remitentes no WhatsApp.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class WebhookTwilioControllerTest {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "token-ficticio-twilio-12345";
    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String RUTA_WEBHOOK = "/api/twilio/webhook";
    private static final String RUTA_ESTADO = "/api/twilio/estado";
    private static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");

    private RecepcionWebhookService recepcion;
    private WebhookTwilioController controlador;
    private TwilioPropiedades propiedades;
    private CorrespondenciaMenusTwilio menus;

    @BeforeEach
    void preparar() {
        recepcion = mock(RecepcionWebhookService.class);
        propiedades = new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_SANDBOX, URL_PUBLICA,
                "barberturno", Duration.ofSeconds(3), "https://api.twilio.com");
        FirmaTwilio firma = new FirmaTwilio(propiedades);
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        menus = new CorrespondenciaMenusTwilio(reloj);
        JsonMapper mapper = JsonMapper.builder().build();
        TraductorEntradaTwilio traductor = new TraductorEntradaTwilio(mapper, reloj, menus, propiedades);
        controlador = new WebhookTwilioController(firma, traductor, recepcion);
    }

    private static String calcularFirma(String token, String url, Map<String, String> parametros) {
        try {
            StringBuilder mensaje = new StringBuilder(url);
            new TreeMap<>(parametros).forEach((k, v) -> mensaje.append(k).append(v == null ? "" : v));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(mensaje.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al calcular HMAC-SHA1 en la prueba.", e);
        }
    }

    private static MockHttpServletRequest crearPeticion(String ruta, Map<String, String> parametros,
            String firma, String queryString, String cuerpoCrudoAlternativo) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", ruta);
        req.setServletPath(ruta);
        req.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        if (queryString != null) {
            req.setQueryString(queryString);
        }
        if (firma != null) {
            req.addHeader("X-Twilio-Signature", firma);
        }

        byte[] contenido;
        if (cuerpoCrudoAlternativo != null) {
            contenido = cuerpoCrudoAlternativo.getBytes(StandardCharsets.UTF_8);
        } else {
            StringBuilder sb = new StringBuilder();
            parametros.forEach((k, v) -> {
                if (!sb.isEmpty()) sb.append("&");
                sb.append(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8)).append("=")
                        .append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
            });
            contenido = sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        req.setContent(contenido);
        return req;
    }

    private Map<String, String> formularioMensajeValido() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("AccountSid", ACCOUNT_SID);
        form.put("MessageSid", "SM0123456789abcdef0123456789abcdef");
        form.put("From", "whatsapp:+51999000001");
        form.put("To", "whatsapp:" + NUMERO_SANDBOX);
        form.put("WaId", "51999000001");
        form.put("ProfileName", "Cliente Ficticio");
        form.put("Body", "hola");
        return form;
    }

    private Map<String, String> formularioEstadoValido() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("AccountSid", ACCOUNT_SID);
        form.put("MessageSid", "SM0123456789abcdef0123456789abcdef");
        form.put("From", "whatsapp:" + NUMERO_SANDBOX);
        form.put("To", "whatsapp:+51999000001");
        form.put("MessageStatus", "delivered");
        return form;
    }

    @Test
    void recibir_firmaValida_devuelve200XmlYDelegaARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firma, null, null);

        var respuesta = controlador.recibir(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_XML);
        assertThat(respuesta.getBody()).isEqualTo("<Response/>");
        verify(recepcion).recibir(any(byte[].class));
    }

    @Test
    void recibir_firmaValida_conUnicodeYMasLiteral_conservaTextoExactoYDelegaARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        String textoComplejo = "Corte + barba — Muñoz";
        form.put("Body", textoComplejo);
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firma, null, null);

        var respuesta = controlador.recibir(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_XML);
        assertThat(respuesta.getBody()).isEqualTo("<Response/>");

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(recepcion).recibir(captor.capture());

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode raiz = mapper.readTree(captor.getValue());
        String bodyDelegado = raiz.path("entry").path(0).path("changes").path(0).path("value")
                .path("messages").path(0).path("text").path("body").asText();
        assertThat(bodyDelegado).isEqualTo(textoComplejo);
    }

    @Test
    void recibir_sinCabeceraFirma_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        var req = crearPeticion(RUTA_WEBHOOK, form, null, null, null);

        var respuesta = controlador.recibir(req, null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_firmaDeOtraUrl_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        String firmaOtraUrl = calcularFirma(AUTH_TOKEN, "https://otra-empresa.test/api/twilio/webhook", form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firmaOtraUrl, null, null);

        var respuesta = controlador.recibir(req, firmaOtraUrl);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_firmaConOtroToken_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        String firmaOtroToken = calcularFirma("otro-token-falso-9999", URL_PUBLICA + RUTA_WEBHOOK, form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firmaOtroToken, null, null);

        var respuesta = controlador.recibir(req, firmaOtroToken);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_parametroAlteradoTrasFirmar_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> formOriginal = formularioMensajeValido();
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, formOriginal);

        Map<String, String> formAlterado = new LinkedHashMap<>(formOriginal);
        formAlterado.put("Body", "mensaje-alterado");
        var req = crearPeticion(RUTA_WEBHOOK, formAlterado, firma, null, null);

        var respuesta = controlador.recibir(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_conFirmaDeRutaEstado_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        // Firma válida pero calculada para la ruta de estado
        String firmaEstado = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_ESTADO, form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firmaEstado, null, null);

        var respuesta = controlador.recibir(req, firmaEstado);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_parametroRepetidoEnFormulario_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, form);

        // Serializa el formulario completo firmado con todos sus parámetros legítimos
        StringBuilder sb = new StringBuilder();
        form.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8)).append("=")
                    .append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        // Añade únicamente otro Body=hola: si el servidor admitiera duplicados colapsando al valor,
        // la firma coincidiría y la prueba fallaría.
        sb.append("&Body=").append(java.net.URLEncoder.encode("hola", StandardCharsets.UTF_8));
        String cuerpoConDuplicado = sb.toString();

        var req = crearPeticion(RUTA_WEBHOOK, form, firma, null, cuerpoConDuplicado);

        var respuesta = controlador.recibir(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_conQueryString_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firma, "parametro=no-permitido", null);

        var respuesta = controlador.recibir(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_cuerpoMayorDeUnMiB_devuelve413SinLlamarARecepcion() throws Exception {
        int limite = 1024 * 1024;
        byte[] grande = new byte[limite + 1];
        java.util.Arrays.fill(grande, (byte) 'a');
        MockHttpServletRequest req = new MockHttpServletRequest("POST", RUTA_WEBHOOK);
        req.setServletPath(RUTA_WEBHOOK);
        req.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        req.setContent(grande);

        var respuesta = controlador.recibir(req, "firma-cualquiera-ficticia28c");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_toDeOtroNumero_devuelve200XmlSinEfectos() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        form.put("To", "whatsapp:+18005559999");
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firma, null, null);

        var respuesta = controlador.recibir(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo("<Response/>");
        verifyNoInteractions(recepcion);
    }

    @Test
    void recibir_fromSinPrefijoWhatsapp_devuelve200XmlSinEfectos() throws Exception {
        Map<String, String> form = formularioMensajeValido();
        form.put("From", "+51999000001");
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, form);
        var req = crearPeticion(RUTA_WEBHOOK, form, firma, null, null);

        var respuesta = controlador.recibir(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo("<Response/>");
        verifyNoInteractions(recepcion);
    }

    @Test
    void estado_firmaValida_devuelve200XmlYDelegaARecepcion() throws Exception {
        Map<String, String> form = formularioEstadoValido();
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_ESTADO, form);
        var req = crearPeticion(RUTA_ESTADO, form, firma, null, null);

        var respuesta = controlador.estado(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo("<Response/>");
        verify(recepcion).recibir(any(byte[].class));
    }

    @Test
    void estado_sinFirma_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioEstadoValido();
        var req = crearPeticion(RUTA_ESTADO, form, null, null, null);

        var respuesta = controlador.estado(req, null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void estado_conFirmaDeRutaWebhook_devuelve401SinLlamarARecepcion() throws Exception {
        Map<String, String> form = formularioEstadoValido();
        // Firma válida pero calculada para la ruta de webhook
        String firmaWebhook = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_WEBHOOK, form);
        var req = crearPeticion(RUTA_ESTADO, form, firmaWebhook, null, null);

        var respuesta = controlador.estado(req, firmaWebhook);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(recepcion);
    }

    @Test
    void estado_conEstadoQueued_devuelve200XmlSinEfectos() throws Exception {
        Map<String, String> form = formularioEstadoValido();
        form.put("MessageStatus", "queued");
        String firma = calcularFirma(AUTH_TOKEN, URL_PUBLICA + RUTA_ESTADO, form);
        var req = crearPeticion(RUTA_ESTADO, form, firma, null, null);

        var respuesta = controlador.estado(req, firma);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo("<Response/>");
        verifyNoInteractions(recepcion);
    }
}
