package pe.barberturno.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las propiedades y la programación de trabajos de la entrada de WhatsApp. El procesador solo se
 * programa si {@code barberturno.whatsapp.procesador.habilitado} es verdadero (V2-DA-08).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(WhatsAppPropiedades.class)
public class ConfiguracionWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionWhatsApp.class);

    /**
     * Avisa al arrancar de que el webhook rechazará todo mientras falten secretos, sin mostrar ningún valor.
     * @param propiedades configuración ya validada
     */
    public ConfiguracionWhatsApp(WhatsAppPropiedades propiedades) {
        if (!propiedades.verificacionConfigurada() || !propiedades.firmaConfigurada()) {
            LOG.warn("WhatsApp sin configurar: el webhook rechaza verificaciones y notificaciones (V2-DA-55).");
        }
    }
}
