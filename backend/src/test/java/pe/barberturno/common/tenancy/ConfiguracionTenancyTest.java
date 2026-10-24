package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfiguracionTenancyTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionTenancy.class, ContextoNegocio.class)
            .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class));

    @Test void sinPropiedad_arrancaSinNegocioPredeterminado() {
        contexto.run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(ProveedorNegocioActual.class).negocioActual()).isEmpty();
        });
    }
    @Test void prodConAlias_fallaAntesDeConectar() {
        contexto.withPropertyValues("spring.profiles.active=prod,test", "barberturno.tenancy.alias-legado.slug=barberturno")
                .run(c -> assertThat(c).hasFailed().getFailure().hasMessageContaining("solo admite dev/test/demo"));
    }
    @Test void prodSinAlias_noImponeNegocio() {
        contexto.withPropertyValues("spring.profiles.active=prod").run(c -> assertThat(c).hasNotFailed());
    }
    @Test void aliasSinPerfilLocal_falla() {
        contexto.withPropertyValues("spring.profiles.active=otro", "barberturno.tenancy.alias-legado.slug=barberturno")
                .run(c -> assertThat(c).hasFailed());
    }
}
