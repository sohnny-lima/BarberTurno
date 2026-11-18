package pe.barberturno.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reduce la capacidad de vinculación a SHA-256 antes de persistir la entrada, también con Meta.
 * El texto original del código no llega a la cola durable ni al historial de la conversación.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public final class CodigoVinculoWhatsApp {
    private CodigoVinculoWhatsApp() { }

    /**
     * Calcula la representación irreversible de un código, sin guiones ni espacios.
     * @param codigo capacidad recibida o generada; se normaliza a mayúsculas
     * @return resumen hexadecimal de sesenta y cuatro caracteres
     */
    public static String hash(String codigo) {
        String normalizado = codigo.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 no está disponible.");
        }
    }

    /**
     * Sustituye únicamente el argumento de «vincular» antes de insertar el evento.
     * @param dato mensaje de Meta extraído de una notificación autenticada
     */
    public static void proteger(ObjectNode dato) {
        // El marcador interno nunca se toma del sobre externo sin recalcularlo.
        dato.remove("bt_codigo_vinculo_hash");
        if (!"text".equals(dato.path("type").asString(""))) return;
        String cuerpo = dato.path("text").path("body").asString("").strip();
        if (!cuerpo.regionMatches(true, 0, "vincular", 0, 8)) return;
        String argumento = cuerpo.length() > 8 ? cuerpo.substring(8).strip() : "";
        dato.put("bt_codigo_vinculo_hash", hash(argumento));
        dato.putObject("text").put("body", "vincular");
    }
}
