package pe.barberturno.whatsapp;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PoliticaReintentosTest {
    @Test void espera_duplicaDesdeElPrimerIntentoYSaturaEnCincoMinutos() {
        for (int intento = 1; intento <= 10; intento++) {
            assertThat(PoliticaReintentos.espera(intento, Duration.ofSeconds(5), Duration.ofMinutes(5)))
                    .isEqualTo(Duration.ofSeconds(Math.min(300, 5L << (intento - 1))));
        }
    }

    @Test void topeNoPotenciaDeDos_yDuracionesFraccionarias_conservanPrecision() {
        assertThat(PoliticaReintentos.espera(4, Duration.ofMillis(1500), Duration.ofMillis(5500)))
                .isEqualTo(Duration.ofMillis(5500));
        assertThat(PoliticaReintentos.espera(2, Duration.ofNanos(1), Duration.ofNanos(3)))
                .isEqualTo(Duration.ofNanos(2));
    }

    @Test void intentosEnormes_yDuracionesMaximas_noDesbordan() {
        var tope = Duration.ofSeconds(Long.MAX_VALUE, 999999999);
        assertThat(PoliticaReintentos.espera(Integer.MAX_VALUE, Duration.ofNanos(1), tope)).isEqualTo(tope);
        assertThat(PoliticaReintentos.espera(Integer.MAX_VALUE, tope, tope)).isEqualTo(tope);
    }

    @Test void parametrosInvalidos_seRechazan() {
        for (int intento : new int[]{0, -1}) {
            assertThatIllegalArgumentException().isThrownBy(() -> PoliticaReintentos.espera(intento,
                    Duration.ofSeconds(5), Duration.ofMinutes(5)));
        }
        for (Duration base : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMinutes(6)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> PoliticaReintentos.espera(1, base, Duration.ofMinutes(5)));
        }
    }
}
