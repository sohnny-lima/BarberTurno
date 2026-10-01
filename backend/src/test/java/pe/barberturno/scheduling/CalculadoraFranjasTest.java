package pe.barberturno.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.scheduling.CalculadoraFranjas.Franja;
import pe.barberturno.scheduling.CalculadoraFranjas.IntervaloJornada;
import static org.assertj.core.api.Assertions.*;

class CalculadoraFranjasTest {
    private final CalculadoraFranjas calculadora = new CalculadoraFranjas();
    private final LocalDate fecha = LocalDate.of(2026, 10, 1);
    private final Instant ahora = Instant.parse("2026-09-28T14:00:00Z");
    private final Instant horizonte = Instant.parse("2026-10-28T14:00:00Z");
    private final List<IntervaloJornada> jornadas = List.of(jornada("09:00", "13:00"), jornada("14:00", "18:00"));
    private final List<Franja> ocupaciones = List.of(franja("10:00", "10:30"), franja("16:00", "17:00"));

    @Test void calcular_corridaConReservaYBloqueo() {
        var resultado = calcular(30, ahora, horizonte);
        var inicios = resultado.stream().map(f -> TiempoNegocio.aLima(f.inicio()).toLocalTime()).toList();
        assertThat(resultado).hasSize(31).isSortedAccordingTo(java.util.Comparator.comparing(Franja::inicio));
        assertThat(inicios).doesNotContain(LocalTime.parse("09:40"), LocalTime.parse("09:50"), LocalTime.parse("10:00"), LocalTime.parse("10:10"), LocalTime.parse("10:20"));
        assertThat(inicios).contains(LocalTime.parse("09:30"), LocalTime.parse("10:30"), LocalTime.parse("15:30"), LocalTime.parse("17:00"));
        assertThat(inicios.stream().filter(t -> t.isBefore(LocalTime.NOON.plusHours(1))).toList()).last().isEqualTo(LocalTime.parse("12:30"));
        assertThat(inicios).noneMatch(t -> !t.isBefore(LocalTime.parse("13:00")) && t.isBefore(LocalTime.parse("14:00")));
        assertThat(inicios).noneMatch(t -> !t.isBefore(LocalTime.parse("15:40")) && !t.isAfter(LocalTime.parse("16:50")));
        assertThat(inicios).last().isEqualTo(LocalTime.parse("17:30"));
        assertThat(resultado).allSatisfy(f -> assertThat(f.fin()).isEqualTo(f.inicio().plusSeconds(1800)));
    }
    @Test void calcular_fechaPasadaYAhoraEstricto() {
        assertThat(calcular(30, instante("18:01"), horizonte)).isEmpty();
        var resultado = calcular(30, instante("11:00"), horizonte);
        assertThat(resultado).allSatisfy(f -> assertThat(f.inicio()).isAfter(instante("11:00")));
        assertThat(resultado).first().extracting(Franja::inicio).isEqualTo(instante("11:10"));
    }
    @Test void calcular_horizonteIncluyeElLimiteDeInicioAunqueFinSeaPosterior() {
        assertThat(calcular(30, ahora, instante("09:20"))).extracting(Franja::inicio)
                .containsExactly(instante("09:00"), instante("09:10"), instante("09:20"));
        assertThat(calcular(30, ahora, instante("08:59"))).isEmpty();
    }
    @Test void calcular_servicioLargoNoCruzaElDescanso() {
        // 180 min sí caben en 4 h sin ocupaciones; no caben en dos tramos de 2 h.
        var partida = List.of(jornada("09:00", "11:00"), jornada("12:00", "14:00"));
        assertThat(calculadora.calcular(fecha, partida, List.of(), 180, ahora, horizonte, 10)).isEmpty();
        assertThat(calculadora.calcular(fecha, jornadas, List.of(), 180, ahora, horizonte, 10)).hasSize(14);
        assertThat(calcular(180, ahora, horizonte)).isEmpty();
    }
    @Test void calcular_ordenDeEntradaNoAfectaSalidaYRejillaParteDeLaJornada() {
        var desordenada = List.of(jornada("14:03", "15:03"), jornada("09:03", "10:03"));
        var resultado = calculadora.calcular(fecha, desordenada, List.of(), 30, ahora, horizonte, 10);
        assertThat(resultado).extracting(Franja::inicio).containsExactly(instante("09:03"), instante("09:13"), instante("09:23"), instante("09:33"), instante("14:03"), instante("14:13"), instante("14:23"), instante("14:33"));
        assertThat(calculadora.calcular(fecha, List.of(), List.of(), 30, ahora, horizonte, 10)).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> resultado.add(franja("09:00", "09:30")));
    }
    @Test void calcular_exclusionYEstadosSeFiltranPorElLlamador() {
        var sinReserva = calculadora.calcular(fecha, jornadas, List.of(ocupaciones.get(1)), 30, ahora, horizonte, 10);
        assertThat(sinReserva).contains(franja("10:00", "10:30"));
        assertThat(calcular(30, ahora, horizonte)).doesNotContain(franja("10:00", "10:30"));
    }
    @ParameterizedTest @ValueSource(ints = {1, 30, 180, 250})
    void esFranjaValida_coincideConListaParaLaRejillaDelDia(int duracion) {
        var resultado = calcular(duracion, instante("09:20"), instante("17:20"));
        for (int minuto = 0; minuto < 1440; minuto += 10) {
            Instant inicio = fecha.atTime(LocalTime.MIDNIGHT.plusMinutes(minuto)).atZone(TiempoNegocio.ZONA).toInstant();
            Franja candidata = new Franja(inicio, inicio.plusSeconds(duracion * 60L));
            assertThat(calculadora.esFranjaValida(fecha, jornadas, ocupaciones, candidata, duracion,
                    instante("09:20"), instante("17:20"), 10)).as("duración %s, minuto %s", duracion, minuto).isEqualTo(resultado.contains(candidata));
        }
    }
    @Test void esFranjaValida_rechazaDuracionDistintaYDesalineacionSubminuto() {
        assertThat(valida(franja("11:00", "11:20"))).isFalse();
        assertThat(valida(franja("11:01", "11:31"))).isFalse();
        assertThat(valida(franja("11:00:01", "11:30:01"))).isFalse();
        assertThat(valida(new Franja(instante("11:00").plusNanos(1), instante("11:30").plusNanos(1)))).isFalse();
    }
    @Test void entradas_invalidasRechazadas() {
        assertThatIllegalArgumentException().isThrownBy(() -> calcular(0, ahora, horizonte));
        assertThatIllegalArgumentException().isThrownBy(() -> calculadora.calcular(fecha, jornadas, ocupaciones, 30, ahora, horizonte, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> calculadora.calcular(fecha, jornadas, ocupaciones, 30, ahora, horizonte, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> jornada("13:00", "09:00"));
        assertThatIllegalArgumentException().isThrownBy(() -> jornada("09:00", "09:00"));
        assertThatIllegalArgumentException().isThrownBy(() -> franja("10:30", "10:00"));
        assertThatNullPointerException().isThrownBy(() -> new IntervaloJornada(null, LocalTime.NOON));
        assertThatNullPointerException().isThrownBy(() -> new IntervaloJornada(LocalTime.NOON, null));
        assertThatNullPointerException().isThrownBy(() -> new Franja(null, ahora));
        assertThatNullPointerException().isThrownBy(() -> new Franja(ahora, null));
        assertThatNullPointerException().isThrownBy(() -> calculadora.calcular(null, jornadas, ocupaciones, 30, ahora, horizonte, 10));
        assertThatNullPointerException().isThrownBy(() -> calculadora.calcular(fecha, null, ocupaciones, 30, ahora, horizonte, 10));
        assertThatNullPointerException().isThrownBy(() -> calculadora.calcular(fecha, jornadas, null, 30, ahora, horizonte, 10));
        assertThatNullPointerException().isThrownBy(() -> calcular(30, null, horizonte));
        assertThatNullPointerException().isThrownBy(() -> calcular(30, ahora, null));
        assertThatNullPointerException().isThrownBy(() -> valida(null));
    }
    private boolean valida(Franja franja) {
        return calculadora.esFranjaValida(fecha, jornadas, ocupaciones, franja, 30, ahora, horizonte, 10);
    }
    private List<Franja> calcular(int duracion, Instant actual, Instant limite) {
        return calculadora.calcular(fecha, jornadas, ocupaciones, duracion, actual, limite, 10);
    }
    private IntervaloJornada jornada(String inicio, String fin) {
        return new IntervaloJornada(LocalTime.parse(inicio), LocalTime.parse(fin));
    }
    private Instant instante(String hora) {
        return fecha.atTime(LocalTime.parse(hora)).atZone(TiempoNegocio.ZONA).toInstant();
    }
    private Franja franja(String inicio, String fin) { return new Franja(instante(inicio), instante(fin)); }
}
