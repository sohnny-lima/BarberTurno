package pe.barberturno.notifications;

import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pe.barberturno.audit.AccionAuditoria;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.notifications.dto.*;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.users.Usuario;

/**
 * Escritura atómica RN-15 y consultas y lectura RF-16 limitadas al usuario autenticado.
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
     * Pagina solo avisos propios, del más reciente al más antiguo, con desempate por identidad.
     * @param actor identidad autenticada y revalidada
     * @param soloNoLeidas true para excluir avisos leídos
     * @param pagina índice desde cero
     * @param tamano cantidad entre 1 y 100
     * @return página RF-16 con fechas de Lima
     * @throws NegocioException VALIDACION si índice o tamaño son inválidos
     */
    @Transactional(readOnly = true)
    public PaginaDto<NotificacionDto> listar(UsuarioAutenticado actor, boolean soloNoLeidas, int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new NegocioException(ErrorCodigo.VALIDACION,
                    "La página debe ser al menos 0 y el tamaño debe estar entre 1 y 100.");
        }
        var resultado = notificaciones.listarPropias(actor.id(), soloNoLeidas,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn", "id")));
        return PaginaDto.desde(resultado.map(NotificacionDto::desde));
    }

    /**
     * Obtiene el contador propio mediante COUNT, sin cargar avisos.
     * @param actor identidad autenticada y revalidada
     * @return número de avisos propios no leídos
     */
    @Transactional(readOnly = true)
    public ConteoNotificacionesDto conteo(UsuarioAutenticado actor) {
        return new ConteoNotificacionesDto(notificaciones.contarNoLeidas(actor.id()));
    }

    /**
     * Registra lectura propia de forma idempotente sin revelar avisos ajenos.
     * @param actor identidad autenticada del destinatario
     * @param id aviso solicitado
     * @throws NegocioException NO_ENCONTRADO si no existe o pertenece a otro destinatario
     */
    @Transactional
    public void marcarLeida(UsuarioAutenticado actor, long id) {
        var aviso = notificaciones.findByIdAndUsuarioId(id, actor.id()).orElseThrow(() ->
                new NegocioException(ErrorCodigo.NO_ENCONTRADO, "El aviso no existe."));
        aviso.marcarLeida();
    }

    /**
     * Marca todos los avisos propios mediante una actualización idempotente y acotada por destinatario.
     * @param actor identidad autenticada del destinatario
     */
    @Transactional
    public void marcarTodasLeidas(UsuarioAutenticado actor) {
        notificaciones.marcarTodasLeidas(actor.id());
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
