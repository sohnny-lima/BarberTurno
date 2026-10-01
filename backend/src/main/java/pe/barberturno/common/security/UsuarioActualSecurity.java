package pe.barberturno.common.security;

import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.users.Rol;

/**
 * Acceso al principal vigente de Spring Security.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class UsuarioActualSecurity implements UsuarioActual {
    /** @return identidad validada; nunca un usuario provisional
     * @throws NegocioException si no existe una sesión válida */
    public UsuarioAutenticado principal() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.isAuthenticated()
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new NegocioException(ErrorCodigo.NO_AUTENTICADO, "Se requiere una sesión válida.");
    }
    /** @return id vigente */
    @Override public long id() { return principal().id(); }
    /** @return rol vigente */
    @Override public Rol rol() { return principal().rol(); }
    /** @return perfil de barbero opcional */
    @Override public Optional<Long> barberoId() { return principal().barberoId(); }
}