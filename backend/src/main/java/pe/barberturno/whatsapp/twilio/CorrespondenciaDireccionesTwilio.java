package pe.barberturno.whatsapp.twilio;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Traduce BSUID a identidades de veinte dígitos y recuerda su última dirección (V2-DA-103).
 * La correspondencia solo vive en esta instancia, con un máximo de 10 000 personas; nunca se registra.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class CorrespondenciaDireccionesTwilio {
    private static final int LIMITE = 10_000;
    private static final String PREFIJO = "whatsapp:";
    private static final BigInteger MODULO = BigInteger.TEN.pow(19);
    private final Map<String, String> direcciones = new LinkedHashMap<>();

    /** Crea una correspondencia vacía sin persistir ni registrar identidades. */
    public CorrespondenciaDireccionesTwilio() { }

    /**
     * Prefiere el WaId E.164; sin él, calcula el sintético del BSUID y recuerda su dirección.
     * @param waId identidad numérica recibida, o vacío cuando Twilio no comunica el teléfono
     * @param direccion dirección completa From o To del formulario ya autenticado
     * @return identidad numérica, o vacío si ni el teléfono ni la dirección cumplen el contrato
     * @throws IllegalStateException si el JDK no ofrece el algoritmo obligatorio SHA-256
     */
    public Optional<String> identificar(String waId, String direccion) {
        if (waId != null && waId.matches("[1-9][0-9]{1,14}")) return Optional.of(waId);
        if (direccion == null || !direccion.matches("whatsapp:[A-Z]{2}\\.[A-Za-z0-9]{1,128}")) {
            return Optional.empty();
        }
        byte[] resumen;
        try {
            resumen = MessageDigest.getInstance("SHA-256")
                    .digest(direccion.substring(PREFIJO.length()).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 no está disponible.");
        }
        String numero = new BigInteger(1, Arrays.copyOf(resumen, 8)).mod(MODULO).toString();
        String sintetico = "9" + "0".repeat(19 - numero.length()) + numero;
        synchronized (direcciones) {
            direcciones.remove(sintetico);
            direcciones.put(sintetico, direccion);
            if (direcciones.size() > LIMITE) direcciones.remove(direcciones.keySet().iterator().next());
        }
        return Optional.of(sintetico);
    }

    /**
     * Recupera una dirección REST, sin interpretar un sintético desconocido como teléfono.
     * @param waId teléfono E.164 sin signo o identidad sintética de veinte dígitos
     * @return dirección completa de Twilio, o vacío si no se conoce una dirección válida
     */
    public Optional<String> destino(String waId) {
        if (waId != null && waId.matches("[1-9][0-9]{1,14}")) return Optional.of("whatsapp:+" + waId);
        synchronized (direcciones) {
            return Optional.ofNullable(direcciones.get(waId));
        }
    }
}
