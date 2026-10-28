package pe.barberturno.whatsapp;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ColaEventosWhatsAppTest {
    @Test void superficie_soloReclamoBloqueoYTransicionesConFichaSinCrudHeredado() {
        assertThat(ColaEventosWhatsApp.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(ColaEventosWhatsApp.class.getInterfaces()).isEmpty();
        var operaciones = Arrays.stream(ColaEventosWhatsApp.class.getDeclaredMethods())
                .filter(m -> !Modifier.isPrivate(m.getModifiers())).toList();
        assertThat(operaciones).extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("reclamar", "bloquear", "descartar", "procesar", "fallar");
        for (var metodo : operaciones) {
            assertThat(Modifier.isPublic(metodo.getModifiers())).isFalse();
            if (!metodo.getName().equals("reclamar")) {
                assertThat(Arrays.stream(metodo.getParameters()).map(java.lang.reflect.Parameter::getName))
                        .contains("eventoId", "ficha");
            }
        }
    }
}
