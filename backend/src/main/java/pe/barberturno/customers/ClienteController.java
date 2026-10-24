package pe.barberturno.customers;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.customers.dto.*;

/**
 * API local de fichas, solo para PROPIETARIO/ENCARGADO; PATCH exige sesión y CSRF.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {
    private final ClienteService clientes;

    /**
     * Delega persistencia y autorización del ámbito al servicio Java.
     * @param clientes operaciones transaccionales sobre fichas
     */
    public ClienteController(ClienteService clientes) { this.clientes = clientes; }

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
