package pe.barberturno.whatsapp.twilio;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import pe.barberturno.whatsapp.ClienteGraphWhatsApp;
import pe.barberturno.whatsapp.MotivoFallo;
import pe.barberturno.whatsapp.ResultadoEnvio;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Transporte del Sandbox con texto numerado y HTTP sin redirecciones ni reintentos ocultos (V2-DA-99).
 * Serializa las solicitudes de esta instancia, esperando al menos intervalo-envio desde el fin de la
 * anterior, incluso entre lotes e hilos. La espera y el HTTP ocurren fuera de transacciones del enviador.
 * No concilia por biz_opaque_callback_data: un resultado desconocido conserva el riesgo de duplicados.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Primary
@Profile("twilio")
public class ClienteTwilioWhatsApp implements ClienteGraphWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(ClienteTwilioWhatsApp.class);
    private final TwilioPropiedades propiedades;
    private final CorrespondenciaMenusTwilio menus;
    private final JsonMapper mapper;
    private final RestClient cliente;
    private boolean huboEnvio;
    private long finAnterior;

    /**
     * Construye un cliente HTTP con tiempos finitos, sin registrar URL, cabeceras ni contenido.
     * @param propiedades credenciales, origen y callbacks del Sandbox
     * @param menus correspondencias compartidas con la entrada
     * @param mapper lectura de las respuestas del proveedor
     */
    public ClienteTwilioWhatsApp(TwilioPropiedades propiedades, CorrespondenciaMenusTwilio menus, JsonMapper mapper) {
        this.propiedades = propiedades; this.menus = menus; this.mapper = mapper;
        var transporte = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var fabrica = new JdkClientHttpRequestFactory(transporte);
        fabrica.setReadTimeout(Duration.ofSeconds(15));
        this.cliente = RestClient.builder().baseUrl(propiedades.apiBase()).requestFactory(fabrica)
                .defaultHeaders(cabeceras -> cabeceras.setBasicAuth(propiedades.accountSid(), propiedades.authToken()))
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public boolean configurado() { return propiedades.configurado(); }

    /**
     * Convierte el cuerpo de Meta y realiza como máximo un POST, respetando el intervalo del Sandbox.
     * Un interactivo aceptado o incierto sustituye el menú; un rechazo conserva el anterior.
     * @param phoneNumberId dígitos del número del Sandbox registrado en el canal
     * @param cuerpo mensaje de la outbox en formato de Graph, con destinatario wa_id
     * @return aceptación por sid, rechazo clasificado o resultado desconocido ante incertidumbre de red
     */
    @Override
    public synchronized ResultadoEnvio enviar(String phoneNumberId, ObjectNode cuerpo) {
        if (!configurado()) return permanente(MotivoFallo.CREDENCIAL_RECHAZADA);
        if (!propiedades.numero().substring(1).equals(phoneNumberId)) return permanente(MotivoFallo.SOLICITUD_RECHAZADA);
        if ("template".equals(cuerpo.path("type").asString(""))) return permanente(MotivoFallo.PLANTILLA_RECHAZADA);
        String waId = cuerpo.path("to").asString("");
        Mensaje mensaje;
        try { mensaje = convertir(cuerpo); }
        catch (IllegalArgumentException mal) { return permanente(MotivoFallo.SOLICITUD_RECHAZADA); }
        if (!waId.matches("[1-9][0-9]{1,14}") || mensaje.texto().isBlank() || mensaje.texto().length() > 1600) {
            return permanente(MotivoFallo.SOLICITUD_RECHAZADA);
        }
        try { esperarIntervalo(); }
        catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            return new ResultadoEnvio.NoEnviado();
        }
        var formulario = new LinkedMultiValueMap<String, String>();
        formulario.add("From", "whatsapp:" + propiedades.numero());
        formulario.add("To", "whatsapp:+" + waId);
        formulario.add("Body", mensaje.texto());
        formulario.add("StatusCallback", propiedades.urlPublica() + "/api/twilio/estado");
        ResultadoEnvio resultado;
        try {
            resultado = cliente.post().uri("/2010-04-01/Accounts/{cuenta}/Messages.json", propiedades.accountSid())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario)
                    .exchange((peticion, respuesta) -> {
                        int http = respuesta.getStatusCode().value();
                        JsonNode dato;
                        try { dato = mapper.readTree(respuesta.getBody()); }
                        catch (RuntimeException | java.io.IOException ilegible) {
                            return http == 201 ? new ResultadoEnvio.Desconocido() : clasificar(http, OptionalInt.empty());
                        }
                        if (http == 201) {
                            JsonNode sid = dato == null ? null : dato.path("sid");
                            return sid != null && sid.isString() && !sid.asString().isBlank() && sid.asString().length() <= 128
                                    ? new ResultadoEnvio.Aceptado(sid.asString()) : new ResultadoEnvio.Desconocido();
                        }
                        JsonNode codigo = dato == null ? null : dato.path("code");
                        return clasificar(http, codigo != null && codigo.isIntegralNumber() && codigo.canConvertToInt()
                                ? OptionalInt.of(codigo.asInt()) : OptionalInt.empty());
                    });
        } catch (RuntimeException fallo) {
            // Las excepciones HTTP pueden incluir URL, cuenta, destinatario y contenido: nunca registrarlas.
            resultado = new ResultadoEnvio.Desconocido();
        } finally {
            finAnterior = System.nanoTime();
            huboEnvio = true;
        }
        if (!mensaje.opciones().isEmpty()
                && (resultado instanceof ResultadoEnvio.Aceptado || resultado instanceof ResultadoEnvio.Desconocido)) {
            menus.guardar(waId, mensaje.opciones());
        }
        LOG.info("Twilio Sandbox: resultado {}.", resultado.getClass().getSimpleName());
        return resultado;
    }

    private void esperarIntervalo() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!huboEnvio) return;
        long restante;
        while ((restante = propiedades.intervaloEnvio().toNanos() - (System.nanoTime() - finAnterior)) > 0) {
            TimeUnit.NANOSECONDS.sleep(restante);
        }
    }

    private Mensaje convertir(ObjectNode cuerpo) {
        if ("text".equals(cuerpo.path("type").asString(""))) {
            return new Mensaje(cuerpo.path("text").path("body").asString(""), List.of());
        }
        if (!"interactive".equals(cuerpo.path("type").asString(""))) throw new IllegalArgumentException();
        JsonNode interactivo = cuerpo.path("interactive");
        String tipo = interactivo.path("type").asString("");
        List<CorrespondenciaMenusTwilio.Seleccion> opciones = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        agregarLinea(texto, interactivo.path("header").path("text").asString(""));
        agregarLinea(texto, interactivo.path("body").path("text").asString(""));
        if (!texto.isEmpty()) texto.append('\n');
        switch (tipo) {
            case "button" -> {
                for (JsonNode boton : interactivo.path("action").path("buttons")) {
                    agregarOpcion(texto, opciones, "button_reply", boton.path("reply"), false);
                }
            }
            case "list" -> {
                for (JsonNode seccion : interactivo.path("action").path("sections")) {
                    for (JsonNode fila : seccion.path("rows")) agregarOpcion(texto, opciones, "list_reply", fila, true);
                }
            }
            default -> throw new IllegalArgumentException();
        }
        if (opciones.isEmpty()) throw new IllegalArgumentException();
        texto.append("Responda con el número.");
        return new Mensaje(texto.toString(), List.copyOf(opciones));
    }

    private static void agregarLinea(StringBuilder texto, String linea) {
        if (!linea.isEmpty()) texto.append(linea).append('\n');
    }

    private static void agregarOpcion(StringBuilder texto, List<CorrespondenciaMenusTwilio.Seleccion> opciones,
            String tipo, JsonNode nodo, boolean descripcion) {
        String id = nodo.path("id").asString("");
        String titulo = nodo.path("title").asString("");
        if (id.isBlank() || titulo.isBlank()) throw new IllegalArgumentException();
        opciones.add(new CorrespondenciaMenusTwilio.Seleccion(tipo, id, titulo));
        texto.append(opciones.size()).append(". ").append(titulo);
        String detalle = nodo.path("description").asString("");
        if (descripcion && !detalle.isEmpty()) texto.append(" — ").append(detalle);
        texto.append('\n');
    }

    private static ResultadoEnvio clasificar(int http, OptionalInt codigo) {
        if (http >= 200 && http < 300) return new ResultadoEnvio.Desconocido();
        boolean transitorio = http == 429 || http >= 500;
        return new ResultadoEnvio.Rechazado(http, codigo, transitorio,
                transitorio ? null : MotivoFallo.SOLICITUD_RECHAZADA);
    }

    private static ResultadoEnvio permanente(MotivoFallo motivo) {
        LOG.warn("Twilio Sandbox: rechazo local {}.", motivo);
        return new ResultadoEnvio.Rechazado(400, OptionalInt.empty(), false, motivo);
    }

    private record Mensaje(String texto, List<CorrespondenciaMenusTwilio.Seleccion> opciones) { }
}
