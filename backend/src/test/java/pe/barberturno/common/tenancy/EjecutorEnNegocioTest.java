package pe.barberturno.common.tenancy;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EjecutorEnNegocioTest {
    final ContextoNegocio contexto = new ContextoNegocio();
    final DirectorioNegocios directorio = mock(DirectorioNegocios.class);
    final EjecutorEnNegocio ejecutor = new EjecutorEnNegocio(contexto, directorio);

    @Test void slugInexistente_fallaSinEjecutarLaAccionNiElegirOtro() {
        when(directorio.buscar("inexistente")).thenReturn(OptionalLong.empty());
        assertThatThrownBy(() -> ejecutor.ejecutar("inexistente", () -> fail("No debe ejecutar")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No existe el negocio configurado");
        assertThat(contexto.delimitado()).isFalse();
    }

    @Test void ambitosAnidados_restauranElAnteriorInclusoConError() {
        when(directorio.buscar("a")).thenReturn(OptionalLong.of(1));
        when(directorio.buscar("b")).thenReturn(OptionalLong.of(2));
        ejecutor.ejecutar("a", () -> {
            assertThatThrownBy(() -> ejecutor.ejecutar("b", () -> {
                assertThat(contexto.negocioActual()).hasValue(2);
                throw new IllegalStateException("Fallo ficticio");
            })).hasMessage("Fallo ficticio");
            assertThat(contexto.negocioActual()).hasValue(1);
            return null;
        });
        assertThat(contexto.delimitado()).isFalse();
        contexto.fijar(OptionalLong.empty());
        try {
            ejecutor.ejecutar("a", () -> null);
            assertThat(contexto.delimitado()).isTrue();
            assertThat(contexto.negocioActual()).isEmpty();
        } finally { contexto.limpiar(); }
    }

    @Test void transaccionAbierta_impideCambiarElAmbitoAntesDeBuscarElSlug() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> ejecutor.ejecutar("b", () -> null))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("antes de abrir la transacción");
            verifyNoInteractions(directorio);
            assertThat(contexto.delimitado()).isFalse();
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
