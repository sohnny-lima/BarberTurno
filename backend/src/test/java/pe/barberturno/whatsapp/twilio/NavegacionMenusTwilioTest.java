package pe.barberturno.whatsapp.twilio;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.barberturno.whatsapp.RecepcionWebhookService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de navegación y validación de rango numérico en el adaptador de Twilio (CT-14, V2-DA-105).
 * Valida que una opción fuera de rango responda de inmediato por TwiML con el aviso exacto y el mismo menú,
 * sin generar eventos en la recepción ni contar como fallo de derivación; que una opción válida posterior
 * funcione consumiendo el menú vigente; que «0» y «menu» pasen a la conversación para volver al inicio;
 * y que un MessageSid duplicado devuelva la misma respuesta de forma idempotente.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class NavegacionMenusTwilioTest {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "secreto-token-ct14-test";
    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String PHONE_NUMBER_ID = "14155238886";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String WA_ID = "51999000111";
    private static final Instant AHORA = Instant.parse("2026-11-12T10:00:00Z");

    private static final String TEXTO_MENU_SERVICIOS = """
            Nuestros servicios:

            1. Corte clásico — 30 min · S/ 25,00
            2. Afeitado tradicional — 20 min · S/ 15,00
            Responde con el número o escribe «menu» para volver al inicio.""";

    private RecepcionWebhookService recepcion;
    private CorrespondenciaMenusTwilio menus;
    private CorrespondenciaDireccionesTwilio direcciones;
    private BuzonRespuestasTwilio buzon;
    private TraductorEntradaTwilio traductor;
    private FirmaTwilio firma;
    private WebhookTwilioController controlador;
    private JsonMapper mapper;
    private TwilioPropiedades propiedades;

    @BeforeEach
    void preparar() {
        mapper = JsonMapper.builder().build();
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        menus = new CorrespondenciaMenusTwilio(reloj);
        direcciones = new CorrespondenciaDireccionesTwilio();

        propiedades = new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_SANDBOX, URL_PUBLICA,
                "barberturno", Duration.ofSeconds(3), "https://api.twilio.com", true,
                Duration.ofSeconds(10), Duration.ofMillis(1500), List.of());

        firma = new FirmaTwilio(propiedades);
        buzon = new BuzonRespuestasTwilio(propiedades, reloj);
        traductor = new TraductorEntradaTwilio(mapper, reloj, menus, propiedades, direcciones);
        recepcion = mock(RecepcionWebhookService.class);
        controlador = new WebhookTwilioController(firma, traductor, recepcion, propiedades, buzon);

        // Guardar menú inicial de servicios con 2 opciones
        menus.guardar(PHONE_NUMBER_ID, WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:1", "Corte clásico"),
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:2", "Afeitado tradicional")
        ), TEXTO_MENU_SERVICIOS);
    }

    private MockHttpServletRequest crearPeticionFirmada(String sid, String body) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", ACCOUNT_SID);
        datos.put("MessageSid", sid);
        datos.put("From", "whatsapp:+" + WA_ID);
        datos.put("To", "whatsapp:" + NUMERO_SANDBOX);
        datos.put("WaId", WA_ID);
        datos.put("ProfileName", "Cliente CT14");
        datos.put("Body", body);

        String firmaCalculada = calcularFirma(AUTH_TOKEN, URL_PUBLICA + "/api/twilio/webhook", datos);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/twilio/webhook");
        req.setServletPath("/api/twilio/webhook");
        req.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        req.addHeader("X-Twilio-Signature", firmaCalculada);

        StringBuilder cuerpoForm = new StringBuilder();
        datos.forEach((k, v) -> {
            if (!cuerpoForm.isEmpty()) cuerpoForm.append('&');
            cuerpoForm.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
        });
        req.setContent(cuerpoForm.toString().getBytes(StandardCharsets.UTF_8));
        return req;
    }

    private static String calcularFirma(String token, String url, Map<String, String> parametros) {
        try {
            StringBuilder mensaje = new StringBuilder(url);
            new TreeMap<>(parametros).forEach((k, v) -> mensaje.append(k).append(v == null ? "" : v));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(mensaje.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al firmar en prueba.", e);
        }
    }

    @Test
    void opcionFueraDeRango_devuelveTwimlInmediatoConAvisoYTextoExactoDelMenu_sinEventoEnRecepcion() throws Exception {
        MockHttpServletRequest peticion = crearPeticionFirmada("SM_RANGO_001", "3");

        ResponseEntity<String> respuesta = controlador.recibir(peticion, peticion.getHeader("X-Twilio-Signature"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_XML);

        String twiml = respuesta.getBody();
        assertThat(twiml).isNotNull().startsWith("<Response><Message>").endsWith("</Message></Response>");
        assertThat(twiml).contains("Esa opción no está en el menú. Responde con un número del 1 al 2 o escribe «menu» para volver al inicio.");
        assertThat(twiml).contains("Nuestros servicios:");
        assertThat(twiml).contains("1. Corte clásico — 30 min · S/ 25,00");
        assertThat(twiml).contains("2. Afeitado tradicional — 20 min · S/ 15,00");
        assertThat(twiml).contains("Responde con el número o escribe «menu» para volver al inicio.");

        // CT-14: No genera evento en la recepción y no cuenta como fallo
        verify(recepcion, never()).recibir(any(byte[].class));
    }

    @Test
    void opcionFueraDeRango_despuesOpcionValidaDelMismoMenuFunciona() throws Exception {
        // 1. Enviar opción 99 fuera de rango -> aviso inmediato, menú sigue vigente
        MockHttpServletRequest peticionInvalida = crearPeticionFirmada("SM_RANGO_002", "99");
        ResponseEntity<String> respInvalida = controlador.recibir(peticionInvalida, peticionInvalida.getHeader("X-Twilio-Signature"));
        assertThat(respInvalida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respInvalida.getBody()).contains("Esa opción no está en el menú");
        verify(recepcion, never()).recibir(any(byte[].class));

        // 2. Enviar opción 2 válida del mismo menú -> se traduce como interactivo y se entrega a recepción
        when(recepcion.recibir(any(byte[].class))).thenReturn(1);
        MockHttpServletRequest peticionValida = crearPeticionFirmada("SM_RANGO_003", "2");
        controlador.recibir(peticionValida, peticionValida.getHeader("X-Twilio-Signature"));

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(recepcion).recibir(captor.capture());

        JsonNode raiz = mapper.readTree(captor.getValue());
        JsonNode mensaje = raiz.path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(mensaje.path("type").asText()).isEqualTo("interactive");
        assertThat(mensaje.path("interactive").path("list_reply").path("id").asText()).isEqualTo("s:2");
        assertThat(mensaje.path("interactive").path("list_reply").path("title").asText()).isEqualTo("Afeitado tradicional");
    }

    @Test
    void ceroYMenu_noSonOpcionFueraDeRango_seTraducenComoTextoNormalParaBienvenida() throws Exception {
        when(recepcion.recibir(any(byte[].class))).thenReturn(1);

        // Entrada «0»
        MockHttpServletRequest peticionCero = crearPeticionFirmada("SM_NAV_001", "0");
        controlador.recibir(peticionCero, peticionCero.getHeader("X-Twilio-Signature"));

        ArgumentCaptor<byte[]> captorCero = ArgumentCaptor.forClass(byte[].class);
        verify(recepcion).recibir(captorCero.capture());
        JsonNode msgCero = mapper.readTree(captorCero.getValue())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msgCero.path("type").asText()).isEqualTo("text");
        assertThat(msgCero.path("text").path("body").asText()).isEqualTo("0");

        // Entrada «menu»
        MockHttpServletRequest peticionMenu = crearPeticionFirmada("SM_NAV_002", "menu");
        controlador.recibir(peticionMenu, peticionMenu.getHeader("X-Twilio-Signature"));

        ArgumentCaptor<byte[]> captorMenu = ArgumentCaptor.forClass(byte[].class);
        verify(recepcion, org.mockito.Mockito.times(2)).recibir(captorMenu.capture());
        JsonNode msgMenu = mapper.readTree(captorMenu.getValue())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msgMenu.path("type").asText()).isEqualTo("text");
        assertThat(msgMenu.path("text").path("body").asText()).isEqualTo("menu");
    }

    @Test
    void opcionFueraDeRango_messageSidDuplicado_devuelveMismaRespuestaIdempotente() throws Exception {
        String sid = "SM_RANGO_DUP_999";
        MockHttpServletRequest peticion1 = crearPeticionFirmada(sid, "5");
        ResponseEntity<String> resp1 = controlador.recibir(peticion1, peticion1.getHeader("X-Twilio-Signature"));

        // Entre los dos intentos con el mismo SID, sustituye el menú guardado
        String textoMenuNuevo = """
                Menú renovado:
                1. Opción Nueva A
                2. Opción Nueva B
                Responde con el número o escribe «menu» para volver al inicio.""";
        menus.guardar(PHONE_NUMBER_ID, WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:nueva_1", "Opción Nueva A"),
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:nueva_2", "Opción Nueva B")
        ), textoMenuNuevo);

        MockHttpServletRequest peticion2 = crearPeticionFirmada(sid, "5");
        ResponseEntity<String> resp2 = controlador.recibir(peticion2, peticion2.getHeader("X-Twilio-Signature"));

        // El duplicado conserva el aviso original, sin evento y sin consumir el nuevo menú
        assertThat(resp1.getBody()).isEqualTo(resp2.getBody());
        assertThat(resp2.getBody()).contains("Esa opción no está en el menú");
        assertThat(resp2.getBody()).contains(TEXTO_MENU_SERVICIOS);
        assertThat(resp2.getBody()).doesNotContain("Menú renovado");
        verify(recepcion, never()).recibir(any(byte[].class));

        var consumoNuevoMenu = menus.consumir(PHONE_NUMBER_ID, WA_ID, "1", "SM_VERIF_NUEVO_MENU");
        assertThat(consumoNuevoMenu).isPresent();
        assertThat(consumoNuevoMenu.get().id()).isEqualTo("s:nueva_1");
    }
}
