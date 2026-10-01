package pe.barberturno.common.security;

import java.util.Optional;
import pe.barberturno.users.Rol;

/**
 * Punto de extensión para el principal autenticado. T-10 aportará la implementación
 * basada en el JWT validado por Spring Security; no se registra un usuario ficticio
 * ni un bean provisional que conceda permisos.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface UsuarioActual {
    /** @return id del usuario autenticado */
    long id();
    /** @return rol verificado del usuario */
    Rol rol();
    /** @return perfil de barbero, también posible para un administrador */
    Optional<Long> barberoId();
}
