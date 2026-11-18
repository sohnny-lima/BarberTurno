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
 * Prefiere teléfonos E.164 y adapta BSUID mediante identidades deterministas (V2-DA-103).
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
    private final CorrespondenciaDireccionesTwilio direcciones;

    /**
     * Comparte las correspondencias con el cliente de salida.
     * @param mapper serialización JSON de la aplicación
     * @param reloj fecha de recepción para los eventos traducidos
     * @param menus último menú por persona
     * @param propiedades número del Sandbox
     * @param direcciones traducción determinista de BSUID y destinos compartidos con la salida
     */
    public TraductorEntradaTwilio(JsonMapper mapper, Clock reloj, CorrespondenciaMenusTwilio menus,
            TwilioPropiedades propiedades, CorrespondenciaDireccionesTwilio direcciones) {
        this.mapper = mapper; this.reloj = reloj; this.menus = menus; this.propiedades = propiedades;
        this.direcciones = direcciones;
    }

    /**
     * Convierte una entrada con WaId E.164 o BSUID admitido; sin identidad no genera eventos.
     * Los destinos ajenos y remitentes de otro canal se ignoran antes de traducir el contenido.
     * @param datos formulario firmado con todos sus valores decodificados
     * @return JSON de Meta, o vacío si la notificación debe ignorarse
     */
    public Optional<byte[]> mensaje(Map<String, String> datos) {
        String id = datos.getOrDefault("MessageSid", "");
        if (numeroEntrada(datos).isEmpty()
                || !datos.getOrDefault("From", "").startsWith("whatsapp:") || id.isBlank()) return Optional.empty();
        var identidad = identidad(datos);
        if (identidad.isEmpty()) return Optional.empty();
        String waId = identidad.get();
        ObjectNode raiz = sobre(numeroEntrada(datos).orElseThrow());
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
            var seleccion = menus.consumir(numeroEntrada(datos).orElseThrow(), waId, cuerpo, id);
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
     * Convierte estados por sid, identificando el To E.164 o BSUID con la misma regla de entrada.
     * From determina el canal; sin él solo se admite la configuración histórica de un canal.
     * @param datos formulario de estado firmado; To es ahora el destinatario del mensaje saliente
     * @return JSON de Meta, o vacío para queued, estados no admitidos o un canal ambiguo
     */
    public Optional<byte[]> estado(Map<String, String> datos) {
        String estado = switch (datos.getOrDefault("MessageStatus", "")) {
            case "sent", "delivered", "read" -> datos.get("MessageStatus");
            case "failed", "undelivered" -> "failed";
            default -> "";
        };
        String id = datos.getOrDefault("MessageSid", "");
        String para = datos.getOrDefault("To", "");
        if (estado.isEmpty() || id.isBlank()) return Optional.empty();
        String telefono = para.startsWith("whatsapp:+") ? para.substring("whatsapp:+".length()) : "";
        var identidad = direcciones.identificar(telefono, para);
        if (identidad.isEmpty()) return Optional.empty();
        var canal = propiedades.canalesEfectivos().stream()
                .filter(c -> ("whatsapp:" + c.numero()).equals(datos.getOrDefault("From", "")))
                .map(c -> c.numero().substring(1)).findFirst();
        if (canal.isEmpty() && propiedades.canalesEfectivos().size() == 1
                && datos.getOrDefault("From", "").isBlank()) {
            canal = Optional.of(propiedades.canalesEfectivos().getFirst().numero().substring(1));
        }
        if (canal.isEmpty()) return Optional.empty();
        ObjectNode raiz = sobre(canal.get());
        ObjectNode aviso = valor(raiz).putArray("statuses").addObject().put("id", id).put("status", estado)
                .put("timestamp", Long.toString(reloj.instant().getEpochSecond()))
                .put("recipient_id", identidad.get());
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

    /**
     * Resuelve la identidad que compartirá la espera del webhook con el mensaje traducido.
     * @param datos formulario autenticado con WaId opcional y From
     * @return teléfono o sintético válido, o vacío para un remitente que deba ignorarse
     */
    public Optional<String> identidad(Map<String, String> datos) {
        return direcciones.identificar(datos.getOrDefault("WaId", ""), datos.getOrDefault("From", ""));
    }

    /**
     * Identifica únicamente un destino configurado antes de consultar menús o abrir esperas.
     * @param datos formulario firmado del webhook de entrada
     * @return número del canal en dígitos, o vacío para destinos ajenos
     */
    public Optional<String> numeroEntrada(Map<String, String> datos) {
        return propiedades.canalesEfectivos().stream()
                .filter(c -> ("whatsapp:" + c.numero()).equals(datos.getOrDefault("To", "")))
                .map(c -> c.numero().substring(1)).findFirst();
    }

    /**
     * Valida la opción numérica local sin pasar un error de rango a la conversación.
     * @param datos formulario firmado, con identidad y destino ya comprobados
     * @return aviso idempotente del menú actual, si la opción no existe
     */
    public Optional<String> respuestaInvalida(Map<String, String> datos) {
        if (!propiedades.respuestaTwiml() || datos.getOrDefault("MessageSid", "").isBlank()
                || !datos.getOrDefault("From", "").startsWith("whatsapp:")
                || !datos.getOrDefault("ButtonPayload", "").isEmpty()) return Optional.empty();
        return numeroEntrada(datos).flatMap(numero -> identidad(datos).flatMap(wa ->
                menus.respuestaInvalida(numero, wa, datos.getOrDefault("Body", ""), datos.get("MessageSid"))));
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
