package pe.barberturno.whatsapp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparo periódico opcional de la salida; desactivarlo no elimina el puerto de envío manual.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@ConditionalOnProperty(prefix = "barberturno.whatsapp.salida", name = "habilitado", havingValue = "true",
        matchIfMissing = true)
class ProgramadorSalidaWhatsApp {
    private final EnviadorSalidaWhatsApp enviador;

    ProgramadorSalidaWhatsApp(EnviadorSalidaWhatsApp enviador) { this.enviador = enviador; }

    @Scheduled(fixedDelayString = "${barberturno.whatsapp.salida.intervalo:2s}")
    void enviar() { enviador.enviarLote(); }
}
