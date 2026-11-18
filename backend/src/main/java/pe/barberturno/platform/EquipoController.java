package pe.barberturno.platform;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.platform.dto.*;

/**
 * API del equipo delimitada por el resolutor de negocio, con CSRF en todas las escrituras.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/equipo")
public class EquipoController {
    private final EquipoService equipo;

    /**
     * Conecta HTTP con las operaciones atómicas del equipo.
     * @param equipo autorización y cambios locales
     */
    public EquipoController(EquipoService equipo) { this.equipo = equipo; }

    /**
     * Muestra roles, estados y causas de baja solo del negocio resuelto.
     * @param actor gestor autenticado
     * @return pertenencias históricas locales
     * @throws NegocioException si carece de permiso
     */
    @GetMapping
    public List<EquipoDto> listar(@AuthenticationPrincipal UsuarioAutenticado actor) { return equipo.listar(actor); }

    /**
     * Sustituye el rol de una pertenencia local bajo autorización del propietario.
     * @param id pertenencia destinataria
     * @param datos nuevo rol validado
     * @param actor propietario autenticado
     * @return HTTP 200 sin cuerpo
     * @throws NegocioException si falta permiso, recurso o propietario alternativo
     */
    @PatchMapping("/{id}/rol")
    public ResponseEntity<Void> cambiarRol(@PathVariable long id, @Valid @RequestBody CambiarRolDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        equipo.cambiarRol(id, datos.rol(), actor); return ResponseEntity.ok().build();
    }

    /**
     * Registra una decisión del gestor sin desactivar la cuenta global.
     * @param id pertenencia que pierde acceso
     * @param actor gestor autenticado
     * @return HTTP 200 sin cuerpo
     * @throws NegocioException si falta permiso, recurso o propietario alternativo
     */
    @PostMapping("/{id}/revocacion")
    public ResponseEntity<Void> revocar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        equipo.revocar(id, actor); return ResponseEntity.ok().build();
    }

    /**
     * Retira la membresía de la propia persona autenticada.
     * @param actor miembro que abandona el negocio
     * @return HTTP 200 sin cuerpo
     * @throws NegocioException si falta membresía o propietario alternativo
     */
    @PostMapping("/salida")
    public ResponseEntity<Void> salir(@AuthenticationPrincipal UsuarioAutenticado actor) {
        equipo.salir(actor); return ResponseEntity.ok().build();
    }
}
