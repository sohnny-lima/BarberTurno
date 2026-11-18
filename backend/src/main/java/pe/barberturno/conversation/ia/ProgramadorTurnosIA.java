package pe.barberturno.conversation.ia;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparo periódico opcional de la cola, sin transacción exterior ni llamadas directas al proveedor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@ConditionalOnProperty(prefix = "barberturno.ia.trabajador", name = "habilitado", havingValue = "true", matchIfMissing = true)
class ProgramadorTurnosIA {
    private final TurnosIA turnos;
    ProgramadorTurnosIA(TurnosIA turnos) { this.turnos = turnos; }
    @Scheduled(fixedDelayString = "${barberturno.ia.trabajador.intervalo:1s}")
    void procesar() { turnos.procesarPendientes(); }
}
