package pe.barberturno.scheduling;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.scheduling.dto.*;
import pe.barberturno.users.Rol;

/**
 * API RF-05 en /api/barberos; consulta pública y mantenimiento ADMIN con CSRF según §7.2.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/barberos")
public class BarberoController {
    private final BarberoService barberos;

    /**
     * Delega reglas y transacciones al servicio de personal Java.
     * @param barberos mantenimiento RF-05
     */
    public BarberoController(BarberoService barberos) { this.barberos = barberos; }

    /**
     * GET público de activos ordenados; solo ADMIN puede incluir inactivos y leer contactos.
     * @param incluirInactivos solicitud ignorada salvo para ADMIN
     * @param actual identidad revalidada o null sin sesión
     * @return perfiles autorizados sin contraseñas
     */
    @GetMapping
    public List<BarberoDto> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos,
            @AuthenticationPrincipal UsuarioAutenticado actual) {
        return barberos.listar(incluirInactivos, actual != null && actual.rol() == Rol.ADMIN);
    }

    /**
     * POST solo ADMIN con CSRF; crea cuenta temporal o vínculo ADMIN y responde 201.
     * @param datos variante validada de alta
     * @return perfil creado con contactos para ADMIN
     * @throws NegocioException si la variante no es válida (400), el vínculo o correo está ocupado (409),
     * falta el usuario (404) o se alcanza el límite de activos (422)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BarberoDto crear(@Valid @RequestBody CrearBarberoDto datos) { return barberos.crear(datos); }

    /**
     * PUT solo ADMIN con CSRF; responde 200 preservando acceso e historial RN-16.
     * @param id perfil que se edita; inexistente produce NO_ENCONTRADO (404)
     * @param datos identidad visible y especialidad validadas
     * @return perfil actualizado con contactos para ADMIN
     * @throws NegocioException si el perfil no existe (NO_ENCONTRADO, 404)
     */
    @PutMapping("/{id}")
    public BarberoDto editar(@PathVariable long id, @Valid @RequestBody EditarBarberoDto datos) {
        return barberos.editar(id, datos);
    }

    /**
     * PATCH solo ADMIN con CSRF; responde 200 con las citas futuras conservadas; aplica RN-19 al reactivar.
     * @param id perfil que cambia de estado; inexistente produce NO_ENCONTRADO (404)
     * @param datos estado obligatorio validado
     * @return perfil y contador para gestionar sus citas
     * @throws NegocioException si el perfil no existe (404) o se alcanza el límite de activos (422)
     */
    @PatchMapping("/{id}/estado")
    public ResultadoEstadoBarberoDto cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoBarberoDto datos) {
        return barberos.cambiarEstado(id, datos.activo());
    }
}
