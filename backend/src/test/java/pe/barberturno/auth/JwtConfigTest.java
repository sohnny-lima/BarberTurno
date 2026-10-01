package pe.barberturno.auth;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class JwtConfigTest {
    private final JwtConfig config = new JwtConfig();

    @ParameterizedTest @ValueSource(strings = {"dev", "test"})
    void secreto_ausenteEnLocal_generaClaveAleatoriaDe32Bytes(String perfil) {
        var entorno = new MockEnvironment();
        entorno.setActiveProfiles(perfil);
        var primera = config.claveJwt("", entorno);
        var segunda = config.claveJwt("", entorno);
        assertThat(primera.getEncoded()).hasSize(32).isNotEqualTo(segunda.getEncoded());
        assertThat(primera.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @ParameterizedTest @ValueSource(strings = {"prod", "demo", ""})
    void secreto_ausenteFueraDeLocal_rechaza(String perfil) {
        var entorno = new MockEnvironment();
        if (!perfil.isEmpty()) entorno.setActiveProfiles(perfil);
        assertThatIllegalStateException().isThrownBy(() -> config.claveJwt("", entorno));
    }

    @ParameterizedTest @ValueSource(strings = {"%%%INVALIDO%%%", "YQ=="})
    void secreto_invalido_rechazaSinMostrarSuValor(String secreto) {
        assertThatIllegalStateException().isThrownBy(() -> config.claveJwt(secreto, new MockEnvironment()))
                .withMessageNotContaining(secreto);
    }

    @Test void secreto_configurado_usaLosBytesAportados() {
        byte[] datos = new byte[32];
        new java.security.SecureRandom().nextBytes(datos);
        assertThat(config.claveJwt(Base64.getEncoder().encodeToString(datos), new MockEnvironment())
                .getEncoded()).isEqualTo(datos);
    }

    @Test void secreto_prodMezcladoConDev_noGeneraSecretoEfimero() {
        var entorno = new MockEnvironment();
        entorno.setActiveProfiles("prod", "dev");
        assertThatIllegalStateException().isThrownBy(() -> config.claveJwt("", entorno));
    }
}
