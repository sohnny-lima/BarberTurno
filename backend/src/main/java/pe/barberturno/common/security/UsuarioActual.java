package pe.barberturno.common.security;

import java.util.Optional;
import pe.barberturno.users.Rol;

/**
 * Acceso a la identidad autenticada y revalidada por Spring Security; permite imponer propiedad y rol en el
 * servidor.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface UsuarioActual {
    /**
     * Identifica al actor autenticado para imponer propiedad de recursos en el servidor.
     * @return identificador positivo del usuario autenticado
     */
    long id();
    /**
     * Expone rol revalidado para decidir permisos sin confiar en datos enviados por el cliente.
     * @return rol vigente revalidado
     */
    Rol rol();
    /**
     * Relaciona identidad con su perfil opcional, también cuando ADMIN atiende como barbero.
     * @return id del perfil de atención; Optional vacío si no tiene perfil
     */
    Optional<Long> barberoId();
}
