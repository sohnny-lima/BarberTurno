package pe.barberturno.conversation.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.LogLevel;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** CI-8/9: SDK real contra HTTP exclusivamente local, sin clave ni servicio del proveedor. */
class ModeloAnthropicTest {
    private final JsonMapper json = new JsonMapper();
    private final BlockingQueue<Plan> planes = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> recibidas = new LinkedBlockingQueue<>();
    private HttpServer servidor;
    private ExecutorService hilos;
    private AnthropicClient cliente;
    private ModeloConversacional modelo;

    @BeforeEach void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        hilos = Executors.newVirtualThreadPerTaskExecutor();
        servidor.setExecutor(hilos);
        servidor.createContext("/v1/messages", intercambio -> {
            recibidas.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Plan plan = planes.remove();
            try {
                if (plan.demora() > 0) Thread.sleep(plan.demora());
                byte[] cuerpo = plan.cuerpo().getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(plan.estado(), cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            } finally { intercambio.close(); }
        });
        servidor.start();
        configurar(Duration.ofSeconds(2));
    }

    private void configurar(Duration tiempo) {
        if (cliente != null) cliente.close();
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
        var propiedades = propiedades("clave-ficticia-local", url, tiempo);
        cliente = AnthropicOkHttpClient.builder().apiKey(propiedades.clave()).baseUrl(url).maxRetries(0)
                .timeout(tiempo).logLevel(LogLevel.OFF).openTelemetry(false).build();
        modelo = new ModeloAnthropic(cliente, propiedades);
    }

    @AfterEach void cerrar() throws Exception {
        if (cliente != null) cliente.close();
        servidor.stop(0);
        hilos.shutdownNow();
        assertThat(hilos.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void peticion_estrictaCacheEsfuerzoSinMuestreoNiDatosAjenos() throws Exception {
        planes.add(new Plan(200, respuesta("end_turn", "[{\"type\":\"text\",\"text\":\"¿Qué servicio deseas?\"}]"), 0));
        var respuesta = modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Busco un corte"))));
        assertThat(respuesta.texto()).contains("¿Qué servicio deseas?");
        JsonNode enviada = json.readTree(recibidas.remove());
        assertThat(enviada.path("model").asString()).isEqualTo("modelo-ficticio");
        assertThat(enviada.path("output_config").path("effort").asString()).isEqualTo("low");
        assertThat(enviada.path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(enviada.path("system").get(0).path("cache_control").path("type").asString()).isEqualTo("ephemeral");
        for (var herramienta : enviada.path("tools")) {
            assertThat(herramienta.path("strict").asBoolean()).isTrue();
            assertThat(herramienta.path("input_schema").path("additionalProperties").asBoolean()).isFalse();
            assertThat(herramienta.path("input_schema").path("required").size())
                    .isEqualTo(herramienta.path("input_schema").path("properties").size());
        }
        var terminal = java.util.stream.StreamSupport.stream(enviada.path("tools").spliterator(), false)
                .filter(h -> h.path("name").asString().equals("responder_al_cliente")).findFirst().orElseThrow();
        var esquema = terminal.path("input_schema");
        assertThat(esquema.path("properties").size()).isEqualTo(4);
        assertThat(esquema.path("required").toString()).contains("intencion", "falta", "servicio", "profesional");
        assertThat(esquema.path("properties").path("intencion").path("enum").size()).isEqualTo(7);
        assertThat(esquema.path("properties").path("falta").path("enum").size()).isEqualTo(5);
        assertThat(esquema.path("properties").path("profesional").toString()).contains("cualquiera");
        for (String ausente : List.of("temperature", "top_p", "top_k", "tool_choice", "fallbacks"))
            assertThat(enviada.has(ausente)).as(ausente).isFalse();
        assertThat(enviada.toString()).doesNotContain("wa_id", "@", "BT-", "PERSONA", "cliente_id", "negocio_id");
        assertThat(respuesta.uso().entrada()).isEqualTo(10);
        assertThat(respuesta.uso().cacheLeida()).isEqualTo(20);
        assertThat(respuesta.uso().cacheEscrita()).isEqualTo(30);
    }

    @Test void pensamientoYResultados_vuelvenIntactosYJuntos() throws Exception {
        String bloques = """
                [{"type":"thinking","thinking":"razonamiento-ficticio","signature":"firma-ficticia"},
                 {"type":"redacted_thinking","data":"cifrado-ficticio"},
                 {"type":"text","text":"Voy a consultar."},
                 {"type":"tool_use","id":"t1","name":"listar_servicios","input":{}},
                 {"type":"tool_use","id":"t2","name":"listar_profesionales","input":{}}]
                """;
        planes.add(new Plan(200, respuesta("tool_use", bloques), 0));
        var primera = modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Un corte"))));
        recibidas.remove();
        assertThat(primera.llamadas()).hasSize(2);
        assertThat(primera.continuacion().toString()).doesNotContain("razonamiento", "firma-ficticia");
        planes.add(new Plan(200, respuesta("tool_use", """
                [{"type":"tool_use","id":"terminal","name":"responder_al_cliente",
                  "input":{"intencion":"PEDIR_DATO","falta":"DIA","servicio":"s1","profesional":"cualquiera"}}]
                """), 0));
        var cierre = modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Un corte"),
                new MensajeModelo.DelAsistente(primera.texto(), primera.llamadas(), primera.continuacion()),
                new MensajeModelo.ResultadosHerramientas(List.of(new ResultadoHerramienta("t1", "[]", false),
                        new ResultadoHerramienta("t2", "{}", true))))));
        assertThat(cierre.llamadas()).singleElement().satisfies(l -> {
            assertThat(l.herramienta()).isEqualTo(HerramientaIA.RESPONDER_AL_CLIENTE);
            assertThat(l.argumentosJson()).contains("cualquiera");
        });
        var enviados = json.readTree(recibidas.remove()).path("messages");
        assertThat(enviados.get(1).path("content")).isEqualTo(json.readTree(bloques));
        var resultados = enviados.get(2).path("content");
        assertThat(resultados.size()).isEqualTo(2);
        assertThat(resultados.get(0).path("tool_use_id").asString()).isEqualTo("t1");
        assertThat(resultados.get(1).path("is_error").asBoolean()).isTrue();
    }

