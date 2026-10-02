package pe.barberturno.scheduling;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.scheduling.dto.JornadaDto;

/**
 * Consulta asignada y reemplazo ADMIN de la semana RF-06, protegidos por la cadena de seguridad.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/barberos/{id}/jornadas")
public class JornadaController {
    private final JornadaService jornadas;

    /**
     * Delega validación RN-17 y transacciones al servicio de jornadas.
     * @param jornadas servicio de agenda semanal
     */
    public JornadaController(JornadaService jornadas) { this.jornadas = jornadas; }

    /**
     * GET para ADMIN o BARBERO asignado; oculta perfiles ajenos con 404.
     * @param id perfil solicitado, activo o inactivo
     * @param actual identidad revalidada por sesión
     * @return semana ordenada por día y hora, posiblemente vacía
     * @throws NegocioException si el perfil no existe o no corresponde al barbero (404)
     */
    @GetMapping
    public List<JornadaDto> listar(@PathVariable long id,
            @AuthenticationPrincipal UsuarioAutenticado actual) {
        return jornadas.listar(id, actual);
    }

    /**
     * PUT solo ADMIN con CSRF; sustituye toda la semana o la vacía de forma atómica.
     * La validación del servicio conserva JORNADA_INVALIDA y los índices del cuerpo.
     * @param id perfil cuya disponibilidad se configura
     * @param semana lista completa de intervalos con horas HH:mm
     * @return lista guardada ordenada por día y hora
     * @throws NegocioException si falta el perfil (404), la jornada es inválida (400)
     * o deja fuera alguna reserva futura que ocupa franja (409)
     */
    @PutMapping
    public List<JornadaDto> reemplazar(@PathVariable long id, @RequestBody List<JornadaDto> semana) {
        return jornadas.reemplazar(id, semana);
    }
}
