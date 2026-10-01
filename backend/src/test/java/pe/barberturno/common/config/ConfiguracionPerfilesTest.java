package pe.barberturno.common.config;

import java.util.Map;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracionPerfilesTest {

    @Test
    void importarSecretos_unaVariableDeEntornoPrevaleceSobreElArchivoLocal() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=test")
                .withInitializer(contexto -> {
                    contexto.getEnvironment().getPropertySources().addFirst(
                            new SystemEnvironmentPropertySource("variablesSimuladas",
                                    Map.of("BT_DB_PASSWORD", "valor-ficticio-de-entorno")));
                    new ConfigDataApplicationContextInitializer().initialize(contexto);
                })
                .run(contexto -> {
                    assertThat(contexto.getEnvironment().getProperty("spring.datasource.password"))
                            .isEqualTo("valor-ficticio-de-entorno");
                    assertThat(contexto.getEnvironment().getProperty("spring.config.import"))
                            .isEqualTo("optional:file:../.local/barberturno.env[.properties]");
                });
    }

    @Test
    void cargarProd_noImportaElArchivoLocalYDeshabilitaSwagger() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=prod")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(contexto -> {
                    boolean archivoLocalImportado = StreamSupport.stream(
                            contexto.getEnvironment().getPropertySources().spliterator(), false)
                            .anyMatch(fuente -> fuente.getName().contains("barberturno.env"));
                    assertThat(archivoLocalImportado).isFalse();
                    assertThat(contexto.getEnvironment().getProperty("spring.config.import")).isNull();
                    assertThat(contexto.getEnvironment().getProperty("springdoc.api-docs.enabled",
                            Boolean.class)).isFalse();
                    assertThat(contexto.getEnvironment().getProperty("springdoc.swagger-ui.enabled",
                            Boolean.class)).isFalse();
                });
    }
}
