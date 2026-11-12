package pe.barberturno.whatsapp.twilio;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Adapta formularios ya autenticados al JSON que recibe la cola de Meta (V2-DA-98).
 * No resuelve negocios, fichas ni reservas y no modifica la conversación.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class TraductorEntradaTwilio {
    private final JsonMapper mapper;
    private final Clock reloj;
    private final CorrespondenciaMenusTwilio menus;
    private final TwilioPropiedades propiedades;

    /**
     * Comparte las correspondencias con el cliente de salida.
     * @param mapper serialización JSON de la aplicación
     * @param reloj fecha de recepción para los eventos traducidos
     * @param menus último menú por persona
     * @param propiedades número del Sandbox
     */
    public TraductorEntradaTwilio(JsonMapper mapper, Clock reloj, CorrespondenciaMenusTwilio menus,
            TwilioPropiedades propiedades) {
        this.mapper = mapper; this.reloj = reloj; this.menus = menus; this.propiedades = propiedades;
    }

    /**
     * Convierte una entrada válida; los destinos ajenos y remitentes de otro canal no generan eventos.
     * @param datos formulario firmado con todos sus valores decodificados
     * @return JSON de Meta, o vacío si la notificación debe ignorarse
     */
    public Optional<byte[]> mensaje(Map<String, String> datos) {
        String id = datos.getOrDefault("MessageSid", "");
        if (!datos.getOrDefault("To", "").equals("whatsapp:" + propiedades.numero())
                || !datos.getOrDefault("From", "").startsWith("whatsapp:") || id.isBlank()) return Optional.empty();
        String waId = datos.getOrDefault("WaId", "");
        ObjectNode raiz = sobre(propiedades.numero().substring(1));
        ObjectNode valor = valor(raiz);
        valor.putArray("contacts").addObject().put("wa_id", waId).putObject("profile")
                .put("name", datos.getOrDefault("ProfileName", ""));
        ObjectNode mensaje = valor.putArray("messages").addObject().put("id", id).put("from", waId)
                .put("timestamp", Long.toString(reloj.instant().getEpochSecond()));
        String cuerpo = datos.getOrDefault("Body", "");
        String boton = datos.getOrDefault("ButtonPayload", "");
        if (!boton.isEmpty()) {
            interactivo(mensaje, new CorrespondenciaMenusTwilio.Seleccion("button_reply", boton,
                    datos.getOrDefault("ButtonText", "")));
        } else {
            var seleccion = menus.consumir(waId, cuerpo, id);
            if (seleccion.isPresent()) interactivo(mensaje, seleccion.get());
            else if (cuerpo.isBlank() && contieneMedia(datos.getOrDefault("NumMedia", "0"))) {
                mensaje.put("type", "unsupported");
            } else {
                mensaje.put("type", "text").putObject("text").put("body", cuerpo);
            }
        }
        return Optional.of(mapper.writeValueAsBytes(raiz));
    }

    /**
     * Convierte estados de Twilio a los estados que actualizan la outbox por sid.
     * @param datos formulario de estado firmado; To es ahora el destinatario del mensaje saliente
     * @return JSON de Meta, o vacío para queued y estados no admitidos
     */
    public Optional<byte[]> estado(Map<String, String> datos) {
        String estado = switch (datos.getOrDefault("MessageStatus", "")) {
            case "sent", "delivered", "read" -> datos.get("MessageStatus");
            case "failed", "undelivered" -> "failed";
            default -> "";
        };
        String id = datos.getOrDefault("MessageSid", "");
        String para = datos.getOrDefault("To", "");
        if (estado.isEmpty() || id.isBlank() || !para.startsWith("whatsapp:+")) return Optional.empty();
        ObjectNode raiz = sobre(propiedades.numero().substring(1));
        ObjectNode aviso = valor(raiz).putArray("statuses").addObject().put("id", id).put("status", estado)
                .put("timestamp", Long.toString(reloj.instant().getEpochSecond()))
                .put("recipient_id", para.substring("whatsapp:+".length()));
        if (estado.equals("failed")) {
            try {
                int codigo = Integer.parseInt(datos.getOrDefault("ErrorCode", ""));
                aviso.putArray("errors").addObject().put("code", codigo);
            } catch (NumberFormatException sinCodigo) {
                // Un estado sin código sigue acreditando el fallo, sin inventar un código de Meta.
            }
        }
        return Optional.of(mapper.writeValueAsBytes(raiz));
    }

    private ObjectNode sobre(String numero) {
        ObjectNode raiz = mapper.createObjectNode().put("object", "whatsapp_business_account");
        ObjectNode valor = raiz.putArray("entry").addObject().putArray("changes").addObject()
                .put("field", "messages").putObject("value").put("messaging_product", "whatsapp");
        valor.putObject("metadata").put("phone_number_id", numero);
        return raiz;
    }

    private static ObjectNode valor(ObjectNode raiz) {
        return (ObjectNode) raiz.path("entry").path(0).path("changes").path(0).path("value");
    }

    private static void interactivo(ObjectNode mensaje, CorrespondenciaMenusTwilio.Seleccion seleccion) {
        mensaje.put("type", "interactive").putObject("interactive").put("type", seleccion.tipo())
                .putObject(seleccion.tipo()).put("id", seleccion.id()).put("title", seleccion.titulo());
    }

    private static boolean contieneMedia(String numero) {
        try { return Integer.parseInt(numero) > 0; }
        catch (NumberFormatException mal) { return false; }
    }
}
