package pe.barberturno.conversation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activa la configuración de la conversación guiada; sin el texto de privacidad el contexto no arranca
 * (V2-DA-68).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConversacionPropiedades.class)
public class ConfiguracionConversacion {

    /** Constructor usado por Spring. */
    public ConfiguracionConversacion() { }
}
