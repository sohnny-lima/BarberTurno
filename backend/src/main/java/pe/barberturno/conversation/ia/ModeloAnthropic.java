package pe.barberturno.conversation.ia;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;

/**
 * Adaptador de Messages estable con SDK oficial y sin reintentos ocultos. Encapsula Jackson 2,
 * devuelve intactos los bloques del asistente y nunca incluye el cuerpo del proveedor en errores.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class ModeloAnthropic implements ModeloConversacional {
    private final AnthropicClient cliente;
    private final IaPropiedades propiedades;
    private final JsonMapper json = ObjectMappers.jsonMapper();

    /**
     * Recibe un cliente explícito, sin leer variables de entorno del SDK.
     * @param cliente cliente cerrado por Spring al detener la aplicación
     * @param propiedades modelo, esfuerzo y topes autorizados
     */
    public ModeloAnthropic(AnthropicClient cliente, IaPropiedades propiedades) {
        this.cliente = cliente; this.propiedades = propiedades;
    }

    /** {@inheritDoc} */
    @Override
    public RespuestaModelo responder(PeticionModelo peticion) {
        long inicio = System.nanoTime();
        UsoModelo uso = null;
        try {
            var cuerpo = json.createObjectNode().put("model", propiedades.modelo()).put("max_tokens", propiedades.maxTokens());
            var sistema = cuerpo.putArray("system");
            sistema.addObject().put("type", "text").put("text", peticion.instrucciones())
                    .putObject("cache_control").put("type", "ephemeral");
            sistema.addObject().put("type", "text").put("text", peticion.contexto());
            cuerpo.putObject("output_config").put("effort", propiedades.esfuerzo());
            var herramientas = cuerpo.putArray("tools");
            for (var h : peticion.herramientas()) herramientas.addObject().put("name", h.nombre())
                    .put("description", h.descripcion()).put("strict", true).set("input_schema", json.readTree(h.esquemaJson()));
            var mensajes = cuerpo.putArray("messages");
            for (var mensaje : peticion.mensajes()) {
                switch (mensaje) {
                    case MensajeModelo.DelCliente(var texto) -> mensajes.addObject().put("role", "user").put("content", texto);
                    case MensajeModelo.DelAsistente asistente -> {
                        if (!(asistente.continuacion() instanceof ContinuacionAnthropic continuacion))
                            throw fallo(MotivoFalloModelo.SALIDA_INVALIDA, null);
                        mensajes.addObject().put("role", "assistant").set("content", continuacion.bloques.deepCopy());
                    }
                    case MensajeModelo.ResultadosHerramientas(var resultados) -> {
                        var bloques = mensajes.addObject().put("role", "user").putArray("content");
                        for (var resultado : resultados) bloques.addObject().put("type", "tool_result")
                                .put("tool_use_id", resultado.llamadaId()).put("content", resultado.json()).put("is_error", resultado.error());
                    }
                }
            }
            var params = MessageCreateParams.builder().body(json.treeToValue(cuerpo, MessageCreateParams.Body.class)).build();
            JsonNode respuesta = json.valueToTree(cliente.messages().create(params));
            var tokens = respuesta.path("usage");
            uso = new UsoModelo(propiedades.modelo(), tokens.path("input_tokens").asLong(), tokens.path("output_tokens").asLong(),
                    tokens.path("cache_read_input_tokens").asLong(), tokens.path("cache_creation_input_tokens").asLong(),
                    (System.nanoTime() - inicio) / 1_000_000);
            String parada = respuesta.path("stop_reason").asText("");
            if ("refusal".equals(parada)) throw fallo(MotivoFalloModelo.RECHAZO, uso);
            if ("max_tokens".equals(parada)) throw fallo(MotivoFalloModelo.SALIDA_INVALIDA, uso);
            if (!(respuesta.path("content") instanceof ArrayNode contenido)) throw fallo(MotivoFalloModelo.SALIDA_INVALIDA, uso);
            var llamadas = new ArrayList<LlamadaHerramienta>();
            var ids = new HashSet<String>();
            var texto = new StringBuilder();
            for (var bloque : contenido) {
                switch (bloque.path("type").asText()) {
                    case "text" -> texto.append(bloque.path("text").asText());
                    case "refusal" -> throw fallo(MotivoFalloModelo.RECHAZO, uso);
                    case "tool_use" -> {
                        String id = bloque.path("id").asText("");
                        if (id.isBlank() || !ids.add(id) || !bloque.path("input").isObject())
                            throw fallo(MotivoFalloModelo.SALIDA_INVALIDA, uso);
                        llamadas.add(new LlamadaHerramienta(id, HerramientaIA.porNombre(bloque.path("name").asText()),
                                json.writeValueAsString(bloque.get("input"))));
                    }
                    case "thinking", "redacted_thinking" -> { /* Solo la continuación opaca los conserva. */ }
                    default -> throw fallo(MotivoFalloModelo.SALIDA_INVALIDA, uso);
                }
            }
            if (!("end_turn".equals(parada) && llamadas.isEmpty()) && !("tool_use".equals(parada) && !llamadas.isEmpty()))
                throw fallo(MotivoFalloModelo.SALIDA_INVALIDA, uso);
            return new RespuestaModelo(texto.isEmpty() ? Optional.empty() : Optional.of(texto.toString()), llamadas,
                    new ContinuacionAnthropic(contenido.deepCopy()), uso, parada);
        } catch (FalloModelo fallo) {
            throw fallo;
        } catch (AnthropicServiceException fallo) {
            throw fallo(fallo.statusCode() == 429 ? MotivoFalloModelo.LIMITE_PROVEEDOR : MotivoFalloModelo.PROVEEDOR, uso);
        } catch (Exception fallo) {
            boolean tiempo = false;
            for (Throwable causa = fallo; causa != null; causa = causa.getCause())
                if (causa instanceof java.net.SocketTimeoutException || causa instanceof java.net.http.HttpTimeoutException
                        || causa instanceof java.io.InterruptedIOException) tiempo = true;
            throw fallo(tiempo ? MotivoFalloModelo.TIEMPO_AGOTADO : MotivoFalloModelo.SALIDA_INVALIDA, uso);
        }
    }

    private static FalloModelo fallo(MotivoFalloModelo motivo, UsoModelo uso) {
        return new FalloModelo(motivo, Optional.ofNullable(uso), null);
    }

    // No es un record: toString nunca revela el pensamiento ni el cuerpo del proveedor.
    private static final class ContinuacionAnthropic implements ContinuacionProveedor {
        private final ArrayNode bloques;
        private ContinuacionAnthropic(ArrayNode bloques) { this.bloques = bloques; }
    }
}
