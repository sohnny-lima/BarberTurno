package pe.barberturno.conversation.ia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Configuración de pruebas para la integración de conversación con IA (17-v2-12 §4; V2-13).
 * Registra los beans del adaptador {@link ModeloConversacionalFalso} como {@link Primary} y del
 * observador en memoria {@link ObservadorEvaluacionRegistro}.
 *
 * <p><strong>Nota de configuración (H-XI-01):</strong> La anotación {@code @TestPropertySource}
 * no se propaga desde una {@code @TestConfiguration} importada; por tanto, las clases de prueba
 * deben declarar explícitamente sus propiedades de prueba (p. ej. mediante {@link #PROPIEDADES_IA}
 * o la anotación directa en la clase del caso de prueba) para activar la IA ficticia y deshabilitar
 * el trabajador automático.</p>
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionPruebasIA {

    /**
     * Propiedades estándar recomendadas para activar la IA en pruebas de integración (CI-11; H-XI-01).
     */
    public static final String[] PROPIEDADES_IA = {
            "barberturno.ia.habilitada=true",
            "barberturno.ia.clave=clave-ficticia-local",
            "barberturno.ia.trabajador.habilitado=false",
            "barberturno.ia.trabajador.lote=1"
    };

    /**
     * Constructor público requerido por Spring Boot Test.
     */
    public ConfiguracionPruebasIA() { }

    /**
     * Adaptador falso primario para inyección en los servicios de IA.
     *
     * @return instancia de {@link ModeloConversacionalFalso}
     */
    @Bean
    @Primary
    public ModeloConversacionalFalso modeloConversacionalFalso() {
        return new ModeloConversacionalFalso();
    }

    /**
     * Observador en memoria para registrar todas las llamadas, herramientas y guardas.
     *
     * @return instancia de {@link ObservadorEvaluacionRegistro}
     */
    @Bean
    @Primary
    public ObservadorEvaluacionRegistro observadorEvaluacionRegistro() {
        return new ObservadorEvaluacionRegistro();
    }
}
