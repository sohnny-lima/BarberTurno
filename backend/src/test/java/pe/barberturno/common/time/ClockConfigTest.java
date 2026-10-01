package pe.barberturno.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void crearClock_utilizaAmericaLima() {
        Clock clock = new ClockConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(Instant.parse("2026-09-28T14:00:00Z").atZone(clock.getZone()).toLocalTime())
                .isEqualTo(LocalTime.of(9, 0));
    }
}
