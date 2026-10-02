package pe.barberturno.common.security;

import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.users.Rol;

/**
 * Lee el principal revalidado de Spring Security y rechaza el acceso sin sesión con NO_AUTENTICADO.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class UsuarioActualSecurity implements UsuarioActual {

    /**
     * Spring crea este componente sin estado; la identidad se consulta en el contexto de cada petición.
     */
    public UsuarioActualSecurity() {
    }
    /**
     * Exige identidad autenticada revalidada; nunca concede acceso con un usuario provisional.
     * @return principal autenticado y revalidado, nunca provisional
     * @throws NegocioException si no existe principal autenticado vigente (NO_AUTENTICADO)
     */
    public UsuarioAutenticado principal() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.isAuthenticated()
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new NegocioException(ErrorCodigo.NO_AUTENTICADO, "Se requiere una sesión válida.");
    }
    /**
     * Extrae identidad vigente del principal para consultar únicamente recursos propios.
     * @return identificador positivo del usuario autenticado
     * @throws pe.barberturno.common.error.NegocioException si no hay principal vigente (NO_AUTENTICADO)
     */
    @Override public long id() { return principal().id(); }
    /**
     * Extrae rol vigente del principal para aplicar los permisos en el servidor.
     * @return rol de la identidad revalidada
     * @throws pe.barberturno.common.error.NegocioException si no hay principal vigente (NO_AUTENTICADO)
     */
    @Override public Rol rol() { return principal().rol(); }
    /**
     * Extrae perfil opcional del principal; un ADMIN también puede tener agenda propia.
     * @return id del perfil de atención; Optional vacío si no tiene perfil
     * @throws pe.barberturno.common.error.NegocioException si no hay principal vigente (NO_AUTENTICADO)
     */
    @Override public Optional<Long> barberoId() { return principal().barberoId(); }
}