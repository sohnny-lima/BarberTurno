package pe.barberturno.reservations;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.reservations.dto.*;

/**
 * Rutas RF-08/10/11/13 con sesión, CSRF en escrituras y autorización en servidor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
public class ReservaController {
    private final ReservaService reservas;
    private final ReservaConsultaService consultas;

    /**
     * Delega creación, cancelación atómica y consultas autorizadas a los servicios transaccionales.
     * @param reservas servicio atómico de creación y cancelación
     * @param consultas búsqueda con permisos, filtros y proyección mínima
     */
    public ReservaController(ReservaService reservas, ReservaConsultaService consultas) {
        this.reservas = reservas;
        this.consultas = consultas;
    }

    /**
     * Crea una reserva propia y comunica su URI canónica mediante Location.
     * @param cmd cuerpo validado con recursos e instante con desfase
     * @param actor identidad CLIENTE revalidada por seguridad
     * @return HTTP 201 con ReservaDto y Location
     * @throws NegocioException si hay rechazo de permisos, recursos, disponibilidad o límite
     */
    @PostMapping("/api/reservas")
    public ResponseEntity<ReservaDto> crear(@Valid @RequestBody CrearReservaDto cmd,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        var reserva = reservas.crear(cmd, actor);
        return ResponseEntity.created(URI.create("/api/reservas/" + reserva.id())).body(reserva);
    }

    /**
     * Consulta el historial propio con días opcionales inclusivos de Lima.
     * @param actor identidad CLIENTE revalidada
     * @param estado filtro opcional
     * @param desde primer día opcional yyyy-MM-dd
     * @param hasta último día opcional yyyy-MM-dd
     * @param pagina índice desde cero, predeterminado 0
     * @param tamano tamaño 1 a 100, predeterminado 20
     * @param parametros valores HTTP originales para rechazar filtros presentes vacíos
     * @return página de reservas propias con permisos y sin teléfono
     * @throws NegocioException si hay parámetros inválidos o falta autorización
     */
    @GetMapping("/api/reservas/mias")
    public PaginaDto<ReservaDto> mias(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano,
            @RequestParam Map<String, String> parametros) {
        validarVacios(parametros);
        return consultas.mias(actor, estado, desde, hasta, pagina, tamano);
    }

    /**
     * Consulta la agenda del personal por un rango obligatorio de hasta 366 días inclusivos.
     * @param actor identidad BARBERO o ADMIN revalidada
     * @param desde primer día obligatorio yyyy-MM-dd
     * @param hasta último día obligatorio yyyy-MM-dd
     * @param barberoId perfil opcional, forzado al propio para BARBERO
     * @param servicioId servicio opcional
     * @param estado estado opcional
     * @param clienteId propietario opcional exclusivo de ADMIN
     * @param pagina índice desde cero, predeterminado 0
     * @param tamano tamaño 1 a 100, predeterminado 20
     * @param parametros valores HTTP originales para rechazar filtros presentes vacíos
     * @return página de agenda autorizada con contacto y permisos del personal
     * @throws NegocioException si el rango, página o filtro son inválidos o no están autorizados
     */
    @GetMapping("/api/reservas")
    public PaginaDto<ReservaDto> listar(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long barberoId, @RequestParam(required = false) Long servicioId,
            @RequestParam(required = false) EstadoReserva estado, @RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano,
            @RequestParam Map<String, String> parametros) {
        validarVacios(parametros);
        return consultas.listar(actor, desde, hasta, barberoId, servicioId, estado, clienteId, pagina, tamano);
    }

    /**
     * Devuelve una reserva solo a propietario, barbero asignado o ADMIN según CP-02.
     * @param actor identidad revalidada que recibe los permisos
     * @param id identidad de la reserva
     * @return detalle autorizado con fechas de Lima
     * @throws NegocioException NO_ENCONTRADO tanto para recurso inexistente como ajeno
     */
    @GetMapping("/api/reservas/{id}")
    public ReservaDto detalle(@AuthenticationPrincipal UsuarioAutenticado actor, @PathVariable long id) {
        return consultas.detalle(actor, id);
    }

    /**
     * Cancela una reserva propia o administrada con control de versión y política RN-07/08.
     * @param id identidad de la reserva solicitada
     * @param cmd cuerpo validado con versión obligatoria y motivo opcional
     * @param actor identidad CLIENTE o ADMIN revalidada por seguridad
     * @return HTTP 200 con estado, versión y permisos actualizados
     * @throws NegocioException si fallan visibilidad, rol, versión, estado, ventana o motivo
     */
    @PostMapping("/api/reservas/{id}/cancelacion")
    public ReservaDto cancelar(@PathVariable long id, @Valid @RequestBody CancelarReservaDto cmd,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return reservas.cancelar(id, cmd, actor);
    }

    private static void validarVacios(Map<String, String> parametros) {
        for (String clave : new String[]{"estado", "desde", "hasta", "barberoId", "servicioId", "clienteId", "pagina", "tamano"}) {
            if (parametros.containsKey(clave) && parametros.get(clave).isBlank()) {
                throw new NegocioException(ErrorCodigo.VALIDACION, "El parámetro " + clave + " no puede estar vacío.");
            }
        }
    }
}
