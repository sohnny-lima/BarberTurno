package pe.barberturno.whatsapp.twilio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Registra las propiedades del adaptador únicamente en el perfil local twilio (V2-DA-97).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Configuration(proxyBeanMethods = false)
@Profile("twilio")
@EnableConfigurationProperties(TwilioPropiedades.class)
public class ConfiguracionTwilio {
    /** Activa el enlace de propiedades sin crear transporte fuera del perfil. */
    public ConfiguracionTwilio() { }
}
