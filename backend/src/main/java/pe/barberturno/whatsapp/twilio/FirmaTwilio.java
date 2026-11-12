package pe.barberturno.whatsapp.twilio;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Valida HMAC-SHA1 sobre URL exacta y formulario ordenado, sin SDK (V2-DA-101).
 * Algoritmo contrastado con
 * <a href="https://github.com/twilio/twilio-java/blob/main/src/main/java/com/twilio/security/RequestValidator.java">RequestValidator oficial</a>
 * y con el vector de
 * <a href="https://github.com/twilio/twilio-java/blob/main/src/test/java/com/twilio/security/RequestValidatorTest.java">RequestValidatorTest</a>.
 * Se firma exclusivamente la URL pública configurada, sin normalizar puertos ni usar cabeceras del proxy.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class FirmaTwilio {
    private final TwilioPropiedades propiedades;

    /**
     * Conserva el secreto sin representarlo ni registrarlo.
     * @param propiedades configuración externa del Sandbox
     */
    public FirmaTwilio(TwilioPropiedades propiedades) { this.propiedades = propiedades; }

    /**
     * Comprueba la firma antes de acceder al negocio; sin configuración siempre falla cerrado.
     * @param ruta ruta exacta del callback, elegida por el controlador
     * @param formulario parámetros decodificados del cuerpo, sin parámetros de consulta
     * @param firma cabecera X-Twilio-Signature o null
     * @return true solo si coincide el HMAC de la URL pública y de todos los parámetros
     */
    public boolean valida(String ruta, Map<String, String> formulario, String firma) {
        return propiedades.configurado() && comprobar(propiedades.authToken(), propiedades.urlPublica() + ruta,
                formulario, firma);
    }

    /**
     * Ejecuta el algoritmo puro del vector oficial: URL, nombres ordenados y sus valores en UTF-8.
     * La comparación de los veinte bytes del HMAC usa MessageDigest.isEqual.
     * @param token Auth Token, nunca nulo ni vacío para una firma válida
     * @param url URL literal firmada
     * @param parametros formulario ya decodificado
     * @param firma HMAC-SHA1 en Base64 recibido del proveedor
     * @return false ante firma ausente, mal formada, diferente o falta de secreto
     * @throws IllegalStateException si el JDK no ofrece HmacSHA1
     */
    public static boolean comprobar(String token, String url, Map<String, String> parametros, String firma) {
        if (token == null || token.isBlank() || firma == null || firma.length() != 28) return false;
        try {
            byte[] recibida = Base64.getDecoder().decode(firma);
            StringBuilder mensaje = new StringBuilder(url);
            new TreeMap<>(parametros).forEach((nombre, valor) -> mensaje.append(nombre).append(valor == null ? "" : valor));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return MessageDigest.isEqual(mac.doFinal(mensaje.toString().getBytes(StandardCharsets.UTF_8)), recibida);
        } catch (IllegalArgumentException mal) {
            return false;
        } catch (GeneralSecurityException imposible) {
            throw new IllegalStateException("El JDK no permite comprobar la firma de Twilio.");
        }
    }
}
