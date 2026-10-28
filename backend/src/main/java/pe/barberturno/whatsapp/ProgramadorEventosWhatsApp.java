package pe.barberturno.whatsapp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Activa únicamente el disparo periódico; el procesador sigue disponible cuando se desactiva.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@ConditionalOnProperty(prefix = "barberturno.whatsapp.procesador", name = "habilitado", havingValue = "true",
        matchIfMissing = true)
class ProgramadorEventosWhatsApp {
    private final ProcesadorEventosWhatsApp procesador;

    ProgramadorEventosWhatsApp(ProcesadorEventosWhatsApp procesador) { this.procesador = procesador; }

    @Scheduled(fixedDelayString = "${barberturno.whatsapp.procesador.intervalo:1500ms}")
    void procesar() { procesador.procesarLote(); }
}
