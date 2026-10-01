package pe.barberturno.common.error;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NegocioExceptionTest {
    @Test
    void construir_sinDetalles_conservaCodigoYMensaje() {
        NegocioException error = new NegocioException(ErrorCodigo.FUERA_DE_HORARIO, "Fuera de jornada.");
        assertThat(error.codigo()).isEqualTo(ErrorCodigo.FUERA_DE_HORARIO);
        assertThat(error.getMessage()).isEqualTo("Fuera de jornada.");
        assertThat(error.detalles()).isEmpty();
    }

    @Test
    void construir_conDetalles_copiaMapaSuperior() {
        Map<String, Object> mapa = new HashMap<>(Map.of("reservas", List.of(100L)));
        NegocioException error = new NegocioException(ErrorCodigo.CONFLICTO_CON_RESERVAS, "Hay reservas.", mapa);
        mapa.clear();
        assertThat(error.detalles()).containsEntry("reservas", List.of(100L));
        assertThatThrownBy(() -> error.detalles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "title", "status", "detail", "instance", "codigo", "errores"})
    void construir_extensionReservada_rechaza(String propiedad) {
        assertThatThrownBy(() -> new NegocioException(ErrorCodigo.CONFLICTO, "Conflicto.", Map.of(propiedad, "otro")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
