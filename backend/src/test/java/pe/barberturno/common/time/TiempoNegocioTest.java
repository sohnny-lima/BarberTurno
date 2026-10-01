package pe.barberturno.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TiempoNegocioTest {
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 1);

    @Test
    void inicioDelDia_medianocheLima_esCincoUtc() {
        assertThat(TiempoNegocio.inicioDelDia(FECHA)).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
    }

    @Test
    void finDelDia_esInicioExclusivoDelDiaSiguiente() {
        Instant fin = TiempoNegocio.finDelDia(FECHA);
        assertThat(fin).isEqualTo(Instant.parse("2026-10-02T05:00:00Z"));
        assertThat(TiempoNegocio.fechaLima(fin.minusNanos(1))).isEqualTo(FECHA);
        assertThat(TiempoNegocio.fechaLima(fin)).isEqualTo(FECHA.plusDays(1));
    }

    @Test
    void diaIso_jueves_esCuatro() {
        assertThat(TiempoNegocio.diaIso(TiempoNegocio.inicioDelDia(FECHA))).isEqualTo(4);
    }

    @Test
    void convertirInstant_aLimaYDeVuelta_conservaInstanteYDesfase() {
        Instant instante = Instant.parse("2026-10-01T15:10:00Z");
        OffsetDateTime lima = TiempoNegocio.aLima(instante);
        assertThat(lima).isEqualTo(OffsetDateTime.parse("2026-10-01T10:10:00-05:00"));
        assertThat(lima.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
        assertThat(lima.toInstant()).isEqualTo(instante);
    }

    @Test
    void fechaLima_aLasVeintitresTreinta_perteneceAlDiaAnteriorUtc() {
        Instant instante = Instant.parse("2026-10-02T04:30:00Z");
        assertThat(TiempoNegocio.fechaLima(instante)).isEqualTo(FECHA);
        assertThat(TiempoNegocio.diaIso(instante)).isEqualTo(4);
        assertThat(TiempoNegocio.aLima(instante).getHour()).isEqualTo(23);
    }
}
