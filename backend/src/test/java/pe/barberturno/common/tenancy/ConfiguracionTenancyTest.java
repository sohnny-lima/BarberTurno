package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
                .run(c -> assertThat(c).hasFailed().getFailure().hasMessageContaining("alias legado fue retirado"));
    }
    @Test void prodSinAlias_noImponeNegocio() {
        contexto.withPropertyValues("spring.profiles.active=prod").run(c -> assertThat(c).hasNotFailed());
    }
    @Test void propiedadRetirada_noActivaUnProveedorTransitorio() {
        contexto.withPropertyValues("barberturno.tenancy.negocio-unico-id=1")
                .run(c -> assertThat(c.getBean(ProveedorNegocioActual.class).negocioActual()).isEmpty());
    }
    @Test void aliasSinPerfilLocal_falla() {
        contexto.withPropertyValues("spring.profiles.active=otro", "barberturno.tenancy.alias-legado.slug=barberturno")
                .run(c -> assertThat(c).hasFailed());
    }

    @ParameterizedTest @ValueSource(strings = {"dev", "test", "demo", "prod", "prod,test", "otro", "default"})
    void aliasEnCualquierPerfil_fallaAntesDeCrearLaFabrica(String perfil) {
        var creada = new java.util.concurrent.atomic.AtomicBoolean();
        new ApplicationContextRunner().withUserConfiguration(ConfiguracionTenancy.class, ContextoNegocio.class)
                .withBean(EntityManagerFactory.class, () -> { creada.set(true); return mock(EntityManagerFactory.class); })
                .withPropertyValues("spring.profiles.active=" + perfil, "barberturno.tenancy.alias-legado.slug=")
                .run(c -> {
                    assertThat(c).hasFailed().getFailure().hasMessageContaining("alias legado fue retirado");
                    assertThat(creada).isFalse();
                });
    }

    @ParameterizedTest @CsvSource({"barberturno.tenancy.alias-legado.habilitado,false",
            "barberturno.tenancy.alias-legado,cualquier-valor", "barberturno.tenancy.alias-legado.otro.valor,x"})
    void cualquierClaveDelAlias_fallaAunqueNoSeaSlug(String clave, String valor) {
        contexto.withPropertyValues(clave + "=" + valor).run(c -> assertThat(c).hasFailed());
    }

    @Test void aliasDesdeVariableDeEntorno_falla() {
        contexto.withInitializer(c -> c.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.SystemEnvironmentPropertySource("entornoSimulado",
                        java.util.Map.of("BARBERTURNO_TENANCY_ALIASLEGADO_SLUG", "barberturno"))))
                .run(c -> assertThat(c).hasFailed());
    }
}
