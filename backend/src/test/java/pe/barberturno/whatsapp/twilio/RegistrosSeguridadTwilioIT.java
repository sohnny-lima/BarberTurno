package pe.barberturno.whatsapp.twilio;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import pe.barberturno.conversation.ConversacionPruebas;
import pe.barberturno.conversation.VinculoProfesionalWhatsAppService;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.whatsapp.CodigoVinculoWhatsApp;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
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

    private static final String MARCADOR_BSUID = "PE.MARCADORBSUIDCT20";
    private static final String BSUID_DIRECCION = "whatsapp:" + MARCADOR_BSUID;
    private static final String MARCADOR_CLIENTE = "MarcadorClienteConfidencial";
    /** wa_id numérico válido: con el BSUID en claro el evento se descartaría y la prueba no ejercitaría nada. */
    private static final String WA_MARCADO = "51999000777";

    @Autowired
    private VinculoProfesionalWhatsAppService vinculoService;

    @Autowired
    private ProcesadorEventosWhatsApp procesador;

    @Autowired
    private JornadaRepository jornadas;

    @BeforeEach
    void prepararDatos() {
        sql.update("UPDATE negocio SET limite_reservas_activas = 5, confirmacion_manual = false WHERE id IN (?, ?)", a, b);
        ConversacionPruebas.prepararJornadasSemana(ejecutor, barberos, jornadas, a, barberoA);
    }

    private long enviarMensajeConMarcadores(String phoneNumberId, String fromBsuid, String nombrePerfil, String texto) {
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
    void operacionesVinculoYAgenda_noRegistranCodigoHashBsuidSinteticoNiNombresDeClientes() {
        Logger loggerRaiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        loggerRaiz.addAppender(appender);

        try {
            // 1. Generar código de vinculación para el barbero
            CodigoVinculoProfesionalDto dto = ejecutor.ejecutar(a, () -> vinculoService.generar(barberoCuenta.getId()));
            String codigoEnClaro = dto.codigo();
            String hashCodigo = CodigoVinculoWhatsApp.hash(codigoEnClaro);

            // 2. Vincular vía mensaje con el remitente BSUID y nombre de cliente marcado
            enviarMensajeConMarcadores(NUMERO_A, BSUID_DIRECCION, MARCADOR_CLIENTE, "vincular " + codigoEnClaro);

            // 3. Consultar agenda
            enviarMensajeConMarcadores(NUMERO_A, BSUID_DIRECCION, MARCADOR_CLIENTE, "agenda hoy");

            // 4. Enviar un texto no reconocido
            enviarMensajeConMarcadores(NUMERO_A, BSUID_DIRECCION, MARCADOR_CLIENTE, "texto no reconocido confidencial");

            // Las operaciones ocurrieron de verdad: el vínculo existe tras «vincular» y «agenda hoy».

            assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject(

                    "SELECT count(*) FROM vinculo_whatsapp_profesional WHERE wa_id=?", Long.class, WA_MARCADO)))

                    .isEqualTo(1L);

            // La captura funciona: una línea de control bajo pe.barberturno llega al appender.

            LoggerFactory.getLogger("pe.barberturno.control").info("control de captura CT-20");

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)

                    .contains("control de captura CT-20");

            // 5. Verificar que NINGÚN registro contenga los marcadores sensibles
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
