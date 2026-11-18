package pe.barberturno.whatsapp.twilio;

import java.net.URI;
import java.time.Duration;
import java.util.List;
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
 * @param respuestaTwiml habilita la respuesta dentro del webhook en vez del envío REST cuando hay espera
 * @param respuestaEspera plazo para el primer mensaje, entre uno y doce segundos
 * @param respuestaSilencio plazo entre mensajes, desde cien milisegundos hasta la espera inicial
 * @param canales pares de número y negocio; vacío conserva la configuración de un solo canal
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@ConfigurationProperties("barberturno.whatsapp.twilio")
public record TwilioPropiedades(@DefaultValue("") String accountSid, @DefaultValue("") String authToken,
        @DefaultValue("+14155238886") String numero, @DefaultValue("") String urlPublica,
        @DefaultValue("barberturno") String negocioSlug, @DefaultValue("3s") Duration intervaloEnvio,
        @DefaultValue("https://api.twilio.com") String apiBase,
        @DefaultValue("true") boolean respuestaTwiml, @DefaultValue("10s") Duration respuestaEspera,
        @DefaultValue("1500ms") Duration respuestaSilencio, List<Canal> canales) {

    /**
     * Rechaza configuraciones ambiguas y evita filtrar secretos en errores de validación.
     * @param accountSid cuenta o vacío si no hay credenciales
     * @param authToken token externo o vacío
     * @param numero emisor E.164, con el valor del Sandbox si se dejó vacío
     * @param urlPublica base HTTPS exacta para la firma
     * @param negocioSlug slug canónico del negocio ficticio
     * @param intervaloEnvio intervalo de al menos tres segundos impuesto por el Sandbox
     * @param apiBase origen del proveedor o del doble HTTP local
     * @param respuestaTwiml true para recoger respuestas de la outbox en el webhook
     * @param respuestaEspera máximo para el primer mensaje desde la apertura
     * @param respuestaSilencio silencio máximo desde la última respuesta aceptada
     * @param canales canales explícitos, sin números ni slugs repetidos
     * @throws IllegalArgumentException si un formato o intervalo no cumple el contrato
     */
    public TwilioPropiedades {
        canales = canales == null ? List.of() : List.copyOf(canales);
        if (canales.stream().map(Canal::numero).distinct().count() != canales.size()
                || canales.stream().map(Canal::negocioSlug).distinct().count() != canales.size()) {
            throw new IllegalArgumentException("Configuración de canales Twilio duplicada.");
        }
        accountSid = accountSid == null ? "" : accountSid.strip();
        authToken = authToken == null ? "" : authToken;
        urlPublica = urlPublica == null ? "" : urlPublica;
        numero = numero == null || numero.isBlank() ? "+14155238886" : numero;
        if (!accountSid.isEmpty() && !accountSid.matches("AC[0-9a-fA-F]{32}")
                || !numero.matches("\\+[1-9][0-9]{1,14}")
                || !urlPublica.isEmpty() && !baseValida(urlPublica, false)
                || negocioSlug == null || negocioSlug.length() > 40 || !negocioSlug.matches("[a-z0-9](-?[a-z0-9])*")
                || intervaloEnvio == null || intervaloEnvio.compareTo(Duration.ofSeconds(3)) < 0
                || !baseValida(apiBase, true)
                || respuestaEspera == null || respuestaEspera.compareTo(Duration.ofSeconds(1)) < 0
                || respuestaEspera.compareTo(Duration.ofSeconds(12)) > 0
                || respuestaSilencio == null || respuestaSilencio.compareTo(Duration.ofMillis(100)) < 0
                || respuestaSilencio.compareTo(respuestaEspera) > 0) {
            throw new IllegalArgumentException("Configuración de Twilio Sandbox fuera de rango.");
        }
    }

    /**
     * Resuelve la lista explícita o el par histórico compatible.
     * @return canales configurados e inmutables
     */
    public List<Canal> canalesEfectivos() {
        return canales.isEmpty() ? List.of(new Canal(numero, negocioSlug)) : canales;
    }

    /**
     * Canal aislado que relaciona un número E.164 con un negocio.
     * @param numero número E.164 con signo más
     * @param negocioSlug slug canónico del negocio
     * @author Sohnny Walter Lima Infanzón
     * @version 2.0
     */
    public record Canal(String numero, String negocioSlug) {
        /**
         * Impide formatos ambiguos sin incluir valores en el error.
         * @param numero número E.164
         * @param negocioSlug slug canónico, de hasta cuarenta caracteres
         * @throws IllegalArgumentException si el par no cumple el formato
         */
        public Canal {
            if (numero == null || !numero.matches("\\+[1-9][0-9]{1,14}")
                    || negocioSlug == null || negocioSlug.length() > 40
                    || !negocioSlug.matches("[a-z0-9](-?[a-z0-9])*")) {
                throw new IllegalArgumentException("Configuración de canal Twilio no válida.");
            }
        }

        /** @return descripción fija sin número ni negocio */
        @Override
        public String toString() { return "Canal[configuración oculta]"; }
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
