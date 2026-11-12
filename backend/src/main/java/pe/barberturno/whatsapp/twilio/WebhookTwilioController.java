package pe.barberturno.whatsapp.twilio;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pe.barberturno.whatsapp.RecepcionWebhookService;

/**
 * Dos callbacks exactos del Sandbox: limita a 1 MiB, autentica y entrega a la cola existente.
 * Nunca confía en Host ni en cabeceras de proxy para construir la URL firmada.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@Profile("twilio")
public class WebhookTwilioController {
    private static final int LIMITE_BYTES = 1024 * 1024;
    private final FirmaTwilio firma;
    private final TraductorEntradaTwilio traductor;
    private final RecepcionWebhookService recepcion;

    /**
     * Recibe autenticación y traducción sin acceso directo a las tablas de negocio.
     * @param firma comprobación de HMAC y configuración
     * @param traductor conversión al contrato de entrada de Meta
     * @param recepcion inserción transaccional idempotente
     */
    public WebhookTwilioController(FirmaTwilio firma, TraductorEntradaTwilio traductor,
            RecepcionWebhookService recepcion) {
        this.firma = firma; this.traductor = traductor; this.recepcion = recepcion;
    }

    /**
     * Recibe mensajes sin responder desde el webhook: la respuesta real sale por la outbox.
     * @param peticion formulario crudo, leído con límite antes de decodificarlo
     * @param cabecera firma de Twilio o null
     * @return 200 text/xml con Response vacío; 401 sin firma válida; 413 por exceso de tamaño
     * @throws IOException si falla la lectura del cuerpo HTTP
     */
    @PostMapping(path = "/api/twilio/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> recibir(HttpServletRequest peticion,
            @RequestHeader(name = "X-Twilio-Signature", required = false) String cabecera) throws IOException {
        return procesar(peticion, cabecera, "/api/twilio/webhook", false);
    }

    /**
     * Recibe avisos firmados de entrega y lectura, ignorando queued.
     * @param peticion formulario crudo del estado
     * @param cabecera firma de Twilio o null
     * @return 200 text/xml vacío, 401 si no se autentica o 413 si excede 1 MiB
     * @throws IOException si falla la lectura del cuerpo HTTP
     */
    @PostMapping(path = "/api/twilio/estado", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> estado(HttpServletRequest peticion,
            @RequestHeader(name = "X-Twilio-Signature", required = false) String cabecera) throws IOException {
        return procesar(peticion, cabecera, "/api/twilio/estado", true);
    }

    private ResponseEntity<String> procesar(HttpServletRequest peticion, String cabecera, String ruta,
            boolean estado) throws IOException {
        if (peticion.getContentLengthLong() > LIMITE_BYTES) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        byte[] crudo = peticion.getInputStream().readNBytes(LIMITE_BYTES + 1);
        if (crudo.length > LIMITE_BYTES) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        Map<String, String> datos;
        try { datos = formulario(crudo); }
        catch (IllegalArgumentException ambiguo) { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); }
        if (peticion.getQueryString() != null || !firma.valida(ruta, datos, cabecera)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var cuerpo = estado ? traductor.estado(datos) : traductor.mensaje(datos);
        cuerpo.ifPresent(recepcion::recibir);
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body("<Response/>");
    }

    private static Map<String, String> formulario(byte[] crudo) {
        Map<String, String> datos = new LinkedHashMap<>();
        String texto = new String(crudo, StandardCharsets.UTF_8);
        if (texto.isEmpty()) return datos;
        for (String par : texto.split("&", -1)) {
            String[] partes = par.split("=", 2);
            String nombre = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length == 2 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            if (nombre.isEmpty() || datos.putIfAbsent(nombre, valor) != null) {
                throw new IllegalArgumentException("Formulario de Twilio ambiguo.");
            }
        }
        return datos;
    }
}
