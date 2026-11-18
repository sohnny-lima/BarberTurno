package pe.barberturno.customers;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import static org.assertj.core.api.Assertions.*;

class VinculoClienteRepositoryTest {
    @Test void superficie_soloComprobacionBooleanaYNegociosPropiosActivosSinCrud() {
        assertThat(VinculoClienteRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(VinculoClienteRepository.class.getMethods()).hasSize(2).allSatisfy(metodo -> {
            if (metodo.getName().equals("listarNegociosPropiosActivos")) {
                assertThat(metodo.getReturnType()).isEqualTo(java.util.List.class);
                assertThat(metodo.getParameterTypes()).containsExactly(long.class);
                assertThat(metodo.getParameters()).extracting(java.lang.reflect.Parameter::getName).containsExactly("usuarioId");
                assertThat(metodo.getAnnotation(org.springframework.data.jpa.repository.Query.class).value())
                        .isEqualTo("select negocio_id from vinculo_cliente where usuario_id = :usuarioId and activo");
                return;
            }
            assertThat(metodo.getName()).isEqualTo("existeEnOtroNegocio");
            assertThat(metodo.getReturnType()).isEqualTo(boolean.class);
            assertThat(metodo.getParameterTypes()).containsExactly(long.class, long.class);
            assertThat(metodo.getParameters()).extracting(java.lang.reflect.Parameter::getName)
                    .containsExactly("usuarioId", "negocioId");
        });
    }
}
