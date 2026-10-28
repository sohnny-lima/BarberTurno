package pe.barberturno.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las propiedades y la programación de trabajos de la entrada y la salida de WhatsApp. El procesador
 * y el enviador solo se programan si {@code barberturno.whatsapp.procesador.habilitado} y
 * {@code barberturno.whatsapp.salida.habilitado} son verdaderos (V2-DA-08).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({WhatsAppPropiedades.class, SalidaWhatsAppPropiedades.class})
public class ConfiguracionWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionWhatsApp.class);

    /**
     * Avisa al arrancar, sin mostrar ningún valor, de que el webhook rechazará todo mientras falten sus
     * secretos (V2-DA-55) y de que no se enviará nada mientras falte el token de la Graph API (V2-DA-62).
     * @param propiedades configuración de la entrada, ya validada
     * @param salida configuración de la salida, ya validada
     */
    public ConfiguracionWhatsApp(WhatsAppPropiedades propiedades, SalidaWhatsAppPropiedades salida) {
        if (!propiedades.verificacionConfigurada() || !propiedades.firmaConfigurada()) {
            LOG.warn("WhatsApp sin configurar: el webhook rechaza verificaciones y notificaciones (V2-DA-55).");
        }
        if (!salida.envioConfigurado()) {
            LOG.warn("WhatsApp sin token de la Graph API: los mensajes salientes quedan pendientes (V2-DA-62).");
        }
    }
}
