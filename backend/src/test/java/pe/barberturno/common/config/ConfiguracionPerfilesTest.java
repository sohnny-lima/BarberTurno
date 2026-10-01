package pe.barberturno.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracionPerfilesTest {

    @TempDir
    Path directorioTemporal;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void importarSecretos_respetaLaPrioridadDelEntornoSobreUnArchivoPresente(
            boolean simularEntorno) throws IOException {
        Path archivo = directorioTemporal.resolve("secretos-ficticios.properties");
        Files.writeString(archivo, "BT_DB_PASSWORD=valor-de-archivo-ficticio\n",
                StandardCharsets.UTF_8);

        // Aislar la prueba de los secretos y perfiles del equipo o de la CI.
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.config.location=optional:file:"
                                + directorioTemporal.resolve("sin-configuracion.properties").toUri(),
                        "spring.config.import=optional:" + archivo.toUri() + "[.properties]",
                        "spring.datasource.password=${BT_DB_PASSWORD}")
                .withInitializer(contexto -> {
                    var fuentes = contexto.getEnvironment().getPropertySources();
                    fuentes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    fuentes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    if (simularEntorno) {
                        fuentes.addFirst(new SystemEnvironmentPropertySource("variablesSimuladas",
                                Map.of("BT_DB_PASSWORD", "valor-ficticio-de-entorno")));
                    }
                    new ConfigDataApplicationContextInitializer().initialize(contexto);
                })
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    boolean archivoImportado = StreamSupport.stream(
                            contexto.getEnvironment().getPropertySources().spliterator(), false)
                            .anyMatch(fuente -> fuente.getName().contains(archivo.getFileName().toString())
                                    && "valor-de-archivo-ficticio".equals(
                                            fuente.getProperty("BT_DB_PASSWORD")));
                    assertThat(archivoImportado).isTrue();
                    assertThat(contexto.getEnvironment().getProperty("spring.datasource.password"))
                            .isEqualTo(simularEntorno
                                    ? "valor-ficticio-de-entorno" : "valor-de-archivo-ficticio");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void cargarPerfilLocal_importaLasRutasDesdeBackendYDesdeLaRaiz(String perfil) {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + perfil)
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    List<String> importaciones = Binder.get(contexto.getEnvironment())
                            .bind("spring.config.import", Bindable.listOf(String.class)).get();
                    assertThat(importaciones).containsExactly(
                            "optional:file:../.local/barberturno.env[.properties]",
                            "optional:file:./.local/barberturno.env[.properties]");
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
