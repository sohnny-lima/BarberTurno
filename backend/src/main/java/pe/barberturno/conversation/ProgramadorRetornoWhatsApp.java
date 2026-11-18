package pe.barberturno.conversation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el retorno automático sin transacción exterior; el servicio separa los negocios.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@ConditionalOnProperty(prefix = "barberturno.whatsapp.bandeja", name = "habilitado",
        havingValue = "true", matchIfMissing = true)
class ProgramadorRetornoWhatsApp {
    private final DerivacionConversacion derivacion;

    ProgramadorRetornoWhatsApp(DerivacionConversacion derivacion) { this.derivacion = derivacion; }

    @Scheduled(fixedDelayString = "${barberturno.whatsapp.bandeja.intervalo:5m}",
            initialDelayString = "${barberturno.whatsapp.bandeja.intervalo:5m}")
    void ejecutar() { derivacion.devolverInactivas(); }
}
