package pe.barberturno.whatsapp;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook de WhatsApp Cloud (11-v2-08-whatsapp-entrada.md §4.1). Ruta global y pública, sin sesión ni CSRF:
 * su autenticación es la firma {@code X-Hub-Signature-256} sobre el cuerpo crudo (V2-DA-15), comprobada
 * antes de interpretar nada. Las respuestas de error no llevan detalle.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WebhookWhatsAppController {
    /** Tamaño máximo admitido del cuerpo de una notificación: 1 MiB. */
    static final int LIMITE_BYTES = 1024 * 1024;
    private static final Pattern DESAFIO = Pattern.compile("[A-Za-z0-9._-]{1,256}");
    private final FirmaWebhook firma;
    private final RecepcionWebhookService recepcion;

    /**
     * Recibe la autenticación propia del webhook y el servicio que guarda en la cola.
     * @param firma comprobación del token de verificación y de la firma
     * @param recepcion inserción idempotente de los eventos
     */
    public WebhookWhatsAppController(FirmaWebhook firma, RecepcionWebhookService recepcion) {
        this.firma = firma;
        this.recepcion = recepcion;
    }

    /**
     * Verificación de la suscripción: devuelve el desafío solo si el modo y el token son los esperados.
     * @param modo {@code hub.mode}; debe ser {@code subscribe}
     * @param token {@code hub.verify_token}, comparado en tiempo constante con la configuración
     * @param desafio {@code hub.challenge}, que se devuelve tal cual (solo caracteres alfanuméricos, punto, guion y guion bajo)
     * @return 200 {@code text/plain} con el desafío, o 403 sin cuerpo
     */
    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode", required = false) String modo,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String desafio) {
        if (!"subscribe".equals(modo) || desafio == null || !DESAFIO.matcher(desafio).matches() || !firma.tokenValido(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(desafio);
    }

    /**
     * Notificación de Meta: limita el tamaño, comprueba la firma sobre los bytes exactos y guarda los eventos.
     * @param peticion petición con el cuerpo crudo
     * @param cabecera {@code X-Hub-Signature-256}, o {@code null}
     * @return 200 sin cuerpo si la firma es válida (aunque no haya eventos nuevos); 413 si el cuerpo supera
     *         1 MiB; 401 si falta la firma, es errónea o no hay secreto configurado; en ningún error se guarda nada
     * @throws IOException si falla la lectura del cuerpo
     * @throws pe.barberturno.common.error.NegocioException VALIDACION (400) si el cuerpo firmado no es un objeto JSON
     */
    @PostMapping
    public ResponseEntity<Void> recibir(HttpServletRequest peticion,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String cabecera) throws IOException {
        if (peticion.getContentLengthLong() > LIMITE_BYTES) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        byte[] cuerpo = peticion.getInputStream().readNBytes(LIMITE_BYTES + 1);
        if (cuerpo.length > LIMITE_BYTES) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        if (!firma.firmaValida(cuerpo, cabecera)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        recepcion.recibir(cuerpo);
        return ResponseEntity.ok().build();
    }
}
