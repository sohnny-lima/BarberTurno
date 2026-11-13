package pe.barberturno.whatsapp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la entrada de WhatsApp (V2-08). Los secretos llegan solo por el entorno
 * ({@code BT_WHATSAPP_VERIFY_TOKEN}, {@code BT_WHATSAPP_APP_SECRET}); vacíos, el webhook falla cerrado (V2-DA-55).
 * @param verifyToken token que Meta devuelve en {@code hub.verify_token}; vacío deniega la verificación
 * @param appSecret secreto de la app de Meta con el que se firma cada notificación; vacío rechaza toda notificación
 * @param procesador ritmo, tamaño de lote y política de reintentos de la cola de eventos
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@ConfigurationProperties("barberturno.whatsapp")
public record WhatsAppPropiedades(@DefaultValue("") String verifyToken, @DefaultValue("") String appSecret,
        @DefaultValue Procesador procesador) {

    /**
     * Normaliza los secretos ausentes a cadena vacía para que nunca se comparen con {@code null}.
     * @param verifyToken token de verificación o {@code null}
     * @param appSecret secreto de firma o {@code null}
     * @param procesador parámetros del procesador o {@code null} para los valores por defecto
     */
    public WhatsAppPropiedades {
        verifyToken = verifyToken == null ? "" : verifyToken;
        appSecret = appSecret == null ? "" : appSecret;
        procesador = procesador == null ? new Procesador(true, Duration.ofMillis(1500), 20, 5,
                Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofSeconds(60)) : procesador;
    }

    /** {@return si hay un token de verificación configurado} */
    public boolean verificacionConfigurada() { return !verifyToken.isBlank(); }

    /** {@return si hay un secreto de firma configurado} */
    public boolean firmaConfigurada() { return !appSecret.isBlank(); }

    /**
     * Parámetros de {@code ProcesadorEventosWhatsApp} (11-v2-08-whatsapp-entrada.md §4.3).
     * @param habilitado si se programa el trabajo; las pruebas lo desactivan y llaman al lote directamente
     * @param intervalo espera entre dos rondas del trabajo programado
     * @param lote número máximo de eventos reclamados por ronda
     * @param maxIntentos reclamos tras los que un fallo deja el evento en {@code ERROR}
     * @param esperaBase espera del primer reintento, que se duplica en cada intento
     * @param esperaMaxima límite superior de la espera entre reintentos
     * @param reclamo plazo tras el que un evento reclamado y no terminado vuelve a estar disponible
     */
    public record Procesador(@DefaultValue("true") boolean habilitado, @DefaultValue("1500ms") Duration intervalo,
            @DefaultValue("20") int lote, @DefaultValue("5") int maxIntentos, @DefaultValue("5s") Duration esperaBase,
            @DefaultValue("5m") Duration esperaMaxima, @DefaultValue("60s") Duration reclamo) {
        /**
         * Rechaza valores que detendrían la cola o la harían girar sin espera.
         * @param habilitado si se programa el trabajo
         * @param intervalo espera entre rondas, positiva
         * @param lote tamaño de lote, entre 1 y 500
         * @param maxIntentos intentos máximos, al menos 1
         * @param esperaBase espera inicial, positiva
         * @param esperaMaxima espera máxima, no menor que la inicial
         * @param reclamo plazo del reclamo, positivo
         * @throws IllegalArgumentException si algún valor está fuera de rango
         */
        public Procesador {
            if (intervalo.isNegative() || intervalo.isZero() || lote < 1 || lote > 500 || maxIntentos < 1
                    || esperaBase.isNegative() || esperaBase.isZero() || esperaMaxima.compareTo(esperaBase) < 0
                    || reclamo.isNegative() || reclamo.isZero()) {
                throw new IllegalArgumentException("Configuración del procesador de WhatsApp fuera de rango.");
            }
        }
    }
}
