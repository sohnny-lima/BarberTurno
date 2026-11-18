package pe.barberturno.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra y valida la rejilla global; las demás reglas se leen del negocio en cada transacción.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RejillaReserva.class)
public class ConfiguracionReservas {

    /**
     * Spring crea esta configuración al arrancar el contexto para registrar los parámetros de reservas.
     */
    public ConfiguracionReservas() {
    }
}
