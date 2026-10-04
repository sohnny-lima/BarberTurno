package pe.barberturno.notifications;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.notifications.dto.*;

/**
 * API RF-16 de avisos propios para cualquier rol autenticado, con CSRF obligatorio al marcar lectura.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {
    private final NotificacionService notificaciones;

    /**
     * Delega consultas y lectura propia al servicio transaccional.
     * @param notificaciones servicio RF-16 que acota cada operación al destinatario
     */
    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    /**
     * Consulta los avisos del usuario actual con paginación §6.1 y filtro de lectura opcional.
     * @param actor identidad revalidada de la sesión
     * @param soloNoLeidas true para consultar solo pendientes de lectura
     * @param pagina índice desde cero, predeterminado 0
     * @param tamano cantidad entre 1 y 100, predeterminada 20
     * @param parametros valores originales para rechazar parámetros vacíos
     * @return HTTP 200 con página ordenada del más reciente al más antiguo
     * @throws NegocioException VALIDACION si los parámetros están vacíos o la página es inválida
     */
    @GetMapping
    public PaginaDto<NotificacionDto> listar(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano,
            @RequestParam Map<String, String> parametros) {
        for (String clave : new String[]{"soloNoLeidas", "pagina", "tamano"}) {
            if (parametros.containsKey(clave) && parametros.get(clave).isBlank()) {
                throw new NegocioException(ErrorCodigo.VALIDACION, "El parámetro " + clave + " no puede estar vacío.");
            }
        }
        return notificaciones.listar(actor, soloNoLeidas, pagina, tamano);
    }

    /**
     * Consulta el contador propio sin materializar los avisos.
     * @param actor identidad revalidada de la sesión
     * @return HTTP 200 con la cantidad de avisos propios no leídos
     */
    @GetMapping("/conteo")
    public ConteoNotificacionesDto conteo(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return notificaciones.conteo(actor);
    }

    /**
     * Marca un aviso propio con sesión y CSRF comprobados por seguridad.
     * @param actor identidad revalidada del destinatario
     * @param id identidad del aviso solicitado
     * @return HTTP 204 incluso si el aviso propio ya estaba leído
     * @throws NegocioException NO_ENCONTRADO si falta o pertenece a otro destinatario
     */
    @PostMapping("/{id}/lectura")
    public ResponseEntity<Void> marcarLeida(@AuthenticationPrincipal UsuarioAutenticado actor, @PathVariable long id) {
        notificaciones.marcarLeida(actor, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Marca todos los avisos propios con sesión y CSRF, sin modificar los de otros usuarios.
     * @param actor identidad revalidada del destinatario
     * @return HTTP 204 también cuando no hay avisos pendientes
     */
    @PostMapping("/lectura")
    public ResponseEntity<Void> marcarTodasLeidas(@AuthenticationPrincipal UsuarioAutenticado actor) {
        notificaciones.marcarTodasLeidas(actor);
        return ResponseEntity.noContent().build();
    }
}
