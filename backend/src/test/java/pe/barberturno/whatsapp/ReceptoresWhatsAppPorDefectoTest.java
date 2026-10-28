package pe.barberturno.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceptoresWhatsAppPorDefectoTest {
    final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ReceptoresWhatsAppPorDefecto.class, ProgramadorEventosWhatsApp.class)
            .withBean(ProcesadorEventosWhatsApp.class, () -> mock(ProcesadorEventosWhatsApp.class));

    @Test void sinAdaptadores_registraAmbosPuertosYProgramaElProcesador() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(ReceptorMensajesWhatsApp.class).hasSingleBean(ReceptorEstadosWhatsApp.class)
                    .hasSingleBean(ProgramadorEventosWhatsApp.class);
            ctx.getBean(ProgramadorEventosWhatsApp.class).procesar();
            verify(ctx.getBean(ProcesadorEventosWhatsApp.class)).procesarLote();
        });
    }

    @Test void deshabilitado_soloRetiraElProgramador_yRespetaLosAdaptadoresInstalados() {
        var mensajes = mock(ReceptorMensajesWhatsApp.class);
        var estados = mock(ReceptorEstadosWhatsApp.class);
        contexto.withPropertyValues("barberturno.whatsapp.procesador.habilitado=false")
                .withBean(ReceptorMensajesWhatsApp.class, () -> mensajes)
                .withBean(ReceptorEstadosWhatsApp.class, () -> estados).run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(ProgramadorEventosWhatsApp.class)
                            .hasSingleBean(ProcesadorEventosWhatsApp.class)
                            .hasSingleBean(ReceptorMensajesWhatsApp.class).hasSingleBean(ReceptorEstadosWhatsApp.class);
                    assertThat(ctx.getBean(ReceptorMensajesWhatsApp.class)).isSameAs(mensajes);
                    assertThat(ctx.getBean(ReceptorEstadosWhatsApp.class)).isSameAs(estados);
                });
    }
}
