package pe.barberturno.reservations;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pe.barberturno.users.Rol;
import static pe.barberturno.reservations.EstadoReserva.*;
import static pe.barberturno.reservations.PoliticaTransiciones.Resultado.*;
import static org.assertj.core.api.Assertions.*;

class PoliticaTransicionesTest {
    private final PoliticaTransiciones politica = new PoliticaTransiciones();
    private final Instant inicio = Instant.parse("2026-10-01T15:00:00Z");
    static Stream<Arguments> matrizActores() {
        return Stream.of(Rol.values()).flatMap(rol -> Stream.of(false, true).flatMap(asignado ->
                EstadoReservaTest.matriz().map(par -> Arguments.of(rol, asignado, par.get()[0], par.get()[1]))));
    }
    @ParameterizedTest @MethodSource("matrizActores")
    void evaluar_matrizDeEstadosPorRol(Rol rol, boolean asignado, EstadoReserva actual, EstadoReserva destino) {
        var esperado = !EstadoReservaTest.permitida(actual, destino) ? TRANSICION_INVALIDA
                : (destino == CANCELADA ? rol == Rol.BARBERO : rol != Rol.ADMIN && !(rol == Rol.BARBERO && asignado)) ? PROHIBIDO : PERMITIDA;
        Instant ahora = destino == CANCELADA ? inicio.minusSeconds(7200) : inicio;
        var resultado = evaluar(rol, asignado, actual, destino, ahora, "Solicitud");
        assertThat(resultado).isEqualTo(esperado);
        assertThat(resultado.permitida()).isEqualTo(esperado == PERMITIDA);
    }
    static Stream<Arguments> ventanas() {
        return Stream.of(Rol.ADMIN, Rol.BARBERO).flatMap(rol -> Stream.of(
                Arguments.of(rol, EN_ATENCION, -960, FUERA_DE_VENTANA), Arguments.of(rol, EN_ATENCION, -900, PERMITIDA),
                Arguments.of(rol, EN_ATENCION, -899, PERMITIDA), Arguments.of(rol, NO_ASISTIO, -1, FUERA_DE_VENTANA),
                Arguments.of(rol, NO_ASISTIO, 0, PERMITIDA), Arguments.of(rol, NO_ASISTIO, 1, PERMITIDA)));
    }
    @ParameterizedTest @MethodSource("ventanas")
    void evaluar_limitesTemporales(Rol rol, EstadoReserva destino, int segundos, PoliticaTransiciones.Resultado esperado) {
        assertThat(evaluar(rol, true, CONFIRMADA, destino, inicio.plusSeconds(segundos), null)).isEqualTo(esperado);
    }
    @Test void cancelar_aplicaLaPoliticaTemporalYElMotivo() {
        assertThat(evaluar(Rol.CLIENTE, false, CONFIRMADA, CANCELADA, inicio.minusSeconds(7199), null)).isEqualTo(FUERA_DE_VENTANA);
        assertThat(evaluar(Rol.ADMIN, false, CONFIRMADA, CANCELADA, inicio.minusSeconds(1), null)).isEqualTo(FUERA_DE_VENTANA);
        assertThat(evaluar(Rol.ADMIN, false, CONFIRMADA, CANCELADA, inicio, "Solicitud")).isEqualTo(FUERA_DE_VENTANA);
        assertThat(evaluar(Rol.ADMIN, false, CONFIRMADA, CANCELADA, inicio.minusSeconds(1), "Solicitud")).isEqualTo(PERMITIDA);
    }
    @Test void completarYConfirmar_noExigenOtraVentana() {
        assertThat(evaluar(Rol.ADMIN, false, EN_ATENCION, COMPLETADA, inicio.minusSeconds(900), null)).isEqualTo(PERMITIDA);
        assertThat(evaluar(Rol.ADMIN, false, PENDIENTE, CONFIRMADA, inicio.minusSeconds(86400), null)).isEqualTo(PERMITIDA);
    }
    @Test void evaluar_rechazaToleranciaNegativaYNulos() {
        assertThatIllegalArgumentException().isThrownBy(() -> politica.evaluar(Rol.ADMIN, false, CONFIRMADA, EN_ATENCION, inicio, inicio, Duration.ofSeconds(-1), Duration.ofHours(2), null));
        assertThatNullPointerException().isThrownBy(() -> evaluar(null, false, CONFIRMADA, EN_ATENCION, inicio, null));
        assertThatNullPointerException().isThrownBy(() -> evaluar(Rol.ADMIN, false, null, EN_ATENCION, inicio, null));
        assertThatNullPointerException().isThrownBy(() -> evaluar(Rol.ADMIN, false, CONFIRMADA, null, inicio, null));
        assertThatNullPointerException().isThrownBy(() -> evaluar(Rol.ADMIN, false, CONFIRMADA, EN_ATENCION, null, null));
        assertThatNullPointerException().isThrownBy(() -> politica.evaluar(Rol.ADMIN, false, CONFIRMADA, EN_ATENCION, inicio, null, Duration.ZERO, Duration.ZERO, null));
        assertThatNullPointerException().isThrownBy(() -> politica.evaluar(Rol.ADMIN, false, CONFIRMADA, EN_ATENCION, inicio, inicio, null, Duration.ZERO, null));
    }
    private PoliticaTransiciones.Resultado evaluar(Rol rol, boolean asignado, EstadoReserva actual, EstadoReserva destino, Instant ahora, String motivo) {
        return politica.evaluar(rol, asignado, actual, destino, ahora, inicio, Duration.ofMinutes(15), Duration.ofHours(2), motivo);
    }
}
