package pe.barberturno.audit;

import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pe.barberturno.reservations.*;
import pe.barberturno.users.Usuario;

/**
 * Escritura del historial RN-15; exige la transacción del cambio, sin depender de ReservaService.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class AuditoriaService {
    private final AuditoriaRepository auditorias;
    private final Clock clock;

    /**
     * Recibe persistencia y reloj para sellar cada cambio.
     * @param auditorias repositorio del historial
     * @param clock reloj inyectado del servidor
     */
    public AuditoriaService(AuditoriaRepository auditorias, Clock clock) {
        this.auditorias = auditorias;
        this.clock = clock;
    }

    /**
     * Inserta el historial en la transacción existente, propagando cualquier fallo para RN-15.
     * @param reserva reserva modificada y persistida
     * @param actor usuario que ejecuta el cambio
     * @param accion acción registrada
     * @param estadoAnterior estado previo o nulo al crear
     * @param datosAnteriores instantánea anterior o nula al crear
     * @param datosNuevos instantánea JSON del cambio
     * @param motivo justificación opcional
     * @param excepcional excepción administrativa RN-08
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarCambio(Reserva reserva, Usuario actor, AccionAuditoria accion,
            EstadoReserva estadoAnterior, Map<String, Object> datosAnteriores,
            Map<String, Object> datosNuevos, String motivo, boolean excepcional) {
        auditorias.save(new AuditoriaReserva(reserva, actor, accion, estadoAnterior,
                reserva.getEstado(), datosAnteriores, datosNuevos, motivo, excepcional, clock.instant()));
    }
}
