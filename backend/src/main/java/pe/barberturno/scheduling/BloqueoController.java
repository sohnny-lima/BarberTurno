package pe.barberturno.scheduling;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.scheduling.dto.*;

/**
 * Rutas RF-06/RF-20 con lecturas asignadas y escrituras ADMIN protegidas por CSRF.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
public class BloqueoController {
    private final BloqueoService bloqueos;

    /**
     * Delega RN-18 y el protocolo de disponibilidad al servicio transaccional.
     * @param bloqueos servicio de indisponibilidad y lotes atómicos
     */
    public BloqueoController(BloqueoService bloqueos) { this.bloqueos = bloqueos; }

    /**
     * GET para ADMIN o BARBERO asignado sobre un rango inclusivo en Lima.
     * @param id perfil solicitado
     * @param desde primer día yyyy-MM-dd
     * @param hasta último día yyyy-MM-dd
     * @param actual identidad vigente de la sesión
     * @return bloqueos ordenados que se cruzan con el rango
     * @throws NegocioException si el perfil no existe o es ajeno (404) o el rango es inválido (400)
     */
    @GetMapping("/api/barberos/{id}/bloqueos")
    public List<BloqueoDto> listar(@PathVariable long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @AuthenticationPrincipal UsuarioAutenticado actual) {
        return bloqueos.listar(id, desde, hasta, actual);
    }

    /**
     * POST ADMIN de un intervalo sin reservas ocupantes; registra el actor autenticado.
     * @param id perfil a bloquear
     * @param solicitud intervalo con desfase y motivo validado
     * @param actual actor autenticado creador del bloqueo
     * @return bloqueo creado, HTTP 201
     * @throws NegocioException si el intervalo es inválido (400), falta el perfil (404),
     * empieza en el pasado (422) o cruza reservas (409)
     */
    @PostMapping("/api/barberos/{id}/bloqueos")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueoDto crear(@PathVariable long id, @Valid @RequestBody CrearBloqueoDto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actual) {
        return bloqueos.crear(id, solicitud, actual);
    }

    /**
     * POST ADMIN atómico: si un perfil tiene conflictos no persiste ningún bloqueo RF-20.
     * @param solicitud selección de perfiles distintos y un intervalo común validado
     * @param actual actor autenticado que crea el lote
     * @return bloqueos creados ordenados por perfil, HTTP 201
     * @throws NegocioException si hay duplicados o intervalo inválido (400), falta un perfil (404),
     * empieza en el pasado (422) o existen conflictos agrupados por perfil (409)
     */
    @PostMapping("/api/bloqueos/lote")
    @ResponseStatus(HttpStatus.CREATED)
    public List<BloqueoDto> crearLote(@Valid @RequestBody CrearBloqueosLoteDto solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actual) {
        return bloqueos.crearLote(solicitud, actual);
    }

    /**
     * DELETE ADMIN con borrado físico permitido por RN-16 y sin cuerpo de respuesta.
     * @param id identidad del bloqueo a eliminar
     * @throws NegocioException si el bloqueo no existe (404)
     */
    @DeleteMapping("/api/bloqueos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable long id) { bloqueos.eliminar(id); }
}
