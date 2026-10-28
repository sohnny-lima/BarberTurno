package pe.barberturno.whatsapp;

import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias de {@link ClienteGraphWhatsAppRest} y su clasificación de errores
 * (12-v2-09-whatsapp-salida.md §4.1 y §4.2, CI-6 y CI-7).
 * Valida la comunicación HTTP con un {@link ServidorGraphFalso}, la cabecera de autenticación, la ruta,
 * y la clasificación estricta de respuestas aceptadas, errores transitorios, errores permanentes, 3xx
 * sin redirección, caídas de red y respuestas desconocidas.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class ClienteGraphWhatsAppTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static ServidorGraphFalso servidor;
    private static SalidaWhatsAppPropiedades propiedades;
    private static ClienteGraphWhatsApp cliente;

    @BeforeAll
    static void iniciarServidor() {
        servidor = new ServidorGraphFalso();
        servidor.iniciar();
        var graph = new SalidaWhatsAppPropiedades.Graph(servidor.urlBase(), "v26.0", "token-ficticio-123",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
        propiedades = new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 5,
                Duration.ofSeconds(30), Duration.ofMinutes(5), 2, Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofMinutes(5), graph);
        cliente = new ClienteGraphWhatsAppRest(propiedades);
    }

    @AfterAll
    static void detenerServidor() {
        if (servidor != null) {
            servidor.close();
        }
    }

    @BeforeEach
    void limpiar() {
        servidor.limpiar();
    }

    private ObjectNode cuerpoPrueba() {
        ObjectNode cuerpo = MAPPER.createObjectNode();
        cuerpo.put("messaging_product", "whatsapp");
        cuerpo.put("recipient_type", "individual");
        cuerpo.put("to", "51999000001");
        cuerpo.put("type", "text");
        cuerpo.putObject("text").put("body", "Mensaje de prueba");
        return cuerpo;
    }

    @Test
    void enviar_exito200ConId_devuelveAceptadoYVerificaRutaYCabeceras() {
        servidor.encolarExito("wamid.FICTICIO_001");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) resultado).wamid()).isEqualTo("wamid.FICTICIO_001");

        var peticion = servidor.ultimaPeticion();
        assertThat(peticion.metodo()).isEqualTo("POST");
        assertThat(peticion.ruta()).isEqualTo("/v26.0/10001/messages");
        assertThat(peticion.cabeceras().get("Authorization")).contains("Bearer token-ficticio-123");
        assertThat(peticion.cuerpoJson().get("messaging_product").asString()).isEqualTo("whatsapp");
        assertThat(peticion.cuerpoJson().get("to").asString()).isEqualTo("51999000001");
    }

    @ParameterizedTest
    @ValueSource(ints = {130429, 131000, 131016, 131056, 1, 2, 4})
    void enviar_erroresMetaTransitorios_clasificadosComoRechazadoTransitorio(int codigoMeta) {
        servidor.encolarErrorMeta(400, codigoMeta, "Error transitorio");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isTrue();
        assertThat(rechazado.codigoMeta()).contains(codigoMeta);
        assertThat(rechazado.motivo()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503})
    void enviar_http429o5xxSinCodigoMeta_clasificadosComoRechazadoTransitorio(int estadoHttp) {
        servidor.encolarRespuesta(estadoHttp, "{\"error\":{\"message\":\"Servicio saturado\"}}");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isTrue();
        assertThat(rechazado.estadoHttp()).isEqualTo(estadoHttp);
        assertThat(rechazado.motivo()).isNull();
    }

    @Test
    void enviar_ventanaCerrada131047_clasificadoComoVentanaCerrada() {
        servidor.encolarErrorMeta(400, 131047, "Re-engagement message");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isFalse();
        assertThat(rechazado.motivo()).isEqualTo(MotivoFallo.VENTANA_CERRADA);
        assertThat(rechazado.codigoMeta()).contains(131047);
    }

    @ParameterizedTest
    @ValueSource(ints = {131026, 131021, 131051})
    void enviar_destinoNoValido_clasificadoComoDestinoNoValido(int codigoMeta) {
        servidor.encolarErrorMeta(400, codigoMeta, "Receiver is not valid");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isFalse();
        assertThat(rechazado.motivo()).isEqualTo(MotivoFallo.DESTINO_NO_VALIDO);
        assertThat(rechazado.codigoMeta()).contains(codigoMeta);
    }

    @ParameterizedTest
    @ValueSource(ints = {132000, 132001, 132005, 132007, 132012, 132015, 132016})
    void enviar_plantillaRechazada_clasificadoComoPlantillaRechazada(int codigoMeta) {
        servidor.encolarErrorMeta(400, codigoMeta, "Template error");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isFalse();
        assertThat(rechazado.motivo()).isEqualTo(MotivoFallo.PLANTILLA_RECHAZADA);
        assertThat(rechazado.codigoMeta()).contains(codigoMeta);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 190, 200, 131005})
    void enviar_credencialRechazada_clasificadoComoCredencialRechazada(int codigoMeta) {
        servidor.encolarErrorMeta(401, codigoMeta, "Invalid OAuth access token");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isFalse();
        assertThat(rechazado.motivo()).isEqualTo(MotivoFallo.CREDENCIAL_RECHAZADA);
        assertThat(rechazado.codigoMeta()).contains(codigoMeta);
    }

    @ParameterizedTest
    @ValueSource(ints = {368, 131031, 131042, 131045, 131048, 131057, 133010})
    void enviar_cuentaRestringida_clasificadoComoCuentaRestringida(int codigoMeta) {
        servidor.encolarErrorMeta(403, codigoMeta, "Account has been restricted");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isFalse();
        assertThat(rechazado.motivo()).isEqualTo(MotivoFallo.CUENTA_RESTRINGIDA);
        assertThat(rechazado.codigoMeta()).contains(codigoMeta);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 131008, 131009, 135000, 99999})
    void enviar_otros4xx_clasificadoComoSolicitudRechazada(int codigoMeta) {
        servidor.encolarErrorMeta(400, codigoMeta, "Bad request parameters");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isFalse();
        assertThat(rechazado.motivo()).isEqualTo(MotivoFallo.SOLICITUD_RECHAZADA);
        assertThat(rechazado.codigoMeta()).contains(codigoMeta);
    }

    @Test
    void enviar_respuesta3xx_sinSeguirRedireccion_clasificadoComoSolicitudRechazada() {
        servidor.encolarRespuesta(302, "<html><body>Moved</body></html>");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Rechazado.class);
        var rechazado = (ResultadoEnvio.Rechazado) resultado;
        assertThat(rechazado.transitorio()).isFalse();
        assertThat(rechazado.estadoHttp()).isEqualTo(302);
        assertThat(rechazado.motivo()).isEqualTo(MotivoFallo.SOLICITUD_RECHAZADA);
    }

    @Test
    void enviar_tiempoDeRespuestaAgotado_clasificadoComoDesconocido() {
        // La demora es de 3s y el timeout de respuesta es de 2s
        servidor.encolarDemora(Duration.ofSeconds(3), 200, "{\"messages\":[{\"id\":\"wamid.tardio\"}]}");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Desconocido.class);
    }

    @Test
    void enviar_corteAbruptoDeConexion_clasificadoComoDesconocido() {
        servidor.encolarCorteConexion();

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Desconocido.class);
    }

    @Test
    void enviar_exito200SinIdOCuerpoVacio_clasificadoComoDesconocido() {
        servidor.encolarRespuesta(200, "{\"messaging_product\":\"whatsapp\"}");

        ResultadoEnvio resultado = cliente.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Desconocido.class);
    }

    @Test
    void enviar_conexionRechazada_clasificadoComoNoEnviado() {
        // Apuntar a un puerto donde nadie escucha
        var graphSinServidor = new SalidaWhatsAppPropiedades.Graph("http://127.0.0.1:1", "v26.0", "token",
                Duration.ofMillis(300), Duration.ofMillis(500));
        var props = new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 5,
                Duration.ofSeconds(30), Duration.ofMinutes(5), 2, Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofMinutes(5), graphSinServidor);
        var clienteSinRed = new ClienteGraphWhatsAppRest(props);

        ResultadoEnvio resultado = clienteSinRed.enviar("10001", cuerpoPrueba());

        assertThat(resultado).isInstanceOf(ResultadoEnvio.NoEnviado.class);
    }
}
