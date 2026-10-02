package pe.barberturno.scheduling;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BloqueoServiceTest {
    private final BloqueoRepository bloqueos = mock(BloqueoRepository.class);
    private final BarberoRepository barberos = mock(BarberoRepository.class);
    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final BloqueoService servicio = new BloqueoService(bloqueos, barberos, reservas, usuarios,
            Clock.fixed(Instant.parse("2026-09-28T14:00:00Z"), ZoneOffset.UTC));

    @Test void listar_clienteInclusoConPerfil_noConsultaDatosAjenos() {
        var cliente = new UsuarioAutenticado(2, Rol.CLIENTE, Optional.of(1L), false);
        assertThatThrownBy(() -> servicio.listar(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), cliente))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
        verifyNoInteractions(bloqueos, barberos, reservas, usuarios);
    }

    @Test void eliminar_desapareceMientrasEsperaPorPerfil_reconsultaYDevuelve404() {
        when(bloqueos.buscarBarberoId(1L)).thenReturn(Optional.of(2L));
        when(bloqueos.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.eliminar(1))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
        var orden = inOrder(bloqueos, barberos);
        orden.verify(bloqueos).buscarBarberoId(1L);
        orden.verify(barberos).bloquearPorIds(List.of(2L));
        orden.verify(bloqueos).findById(1L);
        verify(bloqueos, never()).delete(any());
        verifyNoInteractions(reservas, usuarios);
    }
}
