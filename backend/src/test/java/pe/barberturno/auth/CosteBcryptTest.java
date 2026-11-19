package pe.barberturno.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CosteBcryptTest {
    private final JwtConfig config = new JwtConfig();

    @Test void valorPorDefecto_generaHashConCoste12() {
        PasswordEncoder encoder = config.passwordEncoder(12);

        assertThat(encoder.encode("ClavePrueba123")).startsWith("$2a$12$");
    }

    @Test void coste3_rechazaElConfigurador() {
        assertThatIllegalStateException()
                .isThrownBy(() -> config.passwordEncoder(3))
                .withMessage("El coste BCrypt debe estar entre 4 y 31.");
    }

    @Test void coste32_rechazaElConfigurador() {
        assertThatIllegalStateException()
                .isThrownBy(() -> config.passwordEncoder(32))
                .withMessage("El coste BCrypt debe estar entre 4 y 31.");
    }

    @Test void configuracionProd_declaraCoste12() throws IOException {
        try (var entrada = Objects.requireNonNull(
                getClass().getResourceAsStream("/application-prod.yml"))) {
            String contenido = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(contenido).contains("bcrypt-coste: 12");
        }
    }
}
