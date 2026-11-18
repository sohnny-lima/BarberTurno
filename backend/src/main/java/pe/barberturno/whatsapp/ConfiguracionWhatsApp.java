package pe.barberturno.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
     * secretos (V2-DA-55) y de que Meta no enviará sin token (V2-DA-62). En twilio no atribuye a
     * la falta del token de Graph el estado de la outbox, cuyo transporte usa otras credenciales.
     * @param propiedades configuración de la entrada, ya validada
     * @param salida configuración de la salida, ya validada
     * @param entorno perfiles activos para distinguir el transporte de pruebas
     */
    public ConfiguracionWhatsApp(WhatsAppPropiedades propiedades, SalidaWhatsAppPropiedades salida, Environment entorno) {
        if (!propiedades.verificacionConfigurada() || !propiedades.firmaConfigurada()) {
            LOG.warn("WhatsApp sin configurar: el webhook rechaza verificaciones y notificaciones (V2-DA-55).");
        }
        if (!entorno.acceptsProfiles(Profiles.of("twilio")) && !salida.envioConfigurado()) {
            LOG.warn("WhatsApp sin token de la Graph API: los mensajes salientes quedan pendientes (V2-DA-62).");
        }
    }
}
