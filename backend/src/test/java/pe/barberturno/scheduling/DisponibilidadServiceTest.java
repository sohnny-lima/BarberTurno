package pe.barberturno.scheduling;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.reservations.ReservaRepository;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Comprueba que la ocultación de reservas no convierte otros errores de identidad en ausencia de sesión. */
class DisponibilidadServiceTest {
    @Test void exclusion_identidadConErrorDistintoDeSesion_conservaErrorSinLeerDatos() {
        var actual = mock(UsuarioActual.class);
        var reservas = mock(ReservaRepository.class);
        var servicios = mock(ServicioRepository.class);
        var error = new NegocioException(ErrorCodigo.PROHIBIDO, "Identidad sin permiso.");
        when(actual.rol()).thenThrow(error);
        var servicio = new DisponibilidadService(servicios, mock(BarberoRepository.class),
                mock(JornadaRepository.class), mock(BloqueoRepository.class), reservas,
                Clock.fixed(Instant.parse("2026-09-28T14:00:00Z"), ZoneOffset.UTC),
                new ParametrosReserva(Duration.ofHours(2), 30, 10, 3, false, 15), actual);
        assertThatThrownBy(() -> servicio.consultarFranjas(1, Optional.empty(), LocalDate.of(2026, 10, 1),
                Optional.of(100L))).isSameAs(error);
        verifyNoInteractions(reservas, servicios);
    }
}
