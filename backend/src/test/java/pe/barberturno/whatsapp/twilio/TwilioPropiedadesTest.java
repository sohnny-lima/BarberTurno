package pe.barberturno.whatsapp.twilio;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de validación de {@link TwilioPropiedades} (CT-13, 20-v2-twilio-sandbox.md §10).
 * Valida los rangos estrictos de los plazos de TwiML (espera entre 1 y 12 s, silencio entre 100 ms y la espera),
 * que las configuraciones inválidas lancen {@link IllegalArgumentException} con un mensaje genérico
 * fijo que no filtre valores ni secretos, y que {@link TwilioPropiedades#toString()} proteja la configuración.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class TwilioPropiedadesTest {

    private static final String MENSAJE_ERROR_ESPERADO = "Configuración de Twilio Sandbox fuera de rango.";

    @Test
    void constructor_valoresValidos_construyeCorrectamente() {
        assertThatCode(() -> new TwilioPropiedades(
                "AC0123456789abcdef0123456789abcdef", "token-ficticio", "+14155238886",
                "https://pruebas.barberturno.test", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(10), Duration.ofMillis(1500)))
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_limitesValidosDeEsperaYSilencio_exito() {
        // Límite inferior de espera: 1 s; límite inferior de silencio: 100 ms
        assertThatCode(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(1), Duration.ofMillis(100)))
                .doesNotThrowAnyException();

        // Límite superior de espera: 12 s; silencio igual a la espera
        assertThatCode(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(12), Duration.ofSeconds(12)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 500, 999, 12001, 15000, 60000})
    void constructor_respuestaEsperaFueraDeRango_lanzaIllegalArgumentExceptionSinValores(long millis) {
        Duration esperaInvalida = Duration.ofMillis(millis);

        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, esperaInvalida, Duration.ofMillis(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(String.valueOf(millis)));
    }

    @ParameterizedTest
    @NullSource
    void constructor_respuestaEsperaNula_lanzaIllegalArgumentException(Duration esperaNula) {
        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, esperaNula, Duration.ofMillis(100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 50, 99})
    void constructor_respuestaSilencioMenorA100Ms_lanzaIllegalArgumentExceptionSinValores(long millis) {
        Duration silencioInvalido = Duration.ofMillis(millis);

        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(5), silencioInvalido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(String.valueOf(millis)));
    }

    @Test
    void constructor_respuestaSilencioMayorAEspera_lanzaIllegalArgumentExceptionSinValores() {
        Duration espera = Duration.ofSeconds(5);
        Duration silencioMayor = Duration.ofSeconds(6);

        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, espera, silencioMayor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("6").doesNotContain("5"));
    }

    @ParameterizedTest
    @NullSource
    void constructor_respuestaSilencioNulo_lanzaIllegalArgumentException(Duration silencioNulo) {
        assertThatThrownBy(() -> new TwilioPropiedades(
                "", "", "+14155238886", "", "barberturno", Duration.ofSeconds(3),
                "https://api.twilio.com", true, Duration.ofSeconds(5), silencioNulo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MENSAJE_ERROR_ESPERADO);
    }

    @Test
    void toString_noFiltraSecretosNiValores() {
        TwilioPropiedades propiedades = new TwilioPropiedades(
                "AC0123456789abcdef0123456789abcdef", "secreto-super-confidencial",
                "+14155238886", "https://pruebas.barberturno.test", "barberturno",
                Duration.ofSeconds(3), "https://api.twilio.com", true,
                Duration.ofSeconds(10), Duration.ofMillis(1500));

        assertThat(propiedades.toString()).isEqualTo("TwilioPropiedades[configuración oculta]");
    }
}
