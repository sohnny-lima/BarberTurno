package pe.barberturno.conversation.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Activa {@link IaPropiedades} y el observador nulo de producción (17-v2-12 §4.4). El cliente del proveedor lo
 * crea la configuración del adaptador (Codex).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IaPropiedades.class)
public class ConfiguracionContratoIA {
    /** Constructor usado por Spring. */
    public ConfiguracionContratoIA() { }

    /**
     * Observador que no hace nada; la evaluación lo sustituye por uno que registra.
     * @return observador nulo
     */
    @Bean
    @ConditionalOnMissingBean(ObservadorEvaluacion.class)
    ObservadorEvaluacion observadorEvaluacionNulo() {
        return new ObservadorEvaluacion() { };
    }
}
