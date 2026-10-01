package pe.barberturno.common.config;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguracionProduccionTest {

    @Test
    void validar_sinVariables_informaLosNombresSinValores() {
        MockEnvironment entorno = new MockEnvironment();
        entorno.setActiveProfiles("prod");

        assertThatThrownBy(() -> validar(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Faltan variables obligatorias de producción: BT_DB_URL, BT_DB_USER, "
                        + "BT_DB_PASSWORD, BT_JWT_SECRET, BT_ADMIN_CORREO, BT_ADMIN_PASSWORD, "
                        + "BT_ADMIN_NOMBRE, BT_COOKIE_SECURE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BT_DB_URL", "BT_DB_USER", "BT_DB_PASSWORD", "BT_JWT_SECRET",
            "BT_ADMIN_CORREO", "BT_ADMIN_PASSWORD", "BT_ADMIN_NOMBRE", "BT_COOKIE_SECURE"})
    void validar_conUnaVariableVacia_rechazaLaConfiguracion(String nombre) {
        MockEnvironment entorno = entornoValido().withProperty(nombre, " ");

        assertThatThrownBy(() -> validar(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(nombre)
                .hasMessageNotContaining("valor-ficticio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test", "demo"})
    void validar_conPerfilLocalMezclado_rechazaProduccion(String perfil) {
        MockEnvironment entorno = entornoValido();
        entorno.setActiveProfiles("prod", perfil);

        assertThatThrownBy(() -> validar(entorno))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El perfil prod no puede combinarse con dev, test o demo.");
    }

    @Test
    void validar_conSecretoMalCodificado_noExponeSuValor() {
        MockEnvironment entorno = entornoValido().withProperty("BT_JWT_SECRET", "valor-ficticio!");

        assertThatThrownBy(() -> validar(entorno))
                .hasMessage("BT_JWT_SECRET debe estar codificado en Base64.");
    }

    @Test
    void validar_conSecretoCorto_rechazaProduccion() {
        MockEnvironment entorno = entornoValido().withProperty("BT_JWT_SECRET",
                Base64.getEncoder().encodeToString(new byte[31]));

        assertThatThrownBy(() -> validar(entorno))
                .hasMessage("BT_JWT_SECRET debe contener al menos 32 bytes decodificados.");
    }

    @Test
    void validar_conCookieInsegura_rechazaProduccion() {
        MockEnvironment entorno = entornoValido().withProperty("BT_COOKIE_SECURE", "false");

        assertThatThrownBy(() -> validar(entorno))
                .hasMessage("BT_COOKIE_SECURE debe ser true en producción.");
    }

    @Test
    void validar_conVariablesCompletas_aceptaLaConfiguracion() {
        assertThatCode(() -> validar(entornoValido())).doesNotThrowAnyException();
    }

    private void validar(MockEnvironment entorno) {
        ConfiguracionProduccion.validarVariablesProduccion(entorno)
                .postProcessBeanFactory(new DefaultListableBeanFactory());
    }

    private MockEnvironment entornoValido() {
        // Todos los valores de esta prueba son ficticios y no permiten acceso real.
        MockEnvironment entorno = new MockEnvironment()
                .withProperty("BT_DB_URL", "jdbc:postgresql://localhost:5433/barberturno")
                .withProperty("BT_DB_USER", "barberturno")
                .withProperty("BT_DB_PASSWORD", "valor-ficticio")
                .withProperty("BT_JWT_SECRET", Base64.getEncoder().encodeToString(new byte[32]))
                .withProperty("BT_ADMIN_CORREO", "admin@ejemplo.test")
                .withProperty("BT_ADMIN_PASSWORD", "valor-ficticio")
                .withProperty("BT_ADMIN_NOMBRE", "Administrador de demostración")
                .withProperty("BT_COOKIE_SECURE", "true");
        entorno.setActiveProfiles("prod");
        return entorno;
    }
}
