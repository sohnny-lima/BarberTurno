package pe.barberturno.customers;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.customers.dto.ClienteDto;
import pe.barberturno.users.UsuarioRepository;

/**
 * Da de alta la ficha propia sin crear otra cuenta ni conceder permisos de personal.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class ClienteAltaService {
    private final pe.barberturno.common.tenancy.ProveedorNegocioActual negocio;
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    /**
     * Recibe la persistencia local y el bloqueo de la cuenta para serializar dos altas propias.
     * @param negocio ámbito de la petición, adicional a la barrera RLS
     * @param clientes fichas sometidas a RLS FORCE
     * @param usuarios identidades globales; solo se bloquea la del principal
     * @param reloj fecha de creación de la ficha
     */
    public ClienteAltaService(ClienteRepository clientes, UsuarioRepository usuarios, Clock reloj, pe.barberturno.common.tenancy.ProveedorNegocioActual negocio) {
        this.negocio = negocio;
        this.clientes = clientes; this.usuarios = usuarios; this.reloj = reloj;
    }

    /**
     * Crea una ficha activa con los datos iniciales de la cuenta, sin reactivar fichas existentes.
     * @param usuarioId identidad de la sesión válida, nunca del cuerpo HTTP
     * @return ficha propia recién creada
     * @throws NegocioException CONFLICTO si ya hay ficha, incluso inactiva; NO_AUTENTICADO si falta cuenta
     */
    @Transactional
    public ClienteDto alta(long usuarioId) {
        var usuario = usuarios.bloquearPorId(usuarioId).orElseThrow(() ->
                new NegocioException(ErrorCodigo.NO_AUTENTICADO, "Se requiere una sesión válida."));
        if (clientes.findByNegocioIdAndUsuarioId(negocio.negocioActual().orElseThrow(), usuarioId).isPresent()) {
            throw new NegocioException(ErrorCodigo.CONFLICTO, "Ya tiene una ficha en este negocio.");
        }
        return ClienteDto.desde(clientes.saveAndFlush(new Cliente(usuario, reloj.instant())));
    }
}
