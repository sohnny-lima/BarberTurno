package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ConfiguracionTenancyTest {
    @Test
    void sinPropiedad_noArrancaElProveedorTransitorio() {
        new ApplicationContextRunner().withUserConfiguration(ConfiguracionTenancy.class)
                .run(c -> assertThat(c).hasFailed().getFailure().hasStackTraceContaining("negocio-unico-id"));
    }

    @Test
    void prodConPropiedad_tambienFallaAntesDeConectar() {
        new ApplicationContextRunner().withUserConfiguration(ConfiguracionTenancy.class)
                .withPropertyValues("spring.profiles.active=prod", "barberturno.tenancy.negocio-unico-id=1")
                .run(c -> assertThat(c).hasFailed().getFailure().hasMessageContaining("no admite el perfil prod"));
    }

    @Test
    void prodSinPropiedad_fallaAntesDeConectar() {
        new ApplicationContextRunner().withUserConfiguration(ConfiguracionTenancy.class)
                .withPropertyValues("spring.profiles.active=prod")
                .run(c -> assertThat(c).hasFailed().getFailure().hasMessageContaining("no admite el perfil prod"));
    }
}
