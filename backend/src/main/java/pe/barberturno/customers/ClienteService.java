package pe.barberturno.customers;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.customers.dto.*;
import pe.barberturno.platform.RolNegocio;

/**
 * Consulta y edición de fichas por PROPIETARIO/ENCARGADO, sin cambios en la cuenta (V2-RN-16).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class ClienteService {
    private final ClienteRepository clientes;
    private final Clock reloj;

    /**
     * Recibe persistencia con RLS y el reloj para fechar la edición local.
     * @param clientes fichas del negocio de cada transacción
     * @param reloj fuente de instantes del servicio
     */
    public ClienteService(ClienteRepository clientes, Clock reloj) {
        this.clientes = clientes;
        this.reloj = reloj;
    }

    /**
     * Consulta por id de ficha; las fichas ajenas se ocultan como inexistentes.
     * @param id identificador de ficha solicitado
     * @param actor personal con pertenencia activa de gestión
     * @return datos locales de la ficha visible
     * @throws NegocioException PROHIBIDO si el actor no gestiona; NO_ENCONTRADO si la ficha no es visible
     */
    @Transactional(readOnly = true)
    public ClienteDto obtener(long id, UsuarioAutenticado actor) {
        exigirGestion(actor);
        return ClienteDto.desde(clientes.findById(id).orElseThrow(this::noEncontrado));
    }

    /**
     * Serializa la edición en la propia ficha; no carga ni modifica su cuenta o fichas de otros negocios.
     * @param id identificador de ficha solicitado
     * @param datos nombre y teléfono locales validados por MVC
     * @param actor personal con pertenencia activa de gestión
     * @return datos locales guardados
     * @throws NegocioException PROHIBIDO si el actor no gestiona; NO_ENCONTRADO si la ficha no es visible
     */
    @Transactional
    public ClienteDto editar(long id, EditarClienteDto datos, UsuarioAutenticado actor) {
        exigirGestion(actor);
        var cliente = clientes.bloquearPorId(id).orElseThrow(this::noEncontrado);
        cliente.actualizarDatos(datos.nombre(), datos.telefono(), reloj.instant());
        return ClienteDto.desde(clientes.saveAndFlush(cliente));
    }

    private void exigirGestion(UsuarioAutenticado actor) {
        if (actor == null || actor.rolNegocio().filter(r -> r == RolNegocio.PROPIETARIO || r == RolNegocio.ENCARGADO).isEmpty()) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Se requiere permiso de gestión del negocio.");
        }
    }

    private NegocioException noEncontrado() {
        return new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró la ficha del cliente.");
    }
}
