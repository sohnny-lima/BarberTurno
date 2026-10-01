package pe.barberturno.catalog;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.catalog.dto.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.users.Rol;

/** Catálogo público y escrituras administrativas protegidas por SecurityConfig. */
@RestController
@RequestMapping("/api/servicios")
public class ServicioController {
    private final ServicioService servicios;

    public ServicioController(ServicioService servicios) { this.servicios = servicios; }

    @GetMapping
    public List<ServicioDto> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos,
            @AuthenticationPrincipal UsuarioAutenticado actual) {
        return servicios.listar(incluirInactivos && actual != null && actual.rol() == Rol.ADMIN);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioDto crear(@Valid @RequestBody GuardarServicioDto datos) {
        return servicios.crear(datos);
    }

    @PutMapping("/{id}")
    public ServicioDto editar(@PathVariable long id, @Valid @RequestBody GuardarServicioDto datos) {
        return servicios.editar(id, datos);
    }

    @PatchMapping("/{id}/estado")
    public ServicioDto cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoServicioDto datos) {
        return servicios.cambiarEstado(id, datos.activo());
    }
}
