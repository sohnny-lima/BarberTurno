package pe.barberturno.whatsapp.twilio;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración exclusiva del Sandbox (20-v2-twilio-sandbox.md §4.1). Las credenciales vacías dejan
 * el transporte cerrado; los errores de formato nunca incluyen valores de configuración.
 * @param accountSid identificador de cuenta externo, vacío o AC seguido de 32 dígitos hexadecimales
 * @param authToken secreto usado tanto en HMAC como en autenticación HTTP
 * @param numero emisor del Sandbox en E.164
 * @param urlPublica base HTTPS exacta del callback, sin barra final; vacía deshabilita la recepción
 * @param negocioSlug negocio de prueba al que se registra el canal
 * @param intervaloEnvio separación mínima entre solicitudes, de al menos tres segundos
 * @param apiBase origen HTTPS de Twilio, o HTTP de bucle local para pruebas
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@ConfigurationProperties("barberturno.whatsapp.twilio")
public record TwilioPropiedades(@DefaultValue("") String accountSid, @DefaultValue("") String authToken,
        @DefaultValue("+14155238886") String numero, @DefaultValue("") String urlPublica,
        @DefaultValue("barberturno") String negocioSlug, @DefaultValue("3s") Duration intervaloEnvio,
        @DefaultValue("https://api.twilio.com") String apiBase) {

    /**
     * Rechaza configuraciones ambiguas y evita filtrar secretos en errores de validación.
     * @param accountSid cuenta o vacío si no hay credenciales
     * @param authToken token externo o vacío
     * @param numero emisor E.164, con el valor del Sandbox si se dejó vacío
     * @param urlPublica base HTTPS exacta para la firma
     * @param negocioSlug slug canónico del negocio ficticio
     * @param intervaloEnvio intervalo de al menos tres segundos impuesto por el Sandbox
     * @param apiBase origen del proveedor o del doble HTTP local
     * @throws IllegalArgumentException si un formato o intervalo no cumple el contrato
     */
    public TwilioPropiedades {
        accountSid = accountSid == null ? "" : accountSid.strip();
        authToken = authToken == null ? "" : authToken;
        urlPublica = urlPublica == null ? "" : urlPublica;
        numero = numero == null || numero.isBlank() ? "+14155238886" : numero;
        if (!accountSid.isEmpty() && !accountSid.matches("AC[0-9a-fA-F]{32}")
                || !numero.matches("\\+[1-9][0-9]{1,14}")
                || !urlPublica.isEmpty() && !baseValida(urlPublica, false)
                || negocioSlug == null || negocioSlug.length() > 40 || !negocioSlug.matches("[a-z0-9](-?[a-z0-9])*")
                || intervaloEnvio == null || intervaloEnvio.compareTo(Duration.ofSeconds(3)) < 0
                || !baseValida(apiBase, true)) {
            throw new IllegalArgumentException("Configuración de Twilio Sandbox fuera de rango.");
        }
    }

    private static boolean baseValida(String valor, boolean permitirLocal) {
        if (valor == null || valor.endsWith("/")) return false;
        try {
            URI uri = URI.create(valor);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && ("https".equals(uri.getScheme()) || permitirLocal && "http".equals(uri.getScheme())
                            && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())));
        } catch (IllegalArgumentException mal) {
            return false;
        }
    }

    /**
     * Exige credenciales y la URL que se enviará como StatusCallback.
     * @return true si el adaptador puede autenticar y enviar mensajes
     */
    public boolean configurado() {
        return !accountSid.isBlank() && !authToken.isBlank() && !urlPublica.isBlank();
    }

    /**
     * Protege todas las propiedades ante un registro accidental del bean.
     * @return descripción fija sin cuenta, token, URL ni número
     */
    @Override
    public String toString() { return "TwilioPropiedades[configuración oculta]"; }
}
