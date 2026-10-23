package pe.barberturno.customers;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import static org.assertj.core.api.Assertions.*;

class VinculoClienteRepositoryTest {
    @Test void superficie_soloComprobacionBooleanaSinCrudNiFilasDePlataforma() {
        assertThat(VinculoClienteRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(VinculoClienteRepository.class.getMethods()).singleElement().satisfies(metodo -> {
            assertThat(metodo.getName()).isEqualTo("existeEnOtroNegocio");
            assertThat(metodo.getReturnType()).isEqualTo(boolean.class);
            assertThat(metodo.getParameterTypes()).containsExactly(long.class, long.class);
            assertThat(metodo.getParameters()).extracting(java.lang.reflect.Parameter::getName)
                    .containsExactly("usuarioId", "negocioId");
        });
    }
}
