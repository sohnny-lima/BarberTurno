package pe.barberturno.whatsapp.twilio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.whatsapp.ResultadoEnvio;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de retención y entrega de respuestas tardías a destinos sintéticos BSUID (CT-19, V2-DA-108).
 * Valida que una respuesta diferida a un sintético sin espera abierta no intente HTTP REST hacia el servidor simulado;
 * que se entregue por TwiML delante de las respuestas del siguiente webhook de esa misma persona y número;
 * que si transcurren más de diez minutos antes del siguiente webhook la respuesta retenida caduque y se descarte;
 * que el buzón retenga como máximo cinco respuestas descartando las sobrantes;
 * y que un destinatario E.164 tradicional mantenga el despacho directo por REST.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class RespuestasTardiasTwilioTest {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "secreto-ct19-test";
    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String PHONE_ID = "14155238886";
    private static final String NUMERO_A = "+14155238886";
    private static final String PHONE_ID_A = "14155238886";
    private static final String NUMERO_B = "+15005550006";
    private static final String PHONE_ID_B = "15005550006";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String SINTETICO = "91122334455667788990";
    private static final String E164 = "51999000111";

    private ServidorTwilioFalso servidor;
    private RelojAjustable reloj;
    private JsonMapper mapper;
    private CorrespondenciaMenusTwilio menus;
    private CorrespondenciaDireccionesTwilio direcciones;
    private BuzonRespuestasTwilio buzon;
    private ClienteTwilioWhatsApp cliente;

    @BeforeEach
    void iniciar() {
        servidor = new ServidorTwilioFalso();
        servidor.iniciar();

        reloj = new RelojAjustable();
        reloj.fijar(Instant.parse("2026-11-12T12:00:00Z"));
        mapper = JsonMapper.builder().build();
        menus = new CorrespondenciaMenusTwilio(reloj);
        direcciones = new CorrespondenciaDireccionesTwilio();

        TwilioPropiedades propiedades = new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_SANDBOX, URL_PUBLICA,
                "barberturno", Duration.ofSeconds(3), servidor.urlBase(), true,
                Duration.ofSeconds(10), Duration.ofMillis(1500), List.of(
                        new TwilioPropiedades.Canal(NUMERO_A, "barberturno"),
                        new TwilioPropiedades.Canal(NUMERO_B, "sur")
                ));

        buzon = new BuzonRespuestasTwilio(propiedades, reloj);
        cliente = new ClienteTwilioWhatsApp(propiedades, menus, mapper, direcciones, buzon);
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

    @Test
    void respuestaTardiaSintetico_noHaceHttp_ySeEntregaDelanteDelSiguienteWebhook() {
        // 1. Enviar respuesta tardía cuando no hay ninguna espera abierta
        ResultadoEnvio resultadoTardio = cliente.enviar(PHONE_ID, crearMensajeTexto(SINTETICO, "Respuesta diferida del bot"));

        // CT-19: Aceptada como twiml-pendiente y sin ninguna llamada HTTP al servidor
        assertThat(resultadoTardio).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) resultadoTardio).wamid()).startsWith("twiml-pendiente:");
        assertThat(servidor.peticiones()).as("no debe realizarse petición HTTP para sintético").isEmpty();

        // 2. Llega el siguiente webhook de esa persona y abre la espera
        var esperaSiguiente = buzon.abrir(PHONE_ID, SINTETICO);
        assertThat(esperaSiguiente).isPresent();

        // 3. Durante el procesamiento del nuevo webhook se genera una nueva respuesta
        ResultadoEnvio resultadoNuevo = cliente.enviar(PHONE_ID, crearMensajeTexto(SINTETICO, "Respuesta del nuevo mensaje"));
        assertThat(resultadoNuevo).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) resultadoNuevo).wamid()).startsWith("twiml:");

        // 4. Los mensajes recogidos deben tener la respuesta tardía DELANTE de la nueva
        List<String> mensajesEntregados = buzon.tomar(esperaSiguiente.get());
        assertThat(mensajesEntregados).hasSize(2);
        assertThat(mensajesEntregados.get(0)).isEqualTo("Respuesta diferida del bot");
        assertThat(mensajesEntregados.get(1)).isEqualTo("Respuesta del nuevo mensaje");
        assertThat(servidor.peticiones()).isEmpty();
    }

    @Test
    void respuestaTardiaSintetico_siCaducaALos10Minutos_seDescarta() {
        // Enviar respuesta tardía en T0
        cliente.enviar(PHONE_ID, crearMensajeTexto(SINTETICO, "Respuesta que caducará"));
        assertThat(servidor.peticiones()).isEmpty();

        // Avanzar el reloj 10 minutos y 1 segundo
        reloj.adelantar(Duration.ofSeconds(601));

        // El siguiente webhook abre espera: la respuesta caducada fue purgada
        var esperaSiguiente = buzon.abrir(PHONE_ID, SINTETICO);
        assertThat(esperaSiguiente).isPresent();

        List<String> mensajes = buzon.tomar(esperaSiguiente.get());
        assertThat(mensajes).as("la respuesta retenida debe descartarse tras 10 minutos").isEmpty();
    }

    @Test
    void respuestaTardiaSintetico_masDeCinco_descartaLasQueSobran() {
        // Enviar 5 respuestas tardías: todas se retienen
        for (int i = 1; i <= 5; i++) {
            ResultadoEnvio res = cliente.enviar(PHONE_ID, crearMensajeTexto(SINTETICO, "Mensaje retenido " + i));
            assertThat(res).isInstanceOf(ResultadoEnvio.Aceptado.class);
            assertThat(((ResultadoEnvio.Aceptado) res).wamid()).startsWith("twiml-pendiente:");
        }

        // La sexta respuesta excede el límite de 5 retenidas y se descarta
        ResultadoEnvio res6 = cliente.enviar(PHONE_ID, crearMensajeTexto(SINTETICO, "Mensaje retenido 6 sobrante"));
        assertThat(res6).isInstanceOf(ResultadoEnvio.Aceptado.class);

        // Al abrir la espera del siguiente webhook, contiene exactamente las 5 primeras
        var esperaSiguiente = buzon.abrir(PHONE_ID, SINTETICO);
        assertThat(esperaSiguiente).isPresent();

        List<String> mensajes = buzon.tomar(esperaSiguiente.get());
        assertThat(mensajes).hasSize(5);
        assertThat(mensajes).containsExactly(
                "Mensaje retenido 1",
                "Mensaje retenido 2",
                "Mensaje retenido 3",
                "Mensaje retenido 4",
                "Mensaje retenido 5"
        );
        assertThat(mensajes).doesNotContain("Mensaje retenido 6 sobrante");
        assertThat(servidor.peticiones()).isEmpty();
    }

    @Test
    void destinoE164_noSeRetiene_siguePorRest() {
        direcciones.identificar(E164, "whatsapp:+" + E164);
        servidor.encolarExito("SM_REST_E164_CT19");

        // Sin espera abierta, un destinatario E.164 no se retiene y se despacha de inmediato por REST
        ResultadoEnvio resultado = cliente.enviar(PHONE_ID, crearMensajeTexto(E164, "Mensaje directo a E.164"));

        assertThat(resultado).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) resultado).wamid()).isEqualTo("SM_REST_E164_CT19");

        assertThat(servidor.peticiones()).hasSize(1);
        ServidorTwilioFalso.PeticionRegistrada peticion = servidor.ultimaPeticion();
        assertThat(peticion.parametros().get("To")).isEqualTo("whatsapp:+" + E164);

        // Si después abre una espera, el buzón está vacío (no quedó nada retenido)
        var espera = buzon.abrir(PHONE_ID, E164);
        assertThat(espera).isPresent();
        assertThat(buzon.tomar(espera.get())).isEmpty();
    }

    @Test
    void mismoBsuid_conEsperasYRetenidasSimultaneasEnAyB_cadaUnaRecibeExclusivamenteSusTextos() {
        // 1. Respuestas tardías retenidas simultáneamente para el mismo BSUID sintético en canal A y en canal B
        ResultadoEnvio resRetenidaA = cliente.enviar(PHONE_ID_A, crearMensajeTexto(SINTETICO, "Retenida de negocio A"));
        ResultadoEnvio resRetenidaB = cliente.enviar(PHONE_ID_B, crearMensajeTexto(SINTETICO, "Retenida de negocio B"));

        assertThat(resRetenidaA).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(resRetenidaB).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) resRetenidaA).wamid()).startsWith("twiml-pendiente:");
        assertThat(((ResultadoEnvio.Aceptado) resRetenidaB).wamid()).startsWith("twiml-pendiente:");
        assertThat(servidor.peticiones()).as("no debe realizarse petición REST para sintético").isEmpty();

        // 2. Abrir esperas TwiML simultáneas en A y B para el mismo BSUID
        var esperaA = buzon.abrir(PHONE_ID_A, SINTETICO);
        var esperaB = buzon.abrir(PHONE_ID_B, SINTETICO);
        assertThat(esperaA).isPresent();
        assertThat(esperaB).isPresent();

        // 3. Durante las esperas abiertas, se envían nuevas respuestas para A y para B
        ResultadoEnvio resNuevaA = cliente.enviar(PHONE_ID_A, crearMensajeTexto(SINTETICO, "Nueva respuesta de A"));
        ResultadoEnvio resNuevaB = cliente.enviar(PHONE_ID_B, crearMensajeTexto(SINTETICO, "Nueva respuesta de B"));
        assertThat(resNuevaA).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(resNuevaB).isInstanceOf(ResultadoEnvio.Aceptado.class);
        assertThat(((ResultadoEnvio.Aceptado) resNuevaA).wamid()).startsWith("twiml:");
        assertThat(((ResultadoEnvio.Aceptado) resNuevaB).wamid()).startsWith("twiml:");

        // 4. Cada espera recoge exclusivamente sus propios textos, en el orden correcto (retenida delante de nueva)
        List<String> mensajesA = buzon.tomar(esperaA.get());
        List<String> mensajesB = buzon.tomar(esperaB.get());

        assertThat(mensajesA).as("A debe recibir solo sus mensajes y en orden")
                .containsExactly("Retenida de negocio A", "Nueva respuesta de A");
        assertThat(mensajesB).as("B debe recibir solo sus mensajes y en orden")
                .containsExactly("Retenida de negocio B", "Nueva respuesta de B");

        assertThat(mensajesA).doesNotContain("Retenida de negocio B", "Nueva respuesta de B");
        assertThat(mensajesB).doesNotContain("Retenida de negocio A", "Nueva respuesta de A");
        assertThat(servidor.peticiones()).as("ninguna respuesta debió salir por REST").isEmpty();
    }
}
