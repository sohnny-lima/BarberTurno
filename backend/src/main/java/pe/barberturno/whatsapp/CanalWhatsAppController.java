package pe.barberturno.whatsapp;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.whatsapp.dto.CanalWhatsAppDto;
import pe.barberturno.whatsapp.dto.RegistrarCanalWhatsAppDto;

/**
 * API de plataforma del canal de WhatsApp de cada negocio (11-v2-08-whatsapp-entrada.md §4.1). Exige
 * {@code ADMIN_PLATAFORMA} en {@code SecurityConfig} y en el servicio, y CSRF en las escrituras.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/plataforma/negocios/{id}/canal-whatsapp")
public class CanalWhatsAppController {
    private final CanalWhatsAppService canales;

    /**
     * Delega en el servicio transaccional del canal.
     * @param canales alta, consulta y desconexión auditadas
     */
    public CanalWhatsAppController(CanalWhatsAppService canales) {
        this.canales = canales;
    }

    /**
     * Consulta el canal del negocio.
     * @param id negocio de la ruta
     * @param actor identidad revalidada
     * @return HTTP 200 con el canal
     * @throws pe.barberturno.common.error.NegocioException NO_ENCONTRADO si no existe el negocio o el canal
     */
    @GetMapping
    public CanalWhatsAppDto consultar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return canales.consultar(id, actor);
    }

    /**
     * Registra o sustituye el número del negocio.
     * @param id negocio de la ruta
     * @param datos identificadores del número en la Cloud API
     * @param actor identidad revalidada
     * @return HTTP 200 con el canal activo
     * @throws pe.barberturno.common.error.NegocioException 404, 409 NEGOCIO_SUSPENDIDO, 409 CONFLICTO sin propietario
     *         o 409 NUMERO_WHATSAPP_EN_USO
     */
    @PutMapping
    public CanalWhatsAppDto registrar(@PathVariable long id, @Valid @RequestBody RegistrarCanalWhatsAppDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return canales.registrar(id, datos, actor);
    }

    /**
     * Desconecta el canal; es idempotente.
     * @param id negocio de la ruta
     * @param actor identidad revalidada
     * @return HTTP 204
     * @throws pe.barberturno.common.error.NegocioException NO_ENCONTRADO si no existe el negocio o el canal
     */
    @PostMapping("/desconexion")
    public ResponseEntity<Void> desconectar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        canales.desconectar(id, actor);
        return ResponseEntity.noContent().build();
    }
}
