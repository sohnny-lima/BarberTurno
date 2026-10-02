package pe.barberturno.users;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.users.dto.*;

/**
 * API RF-03 en /api/perfil para CLIENTE, BARBERO y ADMIN sobre su propia identidad; PUT exige CSRF.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {
    private final PerfilService perfiles;
    private final UsuarioActual actual;

    /**
     * Inyecta servicio y principal para operar exclusivamente sobre el perfil propio (RF-03).
     * @param perfiles servicio no nulo del perfil propio
     * @param actual identidad revalidada; nula solo en consultas públicas
     */
    public PerfilController(PerfilService perfiles, UsuarioActual actual) {
        this.perfiles = perfiles;
        this.actual = actual;
    }

    /**
     * GET /api/perfil: CLIENTE, BARBERO y ADMIN consultan su perfil, incluso con contraseña temporal; sin sesión
     * devuelve 401 NO_AUTENTICADO.
     * @return representación pública resultante de la operación
     */
    @GetMapping
    public PerfilDto obtener() { return perfiles.obtener(actual.id()); }

    /**
     * PUT /api/perfil: CLIENTE, BARBERO y ADMIN editan su perfil con CSRF; correo inmutable. Rechaza VALIDACION
     * (400), NO_AUTENTICADO (401), PROHIBIDO o CAMBIO_PASSWORD_REQUERIDO (403).
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @return representación pública resultante de la operación
     */
    @PutMapping
    public PerfilDto actualizar(@Valid @RequestBody ActualizarPerfilDto datos) {
        return perfiles.actualizar(actual.id(), datos);
    }
}