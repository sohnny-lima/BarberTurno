package pe.barberturno.demo;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pe.barberturno.common.config.ConfiguracionProduccion;
import static org.assertj.core.api.Assertions.assertThat;

/** Exclusión de todos los cargadores, incluidos los del escenario original. */
class PerfilesDemoTest {
    private ApplicationContextRunner cargadores(String... perfiles) {
        return new ApplicationContextRunner().withUserConfiguration(DatosDemoService.class,
                DatosDemoRunner.class, DatosDemoMultinegocioService.class, DatosDemoMultinegocioRunner.class)
                .withInitializer(c -> c.getEnvironment().setActiveProfiles(perfiles));
    }

    @Test void prod_noRegistraNingunCargador() {
        for (String perfiles : List.of("prod", "prod,demo")) {
            cargadores(perfiles.split(",")).run(c -> assertThat(c).hasNotFailed()
                    .doesNotHaveBean(DatosDemoService.class).doesNotHaveBean(DatosDemoRunner.class)
                    .doesNotHaveBean(DatosDemoMultinegocioService.class)
                    .doesNotHaveBean(DatosDemoMultinegocioRunner.class));
        }
    }

    @Test void prodConDemo_impideArrancarElContexto() {
        cargadores("prod", "demo").withUserConfiguration(ConfiguracionProduccion.class)
                .run(c -> assertThat(c).hasFailed().getFailure()
                        .hasMessageContaining("El perfil prod no puede combinarse con dev, test o demo."));
    }
}
