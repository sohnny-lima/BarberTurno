package pe.barberturno.reservations;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import static org.assertj.core.api.Assertions.*;

class ReservaTest {
    private final Instant ahora = Instant.parse("2026-09-28T14:00:00Z");
    private final Usuario cliente = new Usuario("Cliente ficticio", "cliente@ejemplo.test", "999000001", "hash-ficticio", Rol.CLIENTE, ahora, ahora);
    private final Barbero barbero = new Barbero(new Usuario("Barbero ficticio", "barbero@ejemplo.test", null, "hash-ficticio", Rol.BARBERO, null, ahora), "Cortes", ahora);
    private final Servicio servicio = new Servicio("Corte", "Ficticio", (short) 30, new BigDecimal("20.00"), ahora);

    @ParameterizedTest @MethodSource("pe.barberturno.reservations.EstadoReservaTest#matriz")
    void cambiarEstado_matrizCompletaConservaDatosAnteRechazo(EstadoReserva origen, EstadoReserva destino) {
        Reserva reserva = reserva(origen);
        if (EstadoReservaTest.permitida(origen, destino)) {
            reserva.cambiarEstado(destino, ahora.plusSeconds(1));
            assertThat(reserva.getEstado()).isEqualTo(destino);
            assertThat(reserva.getActualizadoEn()).isEqualTo(ahora.plusSeconds(1));
        } else {
            assertThatIllegalStateException().isThrownBy(() -> reserva.cambiarEstado(destino, ahora.plusSeconds(1)));
            assertThat(reserva.getEstado()).isEqualTo(origen);
            assertThat(reserva.getActualizadoEn()).isEqualTo(ahora);
        }
    }
    @Test void cambiarEstado_nulosNoModificanLaEntidad() {
        Reserva reserva = reserva(EstadoReserva.CONFIRMADA);
        assertThatNullPointerException().isThrownBy(() -> reserva.cambiarEstado(null, ahora));
        assertThatNullPointerException().isThrownBy(() -> reserva.cambiarEstado(EstadoReserva.CANCELADA, null));
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reserva.getActualizadoEn()).isEqualTo(ahora);
    }
    @Test void reprogramar_conservaReferenciasAcordadas() {
        Reserva reserva = reserva(EstadoReserva.CONFIRMADA);
        servicio.editar("Nuevo", "Ficticio", (short) 60, new BigDecimal("50.00"), ahora.plusSeconds(1));
        reserva.reprogramar(barbero, ahora.plusSeconds(3600), ahora.plusSeconds(2));
        assertThat(reserva.getFin()).isEqualTo(ahora.plusSeconds(5400));
        assertThat(reserva.getDuracionRefMin()).isEqualTo((short) 30);
        assertThat(reserva.getPrecioRef()).isEqualByComparingTo("20.00");
    }
    private Reserva reserva(EstadoReserva estado) {
        return new Reserva(cliente, barbero, servicio, ahora.plusSeconds(7200), estado, cliente, ahora);
    }
}
