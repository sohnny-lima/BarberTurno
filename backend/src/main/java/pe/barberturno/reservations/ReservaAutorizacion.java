package pe.barberturno.reservations;

import org.springframework.stereotype.Component;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.users.Rol;

/**
 * Decisión única de visibilidad de reservas por propiedad, asignación o administración (§7.2, CP-02).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class ReservaAutorizacion {
    /** Crea la política sin estado compartido ni dependencias de persistencia. */
    public ReservaAutorizacion() { }

    /**
     * Comprueba el usuario asignado, sin confiar en un perfil enviado por el solicitante.
     * ADMIN conserva acceso global aunque tenga también perfil de barbero.
     * @param actor identidad vigente autenticada
     * @param reserva entidad con propietario y usuario del barbero accesibles
     * @return true para ADMIN, CLIENTE propietario o BARBERO asignado
     */
    public boolean puedeVer(UsuarioAutenticado actor, Reserva reserva) {
        return actor.rol() == Rol.ADMIN
                || actor.rol() == Rol.CLIENTE && actor.id() == reserva.getCliente().getId()
                || actor.rol() == Rol.BARBERO && actor.id() == reserva.getBarbero().getUsuario().getId();
    }
    /**
     * Aplica la misma visibilidad a la fotografía escalar RF-09 sin precargar entidades.
     * @param actor identidad vigente autenticada
     * @param clienteId propietario de la reserva
     * @param asignadoId cuenta del profesional asignado
     * @return true para ADMIN, CLIENTE propietario o BARBERO asignado
     */
    public boolean puedeVer(UsuarioAutenticado actor, long clienteId, long asignadoId) {
        return actor.rol() == Rol.ADMIN
                || actor.rol() == Rol.CLIENTE && actor.id() == clienteId
                || actor.rol() == Rol.BARBERO && actor.id() == asignadoId;
    }
}
