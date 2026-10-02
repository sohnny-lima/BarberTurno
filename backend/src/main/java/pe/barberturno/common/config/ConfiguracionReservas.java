package pe.barberturno.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra y valida los parámetros externos al arrancar la aplicación.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ParametrosReserva.class)
public class ConfiguracionReservas {

    /**
     * Spring crea esta configuración al arrancar el contexto para registrar los parámetros de reservas.
     */
    public ConfiguracionReservas() {
    }
}
