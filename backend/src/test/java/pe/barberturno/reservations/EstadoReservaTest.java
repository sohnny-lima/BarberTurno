package pe.barberturno.reservations;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

class EstadoReservaTest {
    static boolean permitida(EstadoReserva origen, EstadoReserva destino) {
        return switch (origen) {
            case PENDIENTE -> Set.of(EstadoReserva.CONFIRMADA, EstadoReserva.CANCELADA).contains(destino);
            case CONFIRMADA -> Set.of(EstadoReserva.EN_ATENCION, EstadoReserva.CANCELADA, EstadoReserva.NO_ASISTIO).contains(destino);
            case EN_ATENCION -> destino == EstadoReserva.COMPLETADA;
            default -> false;
        };
    }
    static Stream<Arguments> matriz() {
        return Stream.of(EstadoReserva.values()).flatMap(origen -> Stream.of(EstadoReserva.values()).map(destino -> Arguments.of(origen, destino)));
    }
    @ParameterizedTest @MethodSource("matriz")
    void transiciones_matrizCompleta(EstadoReserva origen, EstadoReserva destino) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(permitida(origen, destino));
        assertThat(origen.transicionesPermitidas().contains(destino)).isEqualTo(permitida(origen, destino));
    }
    @ParameterizedTest @EnumSource(EstadoReserva.class)
    void estado_ocupacionYTerminales(EstadoReserva estado) {
        assertThat(estado.ocupaFranja()).isEqualTo(estado != EstadoReserva.CANCELADA);
        assertThat(estado.esTerminal()).isEqualTo(Set.of(EstadoReserva.COMPLETADA, EstadoReserva.CANCELADA, EstadoReserva.NO_ASISTIO).contains(estado));
        assertThat(estado.puedePasarA(null)).isFalse();
    }
    @Test void transiciones_noPermiteModificarElConjunto() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> EstadoReserva.PENDIENTE.transicionesPermitidas().add(EstadoReserva.COMPLETADA));
    }
}
