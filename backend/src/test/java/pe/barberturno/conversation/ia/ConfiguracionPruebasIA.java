package pe.barberturno.conversation.ia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * Configuración de pruebas para la integración de conversación con IA (17-v2-12 §4; V2-13).
 * Registra el adaptador {@link ModeloConversacionalFalso} como {@link Primary} y el observador
 * en memoria {@link ObservadorEvaluacionRegistro}. Activa las propiedades de prueba para habilitar
 * la IA con clave ficticia y mantener el trabajador de turnos en modo manual para ejecución controlada.
 * Debe utilizarse con {@code @DirtiesContext(classMode = AFTER_CLASS)}.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@TestConfiguration
@TestPropertySource(properties = {
        "barberturno.ia.habilitada=true",
        "barberturno.ia.clave=clave-de-prueba",
        "barberturno.ia.modelo=claude-opus-5-5",
        "barberturno.ia.trabajador.habilitado=false"
})
public class ConfiguracionPruebasIA {

    /**
     * Propiedades estándar para activar la IA en pruebas de integración.
     */
    public static final String[] PROPIEDADES_IA = {
            "barberturno.ia.habilitada=true",
            "barberturno.ia.clave=clave-de-prueba",
            "barberturno.ia.modelo=claude-opus-5-5",
            "barberturno.ia.trabajador.habilitado=false"
    };

    /** Constructor para Spring Boot Test. */
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
