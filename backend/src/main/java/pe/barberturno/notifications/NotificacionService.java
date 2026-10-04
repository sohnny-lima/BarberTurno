package pe.barberturno.notifications;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pe.barberturno.audit.AccionAuditoria;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.users.Usuario;

/**
 * Escritura de avisos internos RN-15 en la transacción del cambio; sin dependencia de ReservaService.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class NotificacionService {
    private final NotificacionRepository notificaciones;
    private final Clock clock;

    /**
     * Recibe persistencia y reloj, sin canal externo de envío.
     * @param notificaciones repositorio de avisos internos
     * @param clock reloj inyectado del servidor
     */
    public NotificacionService(NotificacionRepository notificaciones, Clock clock) {
        this.notificaciones = notificaciones;
        this.clock = clock;
    }

    /**
     * Escribe un aviso no leído al destinatario elegido por RN-15, propagando fallos.
     * @param usuario destinatario del aviso
     * @param reserva reserva persistida asociada
     * @param tipo acción que origina el aviso
     * @param mensaje texto español de hasta 300 caracteres, sin secretos
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notificar(Usuario usuario, Reserva reserva, AccionAuditoria tipo, String mensaje) {
        notificaciones.save(new Notificacion(usuario, reserva, tipo, mensaje, clock.instant()));
    }
}
