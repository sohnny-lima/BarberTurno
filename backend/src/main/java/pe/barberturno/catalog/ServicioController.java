package pe.barberturno.catalog;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.catalog.dto.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.users.Rol;

/**
 * API RF-04 en /api/servicios: consulta pública y escrituras ADMIN con CSRF según arquitectura §7.2.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/servicios")
public class ServicioController {
    private final ServicioService servicios;

    /**
     * Inyecta el mantenimiento del catálogo para delegar reglas y persistencia al servicio Java.
     * @param servicios repositorio o servicio no nulo del catálogo
     */
    public ServicioController(ServicioService servicios) { this.servicios = servicios; }

    /**
     * GET /api/servicios público: lista activos; solo ADMIN incluye inactivos. No exige CSRF; cuentas temporales
     * reciben CAMBIO_PASSWORD_REQUERIDO (403).
     * @param incluirInactivos true incluye inactivos; el controlador autoriza solo a ADMIN
     * @param actual identidad revalidada; nula solo en consultas públicas
     * @return resultados con el orden descrito, sin comprobación adicional de permisos
     */
    @GetMapping
    public List<ServicioDto> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos,
            @AuthenticationPrincipal UsuarioAutenticado actual) {
        return servicios.listar(incluirInactivos && actual != null && actual.rol() == Rol.ADMIN);
    }

    /**
     * POST /api/servicios: solo ADMIN con CSRF; responde 201. Rechaza VALIDACION (400), NOMBRE_DUPLICADO (409),
     * NO_AUTENTICADO (401), PROHIBIDO o CAMBIO_PASSWORD_REQUERIDO (403).
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @return representación pública resultante de la operación
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioDto crear(@Valid @RequestBody GuardarServicioDto datos) {
        return servicios.crear(datos);
    }

    /**
     * PUT /api/servicios/{id}: solo ADMIN con CSRF; responde 200 y conserva RN-13. Rechaza VALIDACION (400),
     * NO_ENCONTRADO (404), NOMBRE_DUPLICADO (409), NO_AUTENTICADO (401) o PROHIBIDO (403).
     * @param id identificador persistente positivo del recurso, no nulo
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @return representación pública resultante de la operación
     */
    @PutMapping("/{id}")
    public ServicioDto editar(@PathVariable long id, @Valid @RequestBody GuardarServicioDto datos) {
        return servicios.editar(id, datos);
    }

    /**
     * PATCH /api/servicios/{id}/estado: solo ADMIN con CSRF; responde 200 sin borrar (RN-16). Rechaza VALIDACION
     * (400), NO_ENCONTRADO (404), NO_AUTENTICADO (401) o PROHIBIDO (403).
     * @param id identificador persistente positivo del recurso, no nulo
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @return representación pública resultante de la operación
     */
    @PatchMapping("/{id}/estado")
    public ServicioDto cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoServicioDto datos) {
        return servicios.cambiarEstado(id, datos.activo());
    }
}
