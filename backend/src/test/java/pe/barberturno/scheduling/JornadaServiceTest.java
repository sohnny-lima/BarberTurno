package pe.barberturno.scheduling;

import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.users.Rol;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JornadaServiceTest {
    private final JornadaRepository jornadas = mock(JornadaRepository.class);
    private final BarberoRepository barberos = mock(BarberoRepository.class);
    private final ReservaRepository reservas = mock(ReservaRepository.class);
    private final Validator validator = mock(Validator.class);
    private final JornadaService servicio = new JornadaService(jornadas, barberos, reservas, Clock.systemUTC(), validator);

    @Test void reemplazar_listaNula_rechazaAntesDeAccederAPersistencia() {
        assertThatThrownBy(() -> servicio.reemplazar(1, null)).isInstanceOfSatisfying(NegocioException.class, e -> {
            org.assertj.core.api.Assertions.assertThat(e.codigo()).isEqualTo(ErrorCodigo.JORNADA_INVALIDA);
            org.assertj.core.api.Assertions.assertThat(e.errores().getFirst().campo()).isEqualTo("semana");
        });
        verifyNoInteractions(jornadas, barberos, reservas, validator);
    }

    @Test void listar_clienteInclusoConPerfil_noConsultaDatosAjenos() {
        var cliente = new UsuarioAutenticado(2, Rol.CLIENTE, Optional.of(1L), false);
        assertThatThrownBy(() -> servicio.listar(1, cliente)).isInstanceOfSatisfying(NegocioException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
        verifyNoInteractions(jornadas, barberos, reservas);
    }
}
