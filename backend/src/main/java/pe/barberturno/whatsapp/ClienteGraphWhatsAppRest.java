package pe.barberturno.whatsapp;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.util.OptionalInt;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adaptador Graph sin redirecciones, reintentos ocultos ni registros HTTP. Solo un fallo probado al conectar
 * permite devolver NoEnviado; toda respuesta de éxito inutilizable o duda de E/S es Desconocido.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class ClienteGraphWhatsAppRest implements ClienteGraphWhatsApp {
    private final RestClient cliente;
    private final String version;
    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * Construye el transporte con los tiempos validados del contrato y sin seguir redirecciones.
     * @param propiedades configuración de Graph, incluido el token que nunca se registra
     */
    public ClienteGraphWhatsAppRest(SalidaWhatsAppPropiedades propiedades) {
        var graph = propiedades.graph();
        var transporte = HttpClient.newBuilder().connectTimeout(graph.conexion())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var fabrica = new JdkClientHttpRequestFactory(transporte);
        fabrica.setReadTimeout(graph.respuesta());
        this.cliente = RestClient.builder().requestFactory(fabrica).baseUrl(graph.urlBase())
                .defaultHeaders(cabeceras -> cabeceras.setBearerAuth(graph.token())).build();
        this.version = graph.version();
    }

    /** {@inheritDoc} */
    @Override
    public ResultadoEnvio enviar(String phoneNumberId, ObjectNode cuerpo) {
        try {
            return cliente.post().uri("/{version}/{numero}/messages", version, phoneNumberId)
                    .contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsBytes(cuerpo))
                    .exchange((peticion, respuesta) -> {
                        int http = respuesta.getStatusCode().value();
                        JsonNode dato;
                        try {
                            dato = mapper.readTree(respuesta.getBody());
                        } catch (RuntimeException | java.io.IOException ilegible) {
                            return http >= 200 && http < 300 ? new ResultadoEnvio.Desconocido()
                                    : ClasificadorErroresGraph.clasificar(http, OptionalInt.empty());
                        }
                        if (http >= 200 && http < 300) {
                            JsonNode id = dato == null ? null : dato.path("messages").path(0).path("id");
                            if (id == null || !id.isString() || id.asString().isBlank() || id.asString().length() > 128) {
                                return new ResultadoEnvio.Desconocido();
                            }
                            return new ResultadoEnvio.Aceptado(id.asString());
                        }
                        JsonNode codigo = dato == null ? null : dato.path("error").path("code");
                        return ClasificadorErroresGraph.clasificar(http,
                                codigo != null && codigo.isIntegralNumber() && codigo.canConvertToInt()
                                        ? OptionalInt.of(codigo.asInt()) : OptionalInt.empty());
                    });
        } catch (RuntimeException fallo) {
            for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
                if (causa instanceof HttpConnectTimeoutException || causa instanceof ConnectException
                        || causa instanceof UnknownHostException) return new ResultadoEnvio.NoEnviado();
            }
            return new ResultadoEnvio.Desconocido();
        }
    }
}
