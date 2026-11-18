package pe.barberturno.customers;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.customers.dto.*;

/**
 * Consulta y edición de fichas para PROPIETARIO/ENCARGADO y alta propia para cualquier cuenta.
 * Todas las escrituras exigen sesión y CSRF.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {
    private final ClienteService clientes;
    private final ClienteAltaService altas;

    /**
     * Delega persistencia y autorización del ámbito al servicio Java.
     * @param clientes operaciones transaccionales sobre fichas
     * @param altas creación de la ficha propia, disponible para toda cuenta autenticada
     */
    public ClienteController(ClienteService clientes, ClienteAltaService altas) { this.clientes = clientes; this.altas = altas; }

    /**
     * Da de alta exclusivamente a la cuenta autenticada, con CSRF y sin aceptar ids del cuerpo.
     * @param actor identidad revalidada, con cualquier rol local o sin relación previa
     * @return ficha propia y estado 201
     * @throws NegocioException CONFLICTO si la cuenta ya tiene una ficha en este negocio
     */
    @PostMapping("/alta")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ClienteDto alta(@AuthenticationPrincipal UsuarioAutenticado actor) { return altas.alta(actor.id()); }

    /**
     * GET de una ficha por su id local, con 404 uniforme para ids ajenos o inexistentes.
     * @param id identificador de la ficha
     * @param actor identidad revalidada en el negocio actual
     * @return datos locales sin información de la cuenta
     * @throws NegocioException si falta permiso de gestión o la ficha no es visible
     */
    @GetMapping("/{id}")
    public ClienteDto obtener(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return clientes.obtener(id, actor);
    }

    /**
     * PATCH de nombre y teléfono locales; nunca modifica la identidad global.
     * @param id identificador de la ficha
     * @param datos campos locales validados por MVC
     * @param actor identidad revalidada en el negocio actual
     * @return datos locales actualizados
     * @throws NegocioException si falta permiso de gestión o la ficha no es visible
     */
    @PatchMapping("/{id}")
    public ClienteDto editar(@PathVariable long id, @Valid @RequestBody EditarClienteDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return clientes.editar(id, datos, actor);
    }
}
