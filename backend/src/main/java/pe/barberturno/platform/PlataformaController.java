package pe.barberturno.platform;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.platform.dto.*;
import pe.barberturno.users.dto.PasswordTemporalDto;

/**
 * API global exclusiva de ADMIN_PLATAFORMA, con CSRF obligatorio en cada escritura.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/plataforma")
public class PlataformaController {
    private final PlataformaService plataforma;

    /**
     * Renueva la invitación del destinatario inicial y evita almacenar su capacidad en caché.
     * @param id negocio destinatario
     * @param actor administrador global autenticado
     * @return HTTP 200 con enlace mostrado una sola vez
     * @throws NegocioException si falta permiso, invitación o el negocio está suspendido
     */
    @PostMapping("/negocios/{id}/invitacion-propietario")
    public ResponseEntity<InvitacionEmitidaDto> reenviarInicial(@PathVariable long id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(plataforma.reenviarInicial(id, actor));
    }

    /**
     * Invalida la invitación inicial sin afectar a otras invitaciones ni membresías.
     * @param id negocio destinatario
     * @param actor administrador global autenticado
     * @return HTTP 200 sin cuerpo
     * @throws NegocioException si falta permiso o invitación inicial
     */
    @DeleteMapping("/negocios/{id}/invitacion-propietario")
    public ResponseEntity<Void> revocarInicial(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        plataforma.revocarInicial(id, actor); return ResponseEntity.ok().build();
    }

    /**
     * Conecta las rutas globales con el servicio transaccional autorizado.
     * @param plataforma operaciones y auditoría atómicas
     */
    public PlataformaController(PlataformaService plataforma) { this.plataforma = plataforma; }

    /**
     * Da de alta un negocio con invitación del propietario atómica y enlace mostrado una vez.
     * @param datos alta validada del negocio
     * @param actor administrador global revalidado
     * @return HTTP 201 con metadatos y enlace sin caché
     * @throws NegocioException si no hay permiso o el slug está ocupado
     */
    @PostMapping("/negocios")
    public ResponseEntity<NegocioCreadoDto> crear(@Valid @RequestBody CrearNegocioDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(plataforma.crear(datos, actor));
    }

    /**
     * Consulta el inventario de plataforma sin entrar en ningún negocio.
     * @param actor administrador global revalidado
     * @return metadatos y número de miembros activos por negocio
     * @throws NegocioException si no tiene permiso global
     */
    @GetMapping("/negocios")
    public List<NegocioPlataformaDto> listar(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return plataforma.listar(actor);
    }

    /**
     * Suspende o reactiva un negocio sin cambiar sus datos operativos.
     * @param id negocio destinatario
     * @param datos estado obligatorio
     * @param actor administrador global revalidado
     * @return HTTP 200 sin cuerpo
     * @throws NegocioException si falta permiso o negocio
     */
    @PatchMapping("/negocios/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable long id, @Valid @RequestBody EstadoNegocioDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        plataforma.cambiarEstado(id, datos.estado(), actor);
        return ResponseEntity.ok().build();
    }

    /**
     * Entrega una credencial temporal una vez e impide almacenarla en caché.
     * @param id identidad ajena que recupera el acceso
     * @param actor administrador global revalidado
     * @return temporal con Cache-Control no-store
     * @throws NegocioException si el objetivo es el propio actor, no existe o falta permiso
     */
    @PostMapping("/cuentas/{id}/restablecer-password")
    public ResponseEntity<PasswordTemporalDto> restablecer(@PathVariable long id,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(plataforma.restablecer(id, actor));
    }
}
