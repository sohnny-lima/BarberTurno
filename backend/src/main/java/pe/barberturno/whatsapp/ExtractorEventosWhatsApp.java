package pe.barberturno.whatsapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Convierte una notificación de WhatsApp Cloud ya autenticada en los eventos de la cola
 * (11-v2-08-whatsapp-entrada.md §4.2): uno por mensaje ({@code "m:" + id}) y uno por aviso de estado
 * ({@code "s:" + id + ":" + status}). No resuelve el negocio (V2-DA-52) ni interpreta el texto; lo que no
 * tiene ese formato se ignora con un registro sin datos personales.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class ExtractorEventosWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(ExtractorEventosWhatsApp.class);
    private static final Pattern NUMERO = Pattern.compile("[0-9]{1,32}");
    private static final int CLAVE_MAXIMA = 160;
    private final JsonMapper mapper;

    /**
     * Usa el mapeador de Jackson del contexto para construir los cuerpos.
     * @param mapper mapeador JSON de la aplicación
     */
    public ExtractorEventosWhatsApp(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Extrae los eventos de una notificación con {@code object = whatsapp_business_account}.
     * @param raiz documento JSON completo de la notificación
     * @return eventos en el orden en que aparecen; vacío si la notificación no contiene mensajes ni estados
     */
    public List<EventoNuevo> extraer(JsonNode raiz) {
        List<EventoNuevo> eventos = new ArrayList<>();
        if (!"whatsapp_business_account".equals(texto(raiz.get("object")))) {
            LOG.info("Notificación de WhatsApp ignorada: objeto no admitido.");
            return eventos;
        }
        int ignorados = 0;
        for (JsonNode entrada : elementos(raiz.get("entry"))) {
            for (JsonNode cambio : elementos(entrada.get("changes"))) {
                JsonNode valor = cambio.get("value");
                String numero = valor == null ? null : texto(valor.path("metadata").get("phone_number_id"));
                if (!"messages".equals(texto(cambio.get("field"))) || valor == null
                        || !"whatsapp".equals(texto(valor.get("messaging_product"))) || numero == null
                        || !NUMERO.matcher(numero).matches()) {
                    ignorados++;
                    continue;
                }
                Map<String, String> nombres = new HashMap<>();
                for (JsonNode contacto : elementos(valor.get("contacts"))) {
                    String waId = texto(contacto.get("wa_id"));
                    if (waId != null) nombres.put(waId, texto(contacto.path("profile").get("name")));
                }
                for (JsonNode mensaje : elementos(valor.get("messages"))) {
                    String id = texto(mensaje.get("id"));
                    String de = texto(mensaje.get("from"));
                    ignorados += agregar(eventos, id == null ? null : "m:" + id, TipoEvento.MENSAJE, numero,
                            de != null && nombres.containsKey(de) ? contacto(de, nombres.get(de)) : null, mensaje);
                }
                for (JsonNode estado : elementos(valor.get("statuses"))) {
                    String id = texto(estado.get("id"));
                    String nombre = texto(estado.get("status"));
                    ignorados += agregar(eventos, id == null || nombre == null ? null : "s:" + id + ":" + nombre,
                            TipoEvento.ESTADO, numero, null, estado);
                }
            }
        }
        if (ignorados > 0) LOG.info("Notificación de WhatsApp: {} elementos ignorados por formato no admitido.", ignorados);
        return eventos;
    }

    private int agregar(List<EventoNuevo> eventos, String clave, TipoEvento tipo, String numero,
            ObjectNode contacto, JsonNode dato) {
        if (clave == null || clave.length() > CLAVE_MAXIMA) return 1;
        ObjectNode cuerpo = mapper.createObjectNode();
        cuerpo.put("phoneNumberId", numero);
        if (contacto == null) cuerpo.putNull("contacto");
        else cuerpo.set("contacto", contacto);
        cuerpo.set("dato", dato);
        eventos.add(new EventoNuevo(clave, tipo, numero, mapper.writeValueAsString(cuerpo)));
        return 0;
    }

    private ObjectNode contacto(String waId, String nombre) {
        ObjectNode contacto = mapper.createObjectNode();
        contacto.put("waId", waId);
        if (nombre == null) contacto.putNull("nombre");
        else contacto.put("nombre", nombre);
        return contacto;
    }

    private static Iterable<JsonNode> elementos(JsonNode nodo) {
        return nodo != null && nodo.isArray() ? nodo : List.of();
    }

    private static String texto(JsonNode nodo) {
        if (nodo == null || !nodo.isString()) return null;
        String valor = nodo.asString();
        return valor.isBlank() ? null : valor;
    }

    /**
     * Evento listo para insertarse en {@code evento_whatsapp_entrante}.
     * @param clave clave de idempotencia ({@code m:…} o {@code s:…:…}), de hasta 160 caracteres
     * @param tipo mensaje o aviso de estado
     * @param phoneNumberId número receptor, solo dígitos
     * @param cuerpo JSON con {@code phoneNumberId}, {@code contacto} y {@code dato}
     */
    public record EventoNuevo(String clave, TipoEvento tipo, String phoneNumberId, String cuerpo) { }
}
