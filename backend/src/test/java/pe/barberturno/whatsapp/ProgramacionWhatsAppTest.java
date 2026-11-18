package pe.barberturno.whatsapp;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** El intervalo de application.yml («1500ms») se interpreta con @EnableScheduling real; las IT desactivan el programador. */
class ProgramacionWhatsAppTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Programacion { }

    @Test void intervaloDeLaConfiguracion_seProgramaComoRetrasoFijo() {
        new ApplicationContextRunner()
                .withUserConfiguration(Programacion.class, ProgramadorEventosWhatsApp.class)
                .withBean(ProcesadorEventosWhatsApp.class, () -> mock(ProcesadorEventosWhatsApp.class))
                .withPropertyValues("barberturno.whatsapp.procesador.intervalo=1500ms")
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThat(contexto.getBean(ScheduledTaskHolder.class).getScheduledTasks()).singleElement()
                            .satisfies(tarea -> assertThat(((FixedDelayTask) tarea.getTask()).getIntervalDuration())
                                    .isEqualTo(Duration.ofMillis(1500)));
                });
    }
}
