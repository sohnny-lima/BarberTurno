package pe.barberturno.whatsapp;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import static org.assertj.core.api.Assertions.*;

class EventoEntranteRepositoryTest {
    @Test void superficie_soloLaInsercionIdempotenteSinCrudHeredado() {
        assertThat(EventoEntranteRepository.class.getInterfaces()).containsExactly(Repository.class);
        assertThat(Arrays.stream(EventoEntranteRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .containsExactly("insertarSiNoExiste");
    }
}