    @ParameterizedTest @CsvSource({"refusal,RECHAZO", "max_tokens,SALIDA_INVALIDA", "pause_turn,SALIDA_INVALIDA"})
    void paradaNoAceptada_fallaConUsoAntesDeEjecutarHerramientas(String parada, MotivoFalloModelo motivo) {
        planes.add(new Plan(200, respuesta(parada,
                "[{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"listar_servicios\",\"input\":{}}]"), 0));
        assertThatThrownBy(() -> modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Consulta")))))
                .isInstanceOfSatisfying(FalloModelo.class, fallo -> {
                    assertThat(fallo.motivo()).isEqualTo(motivo);
                    assertThat(fallo.uso()).isPresent();
                    assertThat(fallo.uso().get().cacheLeida()).isEqualTo(20);
                });
    }

    @ParameterizedTest @CsvSource({"429,LIMITE_PROVEEDOR", "500,PROVEEDOR"})
    void erroresHttp_sinReintentosOcultos(int estado, MotivoFalloModelo motivo) {
        planes.add(new Plan(estado, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"contenido-no-propagable\"}}", 0));
        assertThatThrownBy(() -> modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Consulta")))))
                .isInstanceOfSatisfying(FalloModelo.class, fallo -> {
                    assertThat(fallo.motivo()).isEqualTo(motivo);
                    assertThat(fallo.uso()).isEmpty();
                    assertThat(fallo.getCause()).isNull();
                    assertThat(fallo.getMessage()).doesNotContain("contenido-no-propagable");
                });
        assertThat(recibidas).hasSize(1);
    }

    @Test void tiempoAgotado_noReintentaNiInventaUso() {
        configurar(Duration.ofMillis(150));
        planes.add(new Plan(200, respuesta("end_turn", "[]"), 1000));
        assertThatThrownBy(() -> modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Consulta")))))
                .isInstanceOfSatisfying(FalloModelo.class, fallo -> {
                    assertThat(fallo.motivo()).isEqualTo(MotivoFalloModelo.TIEMPO_AGOTADO);
                    assertThat(fallo.uso()).isEmpty();
                });
        assertThat(recibidas).hasSize(1);
    }

    @Test void jsonInvalido_esSalidaInvalidaSinCausaCruda() {
        planes.add(new Plan(200, "{json roto y contenido privado", 0));
        assertThatThrownBy(() -> modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Consulta")))))
                .isInstanceOfSatisfying(FalloModelo.class, fallo -> {
                    assertThat(fallo.motivo()).isEqualTo(MotivoFalloModelo.SALIDA_INVALIDA);
                    assertThat(fallo.getCause()).isNull();
                });
    }

    @Test void conexionRechazada_esProveedorSinUsoNiCausaCruda() {
        servidor.stop(0);
        assertThatThrownBy(() -> modelo.responder(peticion(List.of(new MensajeModelo.DelCliente("Consulta")))))
                .isInstanceOfSatisfying(FalloModelo.class, fallo -> {
                    assertThat(fallo.motivo()).isEqualTo(MotivoFalloModelo.PROVEEDOR);
                    assertThat(fallo.uso()).isEmpty();
                    assertThat(fallo.getCause()).isNull();
                });
        assertThat(recibidas).isEmpty();
    }

    @Test void sinClave_arrancaYRespondeNoConfigurado() {
        new ApplicationContextRunner().withUserConfiguration(ConfiguracionModeloIA.class)
                .withBean(IaPropiedades.class, () -> propiedades("", "http://127.0.0.1:1", Duration.ofMillis(100)))
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThatThrownBy(() -> contexto.getBean(ModeloConversacional.class).responder(peticion(List.of())))
                            .isInstanceOfSatisfying(FalloModelo.class, fallo -> assertThat(fallo.motivo()).isEqualTo(MotivoFalloModelo.NO_CONFIGURADO));
                });
        assertThat(recibidas).isEmpty();
    }

    private PeticionModelo peticion(List<MensajeModelo> mensajes) {
        return new PeticionModelo("Instrucciones estables", "{\"negocio\":\"Barbería ficticia\"}", mensajes, List.of(HerramientaIA.values()));
    }

    private static IaPropiedades propiedades(String clave, String url, Duration tiempo) {
        return new IaPropiedades(true, clave, "modelo-ficticio", "low", url, tiempo, Duration.ofSeconds(90),
                2048, 4, 4, new IaPropiedades.Limites(6, 20, java.math.BigDecimal.valueOf(5), Duration.ofMinutes(15)),
                Map.of(), new IaPropiedades.Trabajador(false, Duration.ofSeconds(1), 5));
    }

    private static String respuesta(String parada, String contenido) {
        return "{\"id\":\"msg_local\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"modelo-ficticio\",\"content\":"
                + contenido + ",\"stop_reason\":\"" + parada + "\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":4,\"cache_read_input_tokens\":20,\"cache_creation_input_tokens\":30}}";
    }
    private record Plan(int estado, String cuerpo, long demora) { }
}
