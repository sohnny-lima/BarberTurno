package pe.barberturno.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puertos sin efectos hasta instalar conversación y salida; solo registran el id del evento.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
class ReceptoresWhatsAppPorDefecto {
    private static final Logger LOG = LoggerFactory.getLogger(ReceptoresWhatsAppPorDefecto.class);

    @Bean
    @ConditionalOnMissingBean(ReceptorMensajesWhatsApp.class)
    ReceptorMensajesWhatsApp mensajesWhatsAppPorDefecto() {
        return mensaje -> LOG.debug("Evento WhatsApp {}: receptor por defecto.", mensaje.eventoId());
    }

    @Bean
    @ConditionalOnMissingBean(ReceptorEstadosWhatsApp.class)
    ReceptorEstadosWhatsApp estadosWhatsAppPorDefecto() {
        return estado -> LOG.debug("Evento WhatsApp {}: receptor por defecto.", estado.eventoId());
    }
}
