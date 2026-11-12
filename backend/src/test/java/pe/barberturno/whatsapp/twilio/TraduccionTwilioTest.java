package pe.barberturno.whatsapp.twilio;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.barberturno.support.RelojAjustable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de traducción de entrada y correspondencia de menús de Twilio a Meta (CT-3 y CT-4).
 * Valida la conversión al JSON que espera {@code ExtractorEventosWhatsApp}:
 * texto libre, botones nativos ({@code ButtonPayload}), selecciones numéricas con menú vigente
 * (tanto botones como listas), números sin menú o fuera de rango, idempotencia de traducción por
 * {@code MessageSid}, caducidad temporal a las 24 h con {@link RelojAjustable}, contenidos multimedia sin texto,
 * y mapeo de avisos de estado ({@code sent}, {@code delivered}, {@code read}, {@code failed}, {@code undelivered} y omisión de {@code queued}).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class TraduccionTwilioTest {

    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String WA_ID = "51999000001";
    private static final Instant T0 = Instant.parse("2026-11-11T10:00:00Z");

    private JsonMapper mapper;
    private RelojAjustable reloj;
    private CorrespondenciaMenusTwilio menus;
    private TraductorEntradaTwilio traductor;

    @BeforeEach
    void preparar() {
        mapper = JsonMapper.builder().build();
        reloj = new RelojAjustable();
        reloj.fijar(T0);
        menus = new CorrespondenciaMenusTwilio(reloj);
        TwilioPropiedades propiedades = new TwilioPropiedades("AC0123456789abcdef0123456789abcdef",
                "token-ficticio", NUMERO_SANDBOX, "https://pruebas.barberturno.test",
                "barberturno", Duration.ofSeconds(3), "https://api.twilio.com");
        traductor = new TraductorEntradaTwilio(mapper, reloj, menus, propiedades);
    }

    private Map<String, String> baseMensaje(String sid, String body) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", "AC0123456789abcdef0123456789abcdef");
        datos.put("MessageSid", sid);
        datos.put("From", "whatsapp:+" + WA_ID);
        datos.put("To", "whatsapp:" + NUMERO_SANDBOX);
        datos.put("WaId", WA_ID);
        datos.put("ProfileName", "Cliente Demo");
        datos.put("Body", body);
        return datos;
    }

    @Test
    void mensaje_textoNormal_traduceAFormatoMetaTexto() throws Exception {
        Map<String, String> datos = baseMensaje("SM_TEXTO_1", "hola, buenas tardes");

        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode raiz = mapper.readTree(resultado.get());
        assertThat(raiz.path("object").asText()).isEqualTo("whatsapp_business_account");

        JsonNode valor = raiz.path("entry").path(0).path("changes").path(0).path("value");
        assertThat(valor.path("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(valor.path("metadata").path("phone_number_id").asText()).isEqualTo("14155238886");

        JsonNode contacto = valor.path("contacts").path(0);
        assertThat(contacto.path("wa_id").asText()).isEqualTo(WA_ID);
        assertThat(contacto.path("profile").path("name").asText()).isEqualTo("Cliente Demo");

        JsonNode mensaje = valor.path("messages").path(0);
        assertThat(mensaje.path("id").asText()).isEqualTo("SM_TEXTO_1");
        assertThat(mensaje.path("from").asText()).isEqualTo(WA_ID);
        assertThat(mensaje.path("timestamp").asText()).isEqualTo(String.valueOf(T0.getEpochSecond()));
        assertThat(mensaje.path("type").asText()).isEqualTo("text");
        assertThat(mensaje.path("text").path("body").asText()).isEqualTo("hola, buenas tardes");
    }

    @Test
    void mensaje_conButtonPayload_traduceAInteractivoButtonReply() throws Exception {
        Map<String, String> datos = baseMensaje("SM_BTN_1", "Reservar");
        datos.put("ButtonPayload", "m:RESERVAR");
        datos.put("ButtonText", "Reservar");

        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode raiz = mapper.readTree(resultado.get());
        JsonNode mensaje = raiz.path("entry").path(0).path("changes").path(0).path("value")
                .path("messages").path(0);

        assertThat(mensaje.path("type").asText()).isEqualTo("interactive");
        JsonNode interactivo = mensaje.path("interactive");
        assertThat(interactivo.path("type").asText()).isEqualTo("button_reply");
        assertThat(interactivo.path("button_reply").path("id").asText()).isEqualTo("m:RESERVAR");
        assertThat(interactivo.path("button_reply").path("title").asText()).isEqualTo("Reservar");
    }

    @Test
    void mensaje_numeroConMenuVigenteLista_traduceAInteractivoListReplyYConsumeMenu() throws Exception {
        menus.guardar(WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:1", "Corte clásico"),
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:2", "Barba y bigote")
        ));

        Map<String, String> datos = baseMensaje("SM_NUM_LISTA", " 2 ");
        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode raiz = mapper.readTree(resultado.get());
        JsonNode mensaje = raiz.path("entry").path(0).path("changes").path(0).path("value")
                .path("messages").path(0);

        assertThat(mensaje.path("type").asText()).isEqualTo("interactive");
        JsonNode interactivo = mensaje.path("interactive");
        assertThat(interactivo.path("type").asText()).isEqualTo("list_reply");
        assertThat(interactivo.path("list_reply").path("id").asText()).isEqualTo("s:2");
        assertThat(interactivo.path("list_reply").path("title").asText()).isEqualTo("Barba y bigote");

        // El menú fue consumido: una segunda respuesta con "1" llega como texto plano
        Map<String, String> posterior = baseMensaje("SM_NUM_LISTA_2", "1");
        Optional<byte[]> resPosterior = traductor.mensaje(posterior);
        assertThat(resPosterior).isPresent();
        JsonNode mensajePosterior = mapper.readTree(resPosterior.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(mensajePosterior.path("type").asText()).isEqualTo("text");
        assertThat(mensajePosterior.path("text").path("body").asText()).isEqualTo("1");
    }

    @Test
    void mensaje_numeroConMenuVigenteBotones_traduceAInteractivoButtonReplyYConsumeMenu() throws Exception {
        menus.guardar(WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:RESERVAR", "Reservar"),
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:MIS_CITAS", "Mis citas")
        ));

        Map<String, String> datos = baseMensaje("SM_NUM_BTN", "1");
        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode raiz = mapper.readTree(resultado.get());
        JsonNode mensaje = raiz.path("entry").path(0).path("changes").path(0).path("value")
                .path("messages").path(0);

        assertThat(mensaje.path("type").asText()).isEqualTo("interactive");
        JsonNode interactivo = mensaje.path("interactive");
        assertThat(interactivo.path("type").asText()).isEqualTo("button_reply");
        assertThat(interactivo.path("button_reply").path("id").asText()).isEqualTo("m:RESERVAR");
        assertThat(interactivo.path("button_reply").path("title").asText()).isEqualTo("Reservar");
    }

    @Test
    void mensaje_numeroSinMenuVigente_llegaComoTextoPlano() throws Exception {
        Map<String, String> datos = baseMensaje("SM_SIN_MENU", "3");
        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode mensaje = mapper.readTree(resultado.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);

        assertThat(mensaje.path("type").asText()).isEqualTo("text");
        assertThat(mensaje.path("text").path("body").asText()).isEqualTo("3");
    }

    @Test
    void mensaje_numeroFueraDeRango_llegaComoTextoYMenuSigueVigente() throws Exception {
        menus.guardar(WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:RESERVAR", "Reservar"),
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:MIS_CITAS", "Mis citas")
        ));

        // Opción 5 está fuera de rango para un menú de 2 opciones
        Map<String, String> fueraDeRango = baseMensaje("SM_FUERA_1", "5");
        Optional<byte[]> resFuera = traductor.mensaje(fueraDeRango);
        assertThat(resFuera).isPresent();
        JsonNode msgFuera = mapper.readTree(resFuera.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msgFuera.path("type").asText()).isEqualTo("text");
        assertThat(msgFuera.path("text").path("body").asText()).isEqualTo("5");

        // El menú sigue vigente: ahora opción 2 se traduce a Mis citas
        Map<String, String> valida = baseMensaje("SM_VAL_2", "2");
        Optional<byte[]> resValida = traductor.mensaje(valida);
        assertThat(resValida).isPresent();
        JsonNode msgValida = mapper.readTree(resValida.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msgValida.path("type").asText()).isEqualTo("interactive");
        assertThat(msgValida.path("interactive").path("button_reply").path("id").asText()).isEqualTo("m:MIS_CITAS");
    }

    @Test
    void mensaje_mismoMessageSidRepetido_devuelveMismaTraduccionSinConsumirOtroMenu() throws Exception {
        // 1. Instalar menú inicial
        menus.guardar(WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:RESERVAR", "Reservar"),
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:MIS_CITAS", "Mis citas")
        ));

        // 2. Primera entrada con SID "SM_REINTENTO_1" elige la opción 1 -> m:RESERVAR
        Map<String, String> primera = baseMensaje("SM_REINTENTO_1", "1");
        Optional<byte[]> res1 = traductor.mensaje(primera);
        assertThat(res1).isPresent();
        JsonNode msg1 = mapper.readTree(res1.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msg1.path("type").asText()).isEqualTo("interactive");
        assertThat(msg1.path("interactive").path("button_reply").path("id").asText()).isEqualTo("m:RESERVAR");

        // 3. Instalar un menú posterior distinto antes del reintento
        menus.guardar(WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:99", "Servicio Posterior Especial")
        ));

        // 4. Reintento de Twilio con el mismo MessageSid: devuelve la selección original sin consumir el menú nuevo
        Map<String, String> reintento = baseMensaje("SM_REINTENTO_1", "1");
        Optional<byte[]> res2 = traductor.mensaje(reintento);
        assertThat(res2).isPresent();
        JsonNode msg2 = mapper.readTree(res2.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msg2.path("type").asText()).isEqualTo("interactive");
        assertThat(msg2.path("interactive").path("button_reply").path("id").asText()).isEqualTo("m:RESERVAR");

        // 5. Un SID nuevo enviando "1" todavía puede consumir el menú posterior intacto
        Map<String, String> nuevoSid = baseMensaje("SM_NUEVO_2", "1");
        Optional<byte[]> res3 = traductor.mensaje(nuevoSid);
        assertThat(res3).isPresent();
        JsonNode msg3 = mapper.readTree(res3.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msg3.path("type").asText()).isEqualTo("interactive");
        assertThat(msg3.path("interactive").path("list_reply").path("id").asText()).isEqualTo("s:99");
        assertThat(msg3.path("interactive").path("list_reply").path("title").asText()).isEqualTo("Servicio Posterior Especial");
    }

    @Test
    void mensaje_caducidad24Horas_menuVencidoDevuelveTextoPlano() throws Exception {
        menus.guardar(WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:RESERVAR", "Reservar")
        ));

        // Adelantar reloj 24 horas y 1 segundo
        reloj.adelantar(Duration.ofHours(24).plusSeconds(1));

        Map<String, String> datos = baseMensaje("SM_CADUCADO", "1");
        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode msg = mapper.readTree(resultado.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msg.path("type").asText()).isEqualTo("text");
        assertThat(msg.path("text").path("body").asText()).isEqualTo("1");
    }

    @Test
    void mensaje_mediaSinTexto_traduceAUnsupported() throws Exception {
        Map<String, String> datos = baseMensaje("SM_MEDIA_1", "");
        datos.put("NumMedia", "1");

        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode msg = mapper.readTree(resultado.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msg.path("type").asText()).isEqualTo("unsupported");
    }

    @Test
    void mensaje_mediaConTexto_traduceATextoConContenido() throws Exception {
        Map<String, String> datos = baseMensaje("SM_MEDIA_2", "Foto del corte");
        datos.put("NumMedia", "1");

        Optional<byte[]> resultado = traductor.mensaje(datos);

        assertThat(resultado).isPresent();
        JsonNode msg = mapper.readTree(resultado.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("messages").path(0);
        assertThat(msg.path("type").asText()).isEqualTo("text");
        assertThat(msg.path("text").path("body").asText()).isEqualTo("Foto del corte");
    }

    @ParameterizedTest(name = "MessageStatus {0} → status Meta {1}")
    @CsvSource({
            "sent, sent",
            "delivered, delivered",
            "read, read",
            "failed, failed",
            "undelivered, failed"
    })
    void estado_estadosValidos_traduceAStatusMeta(String statusTwilio, String statusMetaEsperado) throws Exception {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("MessageSid", "SM_EST_1");
        datos.put("MessageStatus", statusTwilio);
        datos.put("To", "whatsapp:+" + WA_ID);
        if ("failed".equals(statusTwilio) || "undelivered".equals(statusTwilio)) {
            datos.put("ErrorCode", "63015");
        }

        Optional<byte[]> resultado = traductor.estado(datos);

        assertThat(resultado).isPresent();
        JsonNode raiz = mapper.readTree(resultado.get());
        JsonNode aviso = raiz.path("entry").path(0).path("changes").path(0).path("value")
                .path("statuses").path(0);

        assertThat(aviso.path("id").asText()).isEqualTo("SM_EST_1");
        assertThat(aviso.path("status").asText()).isEqualTo(statusMetaEsperado);
        assertThat(aviso.path("recipient_id").asText()).isEqualTo(WA_ID);
        assertThat(aviso.path("timestamp").asText()).isEqualTo(String.valueOf(T0.getEpochSecond()));

        if ("failed".equals(statusMetaEsperado)) {
            assertThat(aviso.path("errors").path(0).path("code").asInt()).isEqualTo(63015);
        }
    }

    @Test
    void estado_failedSinErrorCode_generaAvisoFailedSinArrayErrors() throws Exception {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("MessageSid", "SM_EST_SIN_COD");
        datos.put("MessageStatus", "failed");
        datos.put("To", "whatsapp:+" + WA_ID);

        Optional<byte[]> resultado = traductor.estado(datos);

        assertThat(resultado).isPresent();
        JsonNode aviso = mapper.readTree(resultado.get())
                .path("entry").path(0).path("changes").path(0).path("value").path("statuses").path(0);
        assertThat(aviso.path("status").asText()).isEqualTo("failed");
        assertThat(aviso.has("errors")).isFalse();
    }

    @Test
    void estado_queued_seIgnoraYDevuelveVacio() {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("MessageSid", "SM_EST_QUEUED");
        datos.put("MessageStatus", "queued");
        datos.put("To", "whatsapp:+" + WA_ID);

        Optional<byte[]> resultado = traductor.estado(datos);

        assertThat(resultado).isEmpty();
    }

    @Test
    void estado_destinatarioSinPrefijoWhatsapp_seIgnoraYDevuelveVacio() {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("MessageSid", "SM_DEST_INVALIDO");
        datos.put("MessageStatus", "delivered");
        datos.put("To", "+51999000001");

        Optional<byte[]> resultado = traductor.estado(datos);

        assertThat(resultado).isEmpty();
    }
}
