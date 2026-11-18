package pe.barberturno.conversation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la retención de WhatsApp de forma periódica (V2-DA-67). Se desactiva con
 * {@code barberturno.whatsapp.conversacion.retencion.habilitada=false} (las pruebas la llaman directamente).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@ConditionalOnProperty(prefix = "barberturno.whatsapp.conversacion.retencion", name = "habilitada",
        havingValue = "true", matchIfMissing = true)
class ProgramadorRetencionWhatsApp {
    private final RetencionWhatsApp retencion;

    ProgramadorRetencionWhatsApp(RetencionWhatsApp retencion) {
        this.retencion = retencion;
    }

    @Scheduled(fixedDelayString = "${barberturno.whatsapp.conversacion.retencion.intervalo:1h}",
            initialDelayString = "${barberturno.whatsapp.conversacion.retencion.intervalo:1h}")
    void ejecutar() {
        retencion.purgar();
    }

    // El texto temporal de IA no espera al intervalo horario de la purga general.
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void abandonarTurnosIA() {
        retencion.abandonarTurnosIA();
    }
}
