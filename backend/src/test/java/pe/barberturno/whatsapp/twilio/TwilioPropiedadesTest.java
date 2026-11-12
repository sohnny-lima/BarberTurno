package pe.barberturno.whatsapp.twilio;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URLEncoder;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.barberturno.whatsapp.RecepcionWebhookService;
import pe.barberturno.whatsapp.ResultadoEnvio;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de validación de {@link TwilioPropiedades} (CT-13, 20-v2-twilio-sandbox.md §10).
 * Valida los rangos estrictos de los plazos de TwiML (espera entre 1 y 12 s, silencio entre 100 ms y la espera),
 * que las configuraciones inválidas lancen {@link IllegalArgumentException} con un mensaje genérico
 * fijo que no filtre valores ni secretos, y que {@link TwilioPropiedades#toString()} proteja la configuración.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class TwilioPropiedadesTest {

    private static final String MENSAJE_ERROR_ESPERADO = "Configuración de Twilio Sandbox fuera de rango.";

    @Test
    void constructor_valoresValidos_construyeCorrectamente() {
        assertThatCode(() -> new TwilioPropiedades(
                "AC0123456789abcdef0123456789abcdef", "token-ficticio", "+14155238886",
                "https://pruebas.barberturno.test", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(10), Duration.ofMillis(1500), java.util.List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_limitesValidosDeEsperaYSilencio_exito() {
        // Límite inferior de espera: 1 s; límite inferior de silencio: 100 ms
        assertThatCode(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(1), Duration.ofMillis(100), java.util.List.of()))
                .doesNotThrowAnyException();

        // Límite superior de espera: 12 s; silencio igual a la espera
        assertThatCode(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(12), Duration.ofSeconds(12), java.util.List.of()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 500, 999, 12001, 15000, 60000})
    void constructor_respuestaEsperaFueraDeRango_lanzaIllegalArgumentExceptionSinValores(long millis) {
        Duration esperaInvalida = Duration.ofMillis(millis);

        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, esperaInvalida, Duration.ofMillis(100), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(String.valueOf(millis)));
    }

    @ParameterizedTest
    @NullSource
    void constructor_respuestaEsperaNula_lanzaIllegalArgumentException(Duration esperaNula) {
        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, esperaNula, Duration.ofMillis(100), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 50, 99})
    void constructor_respuestaSilencioMenorA100Ms_lanzaIllegalArgumentExceptionSinValores(long millis) {
        Duration silencioInvalido = Duration.ofMillis(millis);

        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(5), silencioInvalido, java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(String.valueOf(millis)));
    }

    @Test
    void constructor_respuestaSilencioMayorAEspera_lanzaIllegalArgumentExceptionSinValores() {
        Duration espera = Duration.ofSeconds(5);
        Duration silencioMayor = Duration.ofSeconds(6);

        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, espera, silencioMayor, java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("6").doesNotContain("5"));
    }

    @ParameterizedTest
    @NullSource
    void constructor_respuestaSilencioNulo_lanzaIllegalArgumentException(Duration silencioNulo) {
        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(5), silencioNulo, java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO);
    }

    @Test
    void toString_noFiltraSecretosNiValores() {
        TwilioPropiedades propiedades = new TwilioPropiedades(
                "AC0123456789abcdef0123456789abcdef", "secreto-super-confidencial",
                "+14155238886", "https://pruebas.barberturno.test", "barberturno",
                Duration.ofSeconds(3), "https://api.twilio.com", true,
                Duration.ofSeconds(10), Duration.ofMillis(1500), java.util.List.of());

        assertThat(propiedades.toString()).isEqualTo("TwilioPropiedades[configuración oculta]");
    }

    @Test
    void registros_entradaAceptacionTwimlRestYRechazo_noRegistranBsuidSinteticoNiTexto() throws Exception {
        Logger loggerPaquete = (Logger) LoggerFactory.getLogger("pe.barberturno.whatsapp.twilio");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        loggerPaquete.addAppender(appender);

        String marcadorBsuid = "PE.MARCADORBSUIDLOGCT13";
        String bsuidDireccion = "whatsapp:" + marcadorBsuid;
        String marcadorTexto = "TEXTO_CONFIDENCIAL_MARCADOR_CT13";

        CorrespondenciaDireccionesTwilio direcciones = new CorrespondenciaDireccionesTwilio();
        String marcadorSintetico = direcciones.identificar(null, bsuidDireccion).orElseThrow();

        try {
            JsonMapper mapper = JsonMapper.builder().build();
            Clock reloj = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
            CorrespondenciaMenusTwilio menus = new CorrespondenciaMenusTwilio(reloj);

            // 1. Entrada por Webhook con BSUID y texto confidencial
            TwilioPropiedades propControlador = new TwilioPropiedades(
                    "AC0123456789abcdef0123456789abcdef", "secreto-token-webhook",
                    "+14155238886", "https://pruebas.barberturno.test", "barberturno",
                    Duration.ofSeconds(3), "https://api.twilio.com", true,
                    Duration.ofSeconds(10), Duration.ofMillis(1500), java.util.List.of());
            FirmaTwilio firma = new FirmaTwilio(propControlador);
            BuzonRespuestasTwilio buzonControlador = new BuzonRespuestasTwilio(propControlador, java.time.Clock.systemUTC());
            TraductorEntradaTwilio traductor = new TraductorEntradaTwilio(mapper, reloj, menus, propControlador, direcciones);
            RecepcionWebhookService recepcion = Mockito.mock(RecepcionWebhookService.class);
            Mockito.when(recepcion.recibir(Mockito.any(byte[].class))).thenReturn(1);
            WebhookTwilioController controlador = new WebhookTwilioController(firma, traductor, recepcion, propControlador, buzonControlador);

            Map<String, String> formEntrada = new LinkedHashMap<>();
            formEntrada.put("AccountSid", "AC0123456789abcdef0123456789abcdef");
            formEntrada.put("MessageSid", "SM_ENTRADA_LOG_1");
            formEntrada.put("From", bsuidDireccion);
            formEntrada.put("To", "whatsapp:+14155238886");
            formEntrada.put("ProfileName", "Cliente Confidencial");
            formEntrada.put("Body", marcadorTexto);

            String firmaEntrada = calcularFirma("secreto-token-webhook", "https://pruebas.barberturno.test/api/twilio/webhook", formEntrada);
            MockHttpServletRequest req = crearPeticion("/api/twilio/webhook", formEntrada, firmaEntrada);
            controlador.recibir(req, firmaEntrada);

            // 2. Aceptación por TwiML
            ClienteTwilioWhatsApp clienteTwiml = new ClienteTwilioWhatsApp(propControlador, menus, mapper, direcciones, buzonControlador);
            var espera = buzonControlador.abrir("14155238886", marcadorSintetico);
            assertThat(espera).isPresent();

            ObjectNode cuerpoTwiml = mapper.createObjectNode();
            cuerpoTwiml.put("messaging_product", "whatsapp");
            cuerpoTwiml.put("to", marcadorSintetico);
            cuerpoTwiml.put("type", "text");
            cuerpoTwiml.putObject("text").put("body", marcadorTexto);

            ResultadoEnvio resTwiml = clienteTwiml.enviar("14155238886", cuerpoTwiml);
            assertThat(resTwiml).isInstanceOf(ResultadoEnvio.Aceptado.class);
            buzonControlador.cerrar(espera.get());

            // 3. Envío exitoso por REST y 4. Rechazo/Error local y remoto
            try (ServidorTwilioFalso servidor = new ServidorTwilioFalso()) {
                servidor.iniciar();
                TwilioPropiedades propRest = new TwilioPropiedades(
                        "AC0123456789abcdef0123456789abcdef", "secreto-token-rest",
                        "+14155238886", "https://pruebas.barberturno.test", "barberturno",
                        Duration.ofSeconds(3), servidor.urlBase(), true,
                        Duration.ofSeconds(10), Duration.ofMillis(1500), java.util.List.of());
                BuzonRespuestasTwilio buzonRest = new BuzonRespuestasTwilio(propRest, java.time.Clock.systemUTC());
                ClienteTwilioWhatsApp clienteRest = new ClienteTwilioWhatsApp(propRest, menus, mapper, direcciones, buzonRest);

                // 3a. Envío por REST (201) a un número E.164
                String marcadorE164 = "51999887766";
                direcciones.identificar(marcadorE164, "whatsapp:+" + marcadorE164);
                servidor.encolarExito("SM_OUT_REST_LOG_1");
                ObjectNode cuerpoRest = mapper.createObjectNode();
                cuerpoRest.put("messaging_product", "whatsapp");
                cuerpoRest.put("to", marcadorE164);
                cuerpoRest.put("type", "text");
                cuerpoRest.putObject("text").put("body", marcadorTexto);

                ResultadoEnvio resRest = clienteRest.enviar("14155238886", cuerpoRest);
                assertThat(resRest).isInstanceOf(ResultadoEnvio.Aceptado.class);

                // 3b. Envío diferido a sintético sin espera (se retiene en buzón como twiml-pendiente:)
                ObjectNode cuerpoSintetico = mapper.createObjectNode();
                cuerpoSintetico.put("messaging_product", "whatsapp");
                cuerpoSintetico.put("to", marcadorSintetico);
                cuerpoSintetico.put("type", "text");
                cuerpoSintetico.putObject("text").put("body", marcadorTexto);

                ResultadoEnvio resSintetico = clienteRest.enviar("14155238886", cuerpoSintetico);
                assertThat(resSintetico).isInstanceOf(ResultadoEnvio.Aceptado.class);
                assertThat(((ResultadoEnvio.Aceptado) resSintetico).wamid()).startsWith("twiml-pendiente:");

                // 4a. Rechazo local por plantilla
                ObjectNode cuerpoPlantilla = mapper.createObjectNode();
                cuerpoPlantilla.put("messaging_product", "whatsapp");
                cuerpoPlantilla.put("to", marcadorSintetico);
                cuerpoPlantilla.put("type", "template");
                cuerpoPlantilla.putObject("template").put("name", marcadorTexto);
                ResultadoEnvio resPlantilla = clienteRest.enviar("14155238886", cuerpoPlantilla);
                assertThat(resPlantilla).isInstanceOf(ResultadoEnvio.Rechazado.class);

                // 4b. Rechazo remoto (HTTP 400) vía REST a E.164
                servidor.encolarError(400, 21614, "Destinatario no habilitado");
                ObjectNode cuerpoError = mapper.createObjectNode();
                cuerpoError.put("messaging_product", "whatsapp");
                cuerpoError.put("to", marcadorE164);
                cuerpoError.put("type", "text");
                cuerpoError.putObject("text").put("body", marcadorTexto);
                ResultadoEnvio resError = clienteRest.enviar("14155238886", cuerpoError);
                assertThat(resError).isInstanceOf(ResultadoEnvio.Rechazado.class);
            }
        } finally {
            loggerPaquete.detachAppender(appender);
        }

        // Exigir que se hayan emitido y capturado logs durante el recorrido
        assertThat(appender.list)
                .as("el adaptador debe haber emitido eventos de log en las operaciones")
                .isNotEmpty();

        // CT-13: los registros no deben contener BSUID, wa_id sintético ni texto
        for (ILoggingEvent evento : appender.list) {
            String mensaje = evento.getFormattedMessage();
            assertThat(mensaje)
                    .as("el registro de log no debe contener el BSUID")
                    .doesNotContain(marcadorBsuid)
                    .doesNotContain("PE.MARCADOR");
            assertThat(mensaje)
                    .as("el registro de log no debe contener el wa_id sintético")
                    .doesNotContain(marcadorSintetico);
            assertThat(mensaje)
                    .as("el registro de log no debe contener el cuerpo o texto del mensaje")
                    .doesNotContain(marcadorTexto)
                    .doesNotContain("TEXTO_CONFIDENCIAL");
        }
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

    private static MockHttpServletRequest crearPeticion(String ruta, Map<String, String> parametros, String firma) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", ruta);
        req.setServletPath(ruta);
        req.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        if (firma != null) {
            req.addHeader("X-Twilio-Signature", firma);
        }
        StringBuilder cuerpo = new StringBuilder();
        parametros.forEach((k, v) -> {
            if (!cuerpo.isEmpty()) cuerpo.append('&');
            cuerpo.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
        });
        req.setContent(cuerpo.toString().getBytes(StandardCharsets.UTF_8));
        return req;
    }
}
