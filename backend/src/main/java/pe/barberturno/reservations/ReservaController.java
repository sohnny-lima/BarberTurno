package pe.barberturno.reservations;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.reservations.dto.*;

/**
 * POST exacto RF-08 para CLIENTE con sesión y CSRF; delega reglas y concurrencia al servidor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
public class ReservaController {
    private final ReservaService reservas;

    /**
     * Recibe la creación transaccional, sin reglas de negocio en la capa HTTP.
     * @param reservas servicio atómico de creación
     */
    public ReservaController(ReservaService reservas) { this.reservas = reservas; }

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
}
