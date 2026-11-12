package pe.barberturno.whatsapp.twilio;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.barberturno.conversation.ConversacionPruebas;
import pe.barberturno.conversation.VinculoProfesionalWhatsAppService;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.CodigoVinculoWhatsApp;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.RecepcionWebhookService;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;
import pe.barberturno.whatsapp.dto.CodigoVinculoProfesionalDto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración de captura de registros y privacidad de datos sensibles (CT-20, V2-DA-105/106/107/108).
 * Valida mediante un ListAppender de Logback que ninguna traza de log bajo {@code pe.barberturno}
 * registre el código de vinculación en claro, su hash SHA-256, el identificador BSUID,
 * el wa_id sintético derivado ni los nombres de clientes en las operaciones de vinculación,
 * recepción de webhooks, consulta de agenda o despacho de respuestas.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class RegistrosSeguridadTwilioIT extends WhatsAppSalidaPruebaBase {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "secreto-ct20-registros";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String MARCADOR_BSUID = "PE.MARCADORBSUIDCT20";
    private static final String BSUID_DIRECCION = "whatsapp:" + MARCADOR_BSUID;
    private static final String MARCADOR_CLIENTE = "MarcadorClienteConfidencial";
    /** wa_id numérico válido para complementar la prueba con llamada directa. */
    private static final String WA_MARCADO = "51999000777";

    @Autowired
    private VinculoProfesionalWhatsAppService vinculoService;

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private RecepcionWebhookService recepcion;

    @Autowired
    private JornadaRepository jornadas;

    private CorrespondenciaDireccionesTwilio direcciones;
    private WebhookTwilioController controlador;

    @BeforeEach
    void prepararDatos() {
        sql.update("UPDATE negocio SET limite_reservas_activas = 5, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);

        TwilioPropiedades propiedades = new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, "+" + NUMERO_A, URL_PUBLICA,
                "barberturno", Duration.ofSeconds(3), "https://api.twilio.com", true,
                Duration.ofSeconds(10), Duration.ofMillis(1500), List.of(
                        new TwilioPropiedades.Canal("+" + NUMERO_A, "barberturno")
                ));
        direcciones = new CorrespondenciaDireccionesTwilio();
        CorrespondenciaMenusTwilio menus = new CorrespondenciaMenusTwilio(reloj);
        FirmaTwilio firma = new FirmaTwilio(propiedades);
        BuzonRespuestasTwilio buzon = new BuzonRespuestasTwilio(propiedades, reloj);
        TraductorEntradaTwilio traductor = new TraductorEntradaTwilio(mapper, reloj, menus, propiedades, direcciones);
        controlador = new WebhookTwilioController(firma, traductor, recepcion, propiedades, buzon);
    }

    private String calcularFirma(String token, String url, Map<String, String> parametros) {
        try {
            StringBuilder mensaje = new StringBuilder(url);
            new TreeMap<>(parametros).forEach((k, v) -> mensaje.append(k).append(v == null ? "" : v));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(mensaje.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Error al calcular firma HMAC", e);
        }
    }

    private void enviarWebhookFirmado(String sid, String fromBsuid, String nombrePerfil, String body) throws Exception {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", ACCOUNT_SID);
        datos.put("MessageSid", sid);
        datos.put("From", fromBsuid);
        datos.put("To", "whatsapp:+" + NUMERO_A);
        datos.put("ProfileName", nombrePerfil);
        datos.put("Body", body);

        String firmaCalculada = calcularFirma(AUTH_TOKEN, URL_PUBLICA + "/api/twilio/webhook", datos);

        StringBuilder form = new StringBuilder();
        for (var entry : datos.entrySet()) {
            if (!form.isEmpty()) form.append("&");
            form.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/twilio/webhook");
        req.setServletPath("/api/twilio/webhook");
        req.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        req.addHeader("X-Twilio-Signature", firmaCalculada);
        req.setContent(form.toString().getBytes(StandardCharsets.UTF_8));

        controlador.recibir(req, firmaCalculada);
        procesador.procesarLote();
    }

    private long enviarMensajeConMarcadores(String phoneNumberId, String nombrePerfil, String texto) {
        String wamid = ConversacionPruebas.nuevoWamid();
        var cuerpo = mapper.createObjectNode();
        cuerpo.put("messaging_product", "whatsapp");
        cuerpo.put("recipient_type", "individual");
        var entry = cuerpo.putArray("entry").addObject();
        var change = entry.putArray("changes").addObject();
        change.put("field", "messages");
        var val = change.putObject("value");
        val.put("messaging_product", "whatsapp");
        val.putObject("metadata").put("phone_number_id", phoneNumberId);
        val.putArray("contacts").addObject().put("wa_id", WA_MARCADO)
                .putObject("profile").put("name", nombrePerfil);
        var msg = val.putArray("messages").addObject();
        msg.put("id", wamid);
        msg.put("from", WA_MARCADO);
        msg.put("timestamp", Long.toString(reloj.instant().getEpochSecond()));
        msg.put("type", "text");
        msg.putObject("text").put("body", texto);

        // La protección de código de vinculación se aplica antes de persistir la cola
        CodigoVinculoWhatsApp.proteger(msg);

        long id = ConversacionPruebas.insertarEvento(sql, mapper, "m:" + wamid, phoneNumberId, cuerpo, reloj.instant());
        procesador.procesarLote();
        return id;
    }

    @Test
    void operacionesVinculoYAgenda_noRegistranCodigoHashBsuidSinteticoNiNombresDeClientes() throws Exception {
        Logger loggerRaiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        loggerRaiz.addAppender(appender);

        try {
            // 1. Generar código de vinculación para el barbero
            CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(barberoCuenta.getId()));
            String codigoEnClaro = dto.codigo();
            String hashCodigo = CodigoVinculoWhatsApp.hash(codigoEnClaro);

            // 2. Entrar con un BSUID por el adaptador (webhook firmado)
            String waIdSintetico = direcciones.identificar(null, BSUID_DIRECCION).orElseThrow();
            enviarWebhookFirmado("SM_CT20_ADAPT_01", BSUID_DIRECCION, MARCADOR_CLIENTE, "vincular " + codigoEnClaro);
            enviarWebhookFirmado("SM_CT20_ADAPT_02", BSUID_DIRECCION, MARCADOR_CLIENTE, "agenda hoy");
            enviarWebhookFirmado("SM_CT20_ADAPT_03", BSUID_DIRECCION, MARCADOR_CLIENTE, "texto no reconocido confidencial");

            // Las operaciones ocurrieron de verdad por el adaptador: el vínculo existe tras «vincular» y «agenda hoy».
            assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject(
                    "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE wa_id=?", Long.class, waIdSintetico)))
                    .isEqualTo(1L);

            // La captura funciona: una línea de control bajo pe.barberturno llega al appender
            LoggerFactory.getLogger("pe.barberturno.control").info("control de captura CT-20");

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .contains("control de captura CT-20");

            // 4. Exigir que los registros no contengan el BSUID, el sintético derivado, WA_MARCADO, el código, el hash ni el nombre
            for (ILoggingEvent evento : appender.list) {
                String mensaje = evento.getFormattedMessage();

                // Código en claro (con y sin guiones)
                assertThat(mensaje)
                        .as("el registro no debe contener el código de vinculación en claro")
                        .doesNotContain(codigoEnClaro)
                        .doesNotContain(codigoEnClaro.replace("-", ""));

                // Hash del código
                assertThat(mensaje)
                        .as("el registro no debe contener el hash SHA-256 del código")
                        .doesNotContain(hashCodigo);

                // BSUID del remitente
                assertThat(mensaje)
                        .as("el registro no debe contener el identificador BSUID")
                        .doesNotContain(MARCADOR_BSUID)
                        .doesNotContain("PE.MARCADOR");

                // Identificador sintético derivado del BSUID
                assertThat(mensaje)
                        .as("el registro no debe contener el identificador sintético derivado")
                        .doesNotContain(waIdSintetico);

                // Identificador WA_MARCADO
                assertThat(mensaje)
                        .as("el registro no debe contener el número WA_MARCADO")
                        .doesNotContain(WA_MARCADO);

                // Nombre de perfil o cliente
                assertThat(mensaje)
                        .as("el registro no debe contener el nombre confidencial del cliente")
                        .doesNotContain(MARCADOR_CLIENTE)
                        .doesNotContain("MarcadorCliente");
            }
        } finally {
            loggerRaiz.detachAppender(appender);
        }
    }
}
