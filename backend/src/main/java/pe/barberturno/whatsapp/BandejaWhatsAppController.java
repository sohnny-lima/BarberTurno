package pe.barberturno.whatsapp;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.web.PaginaDto;
import pe.barberturno.whatsapp.dto.*;

/**
 * API de atención humana bajo la ruta normalizada del negocio; las escrituras exigen CSRF.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/whatsapp/conversaciones")
public class BandejaWhatsAppController {
    private final BandejaWhatsApp bandeja;

    BandejaWhatsAppController(BandejaWhatsApp bandeja) { this.bandeja = bandeja; }

    /**
     * Lista solo las conversaciones humanas del ámbito autorizado.
     * @param pagina índice desde cero
     * @param tamano cantidad máxima de 50
     * @param actor identidad de la sesión
     * @return página con máscara y permisos de respuesta
     * @throws NegocioException si falta gestión o los límites no son válidos
     */
    @GetMapping
    public PaginaDto<ConversacionBandejaDto> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return bandeja.listar(actor.id(), pagina, tamano);
    }

    /**
     * Consulta una página del historial humano.
     * @param id conversación solicitada
     * @param antesDe cursor positivo exclusivo opcional
     * @param actor identidad de la sesión
     * @return hasta 50 mensajes en orden ascendente
     * @throws NegocioException si falta permiso, el cursor no es válido o no existe la conversación humana
     */
    @GetMapping("/{id}")
    public DetalleConversacionDto detalle(@PathVariable long id, @RequestParam(required = false) Long antesDe,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return bandeja.detalle(id, antesDe, actor.id());
    }

    /**
     * Encola una respuesta firmada por la sesión actual.
     * @param id conversación humana solicitada
     * @param datos texto validado
     * @param actor identidad de la sesión
     * @return mensaje creado en estado pendiente
     * @throws NegocioException si falta permiso, modo humano, operatividad o ventana
     */
    @PostMapping("/{id}/respuestas")
    @ResponseStatus(HttpStatus.CREATED)
    public MensajeBandejaDto responder(@PathVariable long id, @Valid @RequestBody RespuestaPersonalDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return bandeja.responder(id, datos.texto(), actor.id());
    }

    /**
     * Cierra la atención humana de forma idempotente.
     * @param id conversación solicitada
     * @param actor identidad de la sesión
     * @throws NegocioException si falta gestión o la conversación pertenece a otro ámbito
     */
    @PostMapping("/{id}/devolver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolver(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        bandeja.devolver(id, actor.id());
    }
}
