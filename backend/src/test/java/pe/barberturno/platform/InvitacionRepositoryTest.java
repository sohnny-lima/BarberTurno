package pe.barberturno.platform;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import static org.assertj.core.api.Assertions.*;

class InvitacionRepositoryTest {
    @Test void superficie_cadaOperacionExigeNegocioOSuCapacidadSecretaSinCrudHeredado() throws Exception {
        assertThat(InvitacionRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(Arrays.stream(InvitacionRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .containsExactlyInAnyOrder("save", "findByTokenHash", "bloquearPorToken", "bloquearPorNegocio", "listarPendientes",
                        "findFirstByNegocioIdAndRolOrderByIdAsc");
        for (var metodo : InvitacionRepository.class.getMethods()) {
            switch (metodo.getName()) {
                case "save" -> assertThat(metodo.getParameterTypes()).containsExactly(Invitacion.class);
                case "findByTokenHash", "bloquearPorToken" -> {
                    assertThat(metodo.getParameterTypes()).containsExactly(byte[].class);
                    assertThat(metodo.getParameters()[0].getName()).isEqualTo("tokenHash");
                }
                default -> assertThat(Arrays.stream(metodo.getParameters()).anyMatch(p -> p.getName().equals("negocioId"))).isTrue();
            }
        }
        assertThat(InvitacionRepository.class.getMethod("bloquearPorToken", byte[].class)
                .getAnnotation(org.springframework.data.jpa.repository.Lock.class).value())
                .isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
}
