package pe.barberturno.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Autenticación propia del webhook de Meta (V2-DA-15): HMAC-SHA256 del cuerpo crudo con el
 * <em>app secret</em> y comparación en tiempo constante con {@code X-Hub-Signature-256}; el token de
 * verificación también se compara en tiempo constante. Sin secretos configurados, nada es válido (V2-DA-55).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class FirmaWebhook {
    private static final String PREFIJO = "sha256=";
    private final WhatsAppPropiedades propiedades;

    /**
     * Toma los secretos de la configuración del entorno.
     * @param propiedades configuración de WhatsApp con el token y el secreto, posiblemente vacíos
     */
    public FirmaWebhook(WhatsAppPropiedades propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Comprueba la firma de una notificación antes de interpretar su contenido.
     * @param cuerpo bytes exactos recibidos, sin decodificar ni normalizar
     * @param cabecera valor de {@code X-Hub-Signature-256} ({@code sha256=} seguido de 64 hexadecimales), o {@code null}
     * @return {@code true} solo si hay secreto configurado y la firma coincide con la del cuerpo
     */
    public boolean firmaValida(byte[] cuerpo, String cabecera) {
        if (!propiedades.firmaConfigurada() || cabecera == null || !cabecera.startsWith(PREFIJO)
                || cabecera.length() != PREFIJO.length() + 64) {
            return false;
        }
        byte[] recibida;
        try {
            recibida = HexFormat.of().parseHex(cabecera.substring(PREFIJO.length()));
        } catch (IllegalArgumentException mal) {
            return false;
        }
        return MessageDigest.isEqual(recibida, firmar(cuerpo));
    }

    /**
     * Comprueba el token de la verificación de suscripción ({@code hub.verify_token}).
     * @param token token recibido, o {@code null}
     * @return {@code true} solo si hay token configurado y coincide exactamente
     */
    public boolean tokenValido(String token) {
        if (!propiedades.verificacionConfigurada() || token == null) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                propiedades.verifyToken().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] firmar(byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(propiedades.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(cuerpo);
        } catch (GeneralSecurityException imposible) {
            throw new IllegalStateException("HmacSHA256 no disponible en la JVM.", imposible);
        }
    }
}
