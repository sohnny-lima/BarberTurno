package pe.barberturno.users;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.users.dto.*;

/**
 * API administrativa RF-19; exige ADMIN y CSRF en escrituras, sin exponer datos internos de acceso.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioAdminController {
    private final UsuarioAdminService usuarios;

    /**
     * Conecta la API con las operaciones transaccionales autorizadas.
     * @param usuarios gestión de acceso y búsqueda
     */
    public UsuarioAdminController(UsuarioAdminService usuarios) { this.usuarios = usuarios; }

    /**
     * Consulta identidades por nombre, correo y rol con paginación del contrato §6.1.
     * @param q fragmento opcional de búsqueda
     * @param rol rol opcional
     * @param pagina índice desde cero, predeterminado cero
     * @param tamano tamaño de uno a cien, predeterminado veinte
     * @param actor identidad administrativa revalidada
     * @return página mínima de usuarios
     * @throws NegocioException si no tiene permiso o la paginación es inválida
     */
    @GetMapping
    public PaginaDto<UsuarioAdminDto> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) Rol rol, @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return usuarios.listar(q, rol, pagina, tamano, actor);
    }

    /**
     * Restablece acceso CP-17 y entrega una sola vez la temporal; prohíbe su almacenamiento en caché.
     * @param id identidad destinataria
     * @param actor ADMIN revalidado
     * @return HTTP 200 con temporal y Cache-Control no-store
     * @throws NegocioException si falta usuario o permiso
     */
    @PostMapping("/{id}/restablecer-password")
    public ResponseEntity<PasswordTemporalDto> restablecer(@PathVariable long id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usuarios.restablecer(id, actor));
    }

    /**
     * Habilita o revoca acceso sin borrar historia ni permitir quedarse sin ADMIN activo.
     * @param id identidad destinataria
     * @param datos estado obligatorio validado
     * @param actor ADMIN revalidado
     * @return HTTP 200 sin cuerpo conforme al contrato
     * @throws NegocioException si falta usuario o se viola la protección administrativa
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoUsuarioDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        usuarios.cambiarEstado(id, datos.activo(), actor);
        return ResponseEntity.ok().build();
    }
}
