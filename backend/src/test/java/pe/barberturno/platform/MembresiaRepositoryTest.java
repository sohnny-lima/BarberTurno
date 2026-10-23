package pe.barberturno.platform;

import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import pe.barberturno.common.tenancy.RepositorioTransaccional;
import static org.assertj.core.api.Assertions.*;

class MembresiaRepositoryTest {
    @Test void superficie_soloMarcaYOperacionesAcotadasSinCrudHeredado() {
        assertThat(MembresiaRepository.class.getInterfaces()).containsExactly(Repository.class);
        for (Class<?> prohibida : Set.of(JpaRepository.class, CrudRepository.class, RepositorioTransaccional.class,
                JpaSpecificationExecutor.class, QueryByExampleExecutor.class)) {
            assertThat(prohibida.isAssignableFrom(MembresiaRepository.class)).as(prohibida.getName()).isFalse();
        }
        for (var metodo : MembresiaRepository.class.getMethods()) {
            if (metodo.getName().equals("save")) {
                assertThat(metodo.getParameterTypes()).containsExactly(Membresia.class);
                assertThat(metodo.getReturnType()).isEqualTo(Membresia.class);
            } else {
                assertThat(Arrays.stream(metodo.getParameters()).anyMatch(p -> p.getName().equals("negocioId")))
                        .as(metodo.toString()).isTrue();
                if (metodo.getName().equals("existeActivaEnOtroNegocio")) {
                    assertThat(metodo.getReturnType()).isEqualTo(boolean.class);
                }
            }
        }
    }
}
