package pe.barberturno.whatsapp;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import static org.assertj.core.api.Assertions.*;

class CanalWhatsAppRepositoryTest {
    @Test void superficie_cadaOperacionExigeNegocioOElNumeroReceptorSinCrudHeredado() throws Exception {
        assertThat(CanalWhatsAppRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(Arrays.stream(CanalWhatsAppRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .contains("save", "findByPhoneNumberId", "findByNegocioId", "bloquearPorNegocio");
        for (var metodo : CanalWhatsAppRepository.class.getMethods()) {
            switch (metodo.getName()) {
                case "save" -> assertThat(metodo.getParameterTypes()).containsExactly(CanalWhatsApp.class);
                case "findByPhoneNumberId" -> assertThat(metodo.getParameters()[0].getName()).isEqualTo("phoneNumberId");
                default -> {
                    if (!metodo.getReturnType().equals(java.util.List.class)) {
                        assertThat(Arrays.stream(metodo.getParameters()).anyMatch(p -> p.getName().equals("negocioId"))).isTrue();
                    }
                }
            }
        }
        assertThat(CanalWhatsAppRepository.class.getMethod("bloquearPorNegocio", long.class)
                .getAnnotation(org.springframework.data.jpa.repository.Lock.class).value())
                .isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
}
