package pe.barberturno.whatsapp;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.conversation.VinculoProfesionalWhatsAppService;
import pe.barberturno.whatsapp.dto.CodigoVinculoProfesionalDto;
import pe.barberturno.whatsapp.dto.EstadoVinculoProfesionalDto;

/**
 * API de la cuenta profesional bajo /api/n/{slug}, con sesión y CSRF en las escrituras.
 * La normalización del prefijo y RLS fijan el negocio; el servicio oculta perfiles ajenos con 404.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/whatsapp/vinculo-profesional")
public class VinculoProfesionalWhatsAppController {
    private final VinculoProfesionalWhatsAppService vinculos;

    VinculoProfesionalWhatsAppController(VinculoProfesionalWhatsAppService vinculos) { this.vinculos = vinculos; }

    /**
     * Emite una capacidad de un uso para el perfil propio activo.
     * @param usuario sesión revalidada de la petición
     * @return código y fecha de caducidad; HTTP 201
     * @throws NegocioException si no existe perfil y membresía activos (404)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CodigoVinculoProfesionalDto generar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return vinculos.generar(usuario.id());
    }

    /**
     * Proyecta el estado propio sin exponer el identificador de WhatsApp.
     * @param usuario sesión revalidada de la petición
     * @return estado del vínculo local
     * @throws NegocioException si no existe perfil y membresía activos (404)
     */
    @GetMapping
    public EstadoVinculoProfesionalDto estado(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return vinculos.estado(usuario.id());
    }

    /**
     * Desvincula el perfil propio y retira sus capacidades pendientes.
     * @param usuario sesión revalidada, con CSRF comprobado por la cadena de seguridad
     * @throws NegocioException si no existe perfil y membresía activos (404)
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@AuthenticationPrincipal UsuarioAutenticado usuario) { vinculos.desvincular(usuario.id()); }
}
