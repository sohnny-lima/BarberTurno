package pe.barberturno.whatsapp.twilio;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.barberturno.conversation.FranjaOpcion;
import pe.barberturno.conversation.MenusConversacion;
import pe.barberturno.conversation.ServicioOpcion;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.whatsapp.MotivoFallo;
import pe.barberturno.whatsapp.ResultadoEnvio;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de {@link ClienteTwilioWhatsApp} contra un servidor local {@link ServidorTwilioFalso} (CT-4).
 * Valida la autenticación HTTP Basic, el empaquetado del formulario x-www-form-urlencoded
 * (From, To, Body y StatusCallback), la conversión de texto, botones y listas generados por
 * {@link MenusConversacion}, los rechazos locales de {@code template} o cuerpos > 1600 caracteres sin HTTP,
 * la clasificación de respuestas del proveedor (201, 429, 5xx, 400 y corte de red),
 * la conservación y actualización de correspondencias en {@link CorrespondenciaMenusTwilio},
 * y el cumplimiento del intervalo mínimo de envío.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class ClienteTwilioWhatsAppTest {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "secreto-token-twilio-123";
    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String PHONE_NUMBER_ID = "14155238886";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String WA_ID = "51999000001";

    private ServidorTwilioFalso servidor;
    private JsonMapper mapper;
    private RelojAjustable reloj;
    private CorrespondenciaMenusTwilio menus;
    private MenusConversacion menusConversacion;
    private TwilioPropiedades propiedades;
    private ClienteTwilioWhatsApp cliente;

    @BeforeEach
    void iniciar() {
        servidor = new ServidorTwilioFalso();
        servidor.iniciar();

        mapper = JsonMapper.builder().build();
        reloj = new RelojAjustable();
        reloj.fijar(Instant.parse("2026-11-11T12:00:00Z"));
        menus = new CorrespondenciaMenusTwilio(reloj);
        menusConversacion = new MenusConversacion();

        propiedades = new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_SANDBOX, URL_PUBLICA,
                "barberturno", Duration.ofSeconds(3), servidor.urlBase(), false, Duration.ofSeconds(10), Duration.ofMillis(1500));
        cliente = new ClienteTwilioWhatsApp(propiedades, menus, mapper,
                new CorrespondenciaDireccionesTwilio(), new BuzonRespuestasTwilio(propiedades));
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private ObjectNode crearMensajeTexto(String waId, String texto) {
        ObjectNode nodo = mapper.createObjectNode();
        nodo.put("messaging_product", "whatsapp");
        nodo.put("recipient_type", "individual");
        nodo.put("to", waId);
        nodo.put("type", "text");
        nodo.putObject("text").put("body", texto);
        return nodo;
    }

    private ObjectNode crearMensajeInteractivo(String waId, ObjectNode interactivo) {
        ObjectNode nodo = mapper.createObjectNode();
        nodo.put("messaging_product", "whatsapp");
        nodo.put("recipient_type", "individual");
        nodo.put("to", waId);
        nodo.put("type", "interactive");
        nodo.set("interactive", interactivo);
        return nodo;
    }

    @Test
    void configurado_conPropiedadesCompletas_devuelveTrue() {
        assertThat(cliente.configurado()).isTrue();
    }

    @Test
    void enviar_textoPlano_emitePeticionHttpConBasicAuthYParametrosCorrectos() {
        servidor.encolarExito("SM_TXT_001");
        ObjectNode cuerpo = crearMensajeTexto(WA_ID, "Su turno ha sido confirmado.");

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) resultado).wamid()).isEqualTo("SM_TXT_001");

        ServidorTwilioFalso.PeticionRegistrada peticion = servidor.ultimaPeticion();
        assertThat(peticion.metodo()).isEqualTo("POST");
        assertThat(peticion.ruta()).isEqualTo("/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json");

        String authEsperada = "Basic " + Base64.getEncoder().encodeToString(
                (ACCOUNT_SID + ":" + AUTH_TOKEN).getBytes(StandardCharsets.UTF_8));
        assertThat(peticion.autorizacion()).isEqualTo(authEsperada);

        Map<String, String> params = peticion.parametros();
        assertThat(params.get("From")).isEqualTo("whatsapp:" + NUMERO_SANDBOX);
        assertThat(params.get("To")).isEqualTo("whatsapp:+" + WA_ID);
        assertThat(params.get("Body")).isEqualTo("Su turno ha sido confirmado.");
        assertThat(params.get("StatusCallback")).isEqualTo(URL_PUBLICA + "/api/twilio/estado");
    }

    @Test
    void enviar_interactivoBotones_formatoExactoYGuardaCorrespondencia() {
        servidor.encolarExito("SM_BTN_001");
        ObjectNode interactivo = menusConversacion.menuPrincipal("Seleccione una opción:");
        ObjectNode cuerpo = crearMensajeInteractivo(WA_ID, interactivo);

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Aceptado.class);

        ServidorTwilioFalso.PeticionRegistrada peticion = servidor.ultimaPeticion();
        String bodyEnviado = peticion.parametros().get("Body");
        assertThat(bodyEnviado).isEqualTo("""
                Seleccione una opción:

                1. Reservar
                2. Mis citas
                3. Hablar con alguien
                Responda con el número.""");

        // Verifica que se guardó la correspondencia en memoria
        var seleccion1 = menus.consumir(WA_ID, "1", "SM_IN_1");
        assertThat(seleccion1).isPresent();
        assertThat(seleccion1.get().tipo()).isEqualTo("button_reply");
        assertThat(seleccion1.get().id()).isEqualTo("m:RESERVAR");
        assertThat(seleccion1.get().titulo()).isEqualTo("Reservar");
    }

    @Test
    void enviar_interactivoLista_formatoExactoConDescripcionYGuardaCorrespondencia() {
        servidor.encolarExito("SM_LST_001");
        List<ServicioOpcion> listaServicios = List.of(
                new ServicioOpcion(1L, "Corte clásico", (short) 30, BigDecimal.valueOf(25.0)),
                new ServicioOpcion(2L, "Afeitado tradicional", (short) 20, BigDecimal.valueOf(15.0))
        );
        ObjectNode interactivo = menusConversacion.servicios("Nuestros servicios:", listaServicios, 0);
        ObjectNode cuerpo = crearMensajeInteractivo(WA_ID, interactivo);

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Aceptado.class);

        ServidorTwilioFalso.PeticionRegistrada peticion = servidor.ultimaPeticion();
        String bodyEnviado = peticion.parametros().get("Body");
        assertThat(bodyEnviado).isEqualTo("""
                Nuestros servicios:

                1. Corte clásico — 30 min · S/ 25,00
                2. Afeitado tradicional — 20 min · S/ 15,00
                Responda con el número.""");

        // Verifica que la opción 2 se resuelve como list_reply con el identificador del servicio
        var seleccion2 = menus.consumir(WA_ID, "2", "SM_IN_2");
        assertThat(seleccion2).isPresent();
        assertThat(seleccion2.get().tipo()).isEqualTo("list_reply");
        assertThat(seleccion2.get().id()).isEqualTo("s:2");
        assertThat(seleccion2.get().titulo()).isEqualTo("Afeitado tradicional");
    }

    @Test
    void enviar_interactivoFranjas_formatoConFechaCompletaLimaYGuardaCorrespondencia() {
        servidor.encolarExito("SM_FRJ_001");
        // Jueves 1 de octubre de 2026: 17:00 UTC = 12:00 en Lima (UTC-5), 17:30 UTC = 12:30 en Lima
        Instant inicio1 = Instant.parse("2026-10-01T17:00:00Z");
        Instant fin1 = Instant.parse("2026-10-01T17:30:00Z");
        Instant inicio2 = Instant.parse("2026-10-01T17:30:00Z");
        Instant fin2 = Instant.parse("2026-10-01T18:00:00Z");

        List<FranjaOpcion> franjas = List.of(
                new FranjaOpcion(2L, "Carlos", inicio1, fin1),
                new FranjaOpcion(2L, "Carlos", inicio2, fin2)
        );
        LocalDate dia = LocalDate.of(2026, 10, 1);
        ObjectNode interactivo = menusConversacion.franjas("Horarios disponibles:", 1L, 2L, dia, franjas, 0);
        ObjectNode cuerpo = crearMensajeInteractivo(WA_ID, interactivo);

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Aceptado.class);

        ServidorTwilioFalso.PeticionRegistrada peticion = servidor.ultimaPeticion();
        String bodyEnviado = peticion.parametros().get("Body");

        // Verifica formato del texto numerado con fecha completa en español y hora de Lima (CT-4)
        assertThat(bodyEnviado).contains("Horarios disponibles:");
        assertThat(bodyEnviado).contains("1. 12:00 · Carlos — jueves 1 de octubre de 2026");
        assertThat(bodyEnviado).contains("2. 12:30 · Carlos — jueves 1 de octubre de 2026");
        assertThat(bodyEnviado).contains("Responda con el número.");

        // Aserciones explícitas de componentes de fecha y hora
        assertThat(bodyEnviado).contains("jueves");
        assertThat(bodyEnviado).contains("1 de octubre de 2026");
        assertThat(bodyEnviado).contains("12:00");
        assertThat(bodyEnviado).contains("12:30");

        // Verifica correspondencia numérica guardada en el menú
        var seleccion1 = menus.consumir(WA_ID, "1", "SM_IN_FRJ_1");
        assertThat(seleccion1).isPresent();
        assertThat(seleccion1.get().tipo()).isEqualTo("list_reply");
        assertThat(seleccion1.get().titulo()).isEqualTo("12:00 · Carlos");
        assertThat(seleccion1.get().id()).isEqualTo("f:1:2:" + inicio1.getEpochSecond());
    }

    @Test
    void enviar_template_rechazoPermanenteLocalSinLlamadaHttp() {
        ObjectNode cuerpo = mapper.createObjectNode();
        cuerpo.put("messaging_product", "whatsapp");
        cuerpo.put("to", WA_ID);
        cuerpo.put("type", "template");
        cuerpo.putObject("template").put("name", "recordatorio_cita");

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        ResultadoEnvio.Rechazado rechazo = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazo.transitorio()).isFalse();
        assertThat(rechazo.motivo()).isEqualTo(MotivoFallo.PLANTILLA_RECHAZADA);
        assertThat(servidor.peticiones()).isEmpty();
    }

    @Test
    void enviar_cuerpoMayorDe1600Caracteres_rechazoPermanenteLocalSinLlamadaHttp() {
        String textoLargo = "A".repeat(1601);
        ObjectNode cuerpo = crearMensajeTexto(WA_ID, textoLargo);

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        ResultadoEnvio.Rechazado rechazo = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazo.transitorio()).isFalse();
        assertThat(rechazo.motivo()).isEqualTo(MotivoFallo.SOLICITUD_RECHAZADA);
        assertThat(servidor.peticiones()).isEmpty();
    }

    @Test
    void enviar_respuesta429_clasificaComoTransitorio() {
        servidor.encolarError(429, 20429, "Too Many Requests");
        ObjectNode cuerpo = crearMensajeTexto(WA_ID, "Prueba 429");

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        ResultadoEnvio.Rechazado rechazo = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazo.estadoHttp()).isEqualTo(429);
        assertThat(rechazo.transitorio()).isTrue();
        assertThat(rechazo.codigoMeta()).isEqualTo(OptionalInt.of(20429));
    }

    @Test
    void enviar_respuesta500_clasificaComoTransitorio() {
        servidor.encolarError(500, 50000, "Internal Server Error");
        ObjectNode cuerpo = crearMensajeTexto(WA_ID, "Prueba 500");

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        ResultadoEnvio.Rechazado rechazo = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazo.estadoHttp()).isEqualTo(500);
        assertThat(rechazo.transitorio()).isTrue();
    }

    @Test
    void enviar_respuesta400_clasificaComoPermanenteConCodigoTwilio() {
        servidor.encolarError(400, 21614, "To number is not a valid WhatsApp-enabled number.");
        ObjectNode cuerpo = crearMensajeTexto(WA_ID, "Prueba 400");

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        ResultadoEnvio.Rechazado rechazo = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazo.estadoHttp()).isEqualTo(400);
        assertThat(rechazo.transitorio()).isFalse();
        assertThat(rechazo.codigoMeta()).isEqualTo(OptionalInt.of(21614));
        assertThat(rechazo.motivo()).isEqualTo(MotivoFallo.SOLICITUD_RECHAZADA);
    }

    @Test
    void enviar_corteAbruptoDeRed_clasificaComoDesconocido() {
        servidor.encolarCorteConexion();
        ObjectNode cuerpo = crearMensajeTexto(WA_ID, "Prueba corte red");

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Desconocido.class);
    }

    @Test
    void enviar_falloProbado400_conservaMenuAnterior() {
        // 1. Guardar menú previo con identificador y título distintos a m:RESERVAR
        menus.guardar(WA_ID, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", "m:ANTERIOR_123", "Menú previo especial")
        ));

        // 2. Enviar interactivo que resulta en 400 (su primera opción es m:RESERVAR)
        servidor.encolarError(400, 21614, "Error ficticio");
        ObjectNode interactivo = menusConversacion.menuPrincipal("Nuevo menú:");
        ObjectNode cuerpo = crearMensajeInteractivo(WA_ID, interactivo);

        ResultadoEnvio resultado = cliente.enviar(PHONE_NUMBER_ID, cuerpo);
        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);

        // 3. El menú anterior no se sobreescribió: permanece m:ANTERIOR_123 y no m:RESERVAR
        var opcion = menus.consumir(WA_ID, "1", "SM_CHECK_PREVIO");
        assertThat(opcion).isPresent();
        assertThat(opcion.get().id()).isEqualTo("m:ANTERIOR_123");
        assertThat(opcion.get().titulo()).isEqualTo("Menú previo especial");
        assertThat(opcion.get().id()).isNotEqualTo("m:RESERVAR");
    }

    @Test
    void enviar_dosEnviosSeguidos_separaAlMenosIntervaloConfigurado() {
        servidor.encolarExito("SM_INT_001");
        servidor.encolarExito("SM_INT_002");
        ObjectNode cuerpo1 = crearMensajeTexto(WA_ID, "Primer envío");
        ObjectNode cuerpo2 = crearMensajeTexto(WA_ID, "Segundo envío");

        ResultadoEnvio res1 = cliente.enviar(PHONE_NUMBER_ID, cuerpo1);
        ResultadoEnvio res2 = cliente.enviar(PHONE_NUMBER_ID, cuerpo2);

        // Exige ambos resultados aceptados por el proveedor
        assertThat(res1).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) res1).wamid()).isEqualTo("SM_INT_001");
        assertThat(res2).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) res2).wamid()).isEqualTo("SM_INT_002");

        var peticiones = servidor.peticiones();
        assertThat(peticiones).hasSize(2);

        var peticion1 = peticiones.get(0);
        var peticion2 = peticiones.get(1);

        // Separación monotónica registrada en el servidor falso: desde el fin de la respuesta 1 hasta el inicio de la 2
        long separacionNanos = peticion2.inicioNano() - peticion1.finNano();
        long separacionMs = TimeUnit.NANOSECONDS.toMillis(separacionNanos);

        // TwilioPropiedades impone mínimo 3 s; la siguiente petición debe iniciarse al menos 3000 ms tras responder la anterior
        assertThat(separacionMs).isGreaterThanOrEqualTo(3000);
    }

    @Test
    void propiedades_intervaloMenorDeTresSegundos_esRechazadoEnConstruccion() {
        assertThatThrownBy(() -> new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_SANDBOX, URL_PUBLICA,
                "barberturno", Duration.ofMillis(200), "https://api.twilio.com", false, Duration.ofSeconds(10), Duration.ofMillis(1500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Configuración de Twilio Sandbox fuera de rango.");
    }
}
