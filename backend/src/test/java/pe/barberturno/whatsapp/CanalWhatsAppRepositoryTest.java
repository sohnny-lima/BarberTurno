package pe.barberturno.whatsapp;

import jakarta.persistence.LockModeType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;
import static org.assertj.core.api.Assertions.*;

class CanalWhatsAppRepositoryTest {
    @Test void superficie_cadaOperacionExigeNegocioOElNumeroReceptorSinCrudHeredado() throws Exception {
        assertThat(CanalWhatsAppRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(Arrays.stream(CanalWhatsAppRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .containsExactlyInAnyOrder("save", "findByPhoneNumberId", "findByNegocioId", "bloquearPorNegocio", "listarNegociosConCanal");
        for (var metodo : CanalWhatsAppRepository.class.getMethods()) {
            switch (metodo.getName()) {
                case "save" -> assertThat(metodo.getParameterTypes()).containsExactly(CanalWhatsApp.class);
                case "findByPhoneNumberId" -> assertThat(metodo.getParameters()[0].getName()).isEqualTo("phoneNumberId");
                case "listarNegociosConCanal" -> {
                    assertThat(metodo.getParameterCount()).isZero();
                    assertThat(metodo.getReturnType()).isEqualTo(List.class);
                }
                default -> assertThat(Arrays.stream(metodo.getParameters()).anyMatch(p -> p.getName().equals("negocioId")))
                        .as("El método %s debe exigir negocioId", metodo.getName()).isTrue();
            }
        }
        assertThat(CanalWhatsAppRepository.class.getMethod("bloquearPorNegocio", long.class)
                .getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
