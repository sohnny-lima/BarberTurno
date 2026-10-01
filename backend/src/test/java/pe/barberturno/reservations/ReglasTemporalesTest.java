package pe.barberturno.reservations;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ReglasTemporalesTest {
    private final ReglasTemporales reglas = new ReglasTemporales();
    private final Instant inicio = Instant.parse("2026-10-01T15:00:00Z");
    private final Duration anticipacion = Duration.ofHours(2);

    @Test void fin_segunDuracion() {
        assertThat(reglas.calcularFin(inicio, 30)).isEqualTo(inicio.plusSeconds(1800));
    }
    @Test void solapamiento_parcial() {
        assertThat(reglas.seSolapan(inicio, inicio.plusSeconds(1800), inicio.plusSeconds(600), inicio.plusSeconds(2400))).isTrue();
    }
    @Test void franjas_contiguasPermitidas() {
        assertThat(reglas.seSolapan(inicio, inicio.plusSeconds(1800), inicio.plusSeconds(1800), inicio.plusSeconds(3000))).isFalse();
        assertThat(reglas.seSolapan(inicio.plusSeconds(1800), inicio.plusSeconds(3000), inicio, inicio.plusSeconds(1800))).isFalse();
    }
    @Test void modificar_limiteExactoDeDosHoras() {
        assertThat(reglas.puedeModificar(inicio.minusSeconds(7200), inicio, false, null, anticipacion)).isTrue();
    }
    @Test void modificar_rechazoAntesDelMinimo() {
        assertThat(reglas.puedeModificar(inicio.minusSeconds(7140), inicio, false, null, anticipacion)).isFalse();
        assertThat(reglas.puedeModificar(inicio.minusSeconds(7199), inicio, false, null, anticipacion)).isFalse();
    }
    @Test void excepcionAdministrativa_requiereMotivoYSerAnteriorAlInicio() {
        Instant antes = inicio.minusSeconds(1);
        assertThat(reglas.puedeModificar(antes, inicio, true, " ", anticipacion)).isFalse();
        assertThat(reglas.puedeModificar(antes, inicio, true, "Solicitud del cliente", anticipacion)).isTrue();
        assertThat(reglas.puedeModificar(antes, inicio, true, null, anticipacion)).isFalse();
        assertThat(reglas.puedeModificar(antes, inicio, true, "abcd", anticipacion)).isFalse();
        assertThat(reglas.puedeModificar(antes, inicio, true, "a b c d", anticipacion)).isFalse();
        assertThat(reglas.puedeModificar(antes, inicio, true, "a b c d e", anticipacion)).isTrue();
        assertThat(reglas.puedeModificar(inicio, inicio, true, "Solicitud", anticipacion)).isFalse();
        assertThat(reglas.puedeModificar(inicio.plusSeconds(1), inicio, true, "Solicitud", anticipacion)).isFalse();
    }
    @ParameterizedTest @ValueSource(ints = {0, -1})
    void duracion_invalidaRechazada(int minutos) {
        assertThatIllegalArgumentException().isThrownBy(() -> reglas.calcularFin(inicio, minutos));
    }
    @Test void intervalos_invalidosONulosRechazados() {
        assertThatIllegalArgumentException().isThrownBy(() -> reglas.seSolapan(inicio, inicio, inicio, inicio.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> reglas.seSolapan(inicio, inicio.plusSeconds(1), inicio, inicio.minusSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> reglas.calcularFin(null, 30));
        assertThatNullPointerException().isThrownBy(() -> reglas.seSolapan(null, inicio, inicio, inicio.plusSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> reglas.seSolapan(inicio, null, inicio, inicio.plusSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> reglas.puedeModificar(null, inicio, false, null, anticipacion));
        assertThatNullPointerException().isThrownBy(() -> reglas.puedeModificar(inicio, null, false, null, anticipacion));
        assertThatNullPointerException().isThrownBy(() -> reglas.puedeModificar(inicio, inicio, false, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> reglas.puedeModificar(inicio, inicio, false, null, Duration.ofSeconds(-1)));
        assertThat(reglas.puedeModificar(inicio, inicio, false, null, Duration.ZERO)).isTrue();
    }
}
