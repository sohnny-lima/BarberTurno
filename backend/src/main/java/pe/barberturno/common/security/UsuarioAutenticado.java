package pe.barberturno.common.security;

import java.util.Optional;
import pe.barberturno.users.Rol;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.platform.RolPlataforma;

/**
 * Identidad verificada contra la base de datos en cada petición.
 * @param id identificador del usuario
 * @param rol rol efectivo del negocio; nulo si no tiene permisos de negocio
 * @param barberoId perfil opcional, incluido el administrador que atiende
 * @param debeCambiarPassword indica contraseña temporal
 * @param rolPlataforma permiso global independiente
 * @param rolNegocio pertenencia activa en el negocio actual, si existe
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record UsuarioAutenticado(long id, Rol rol, Optional<Long> barberoId,
        boolean debeCambiarPassword, RolPlataforma rolPlataforma, Optional<RolNegocio> rolNegocio) {
    /**
     * Conserva la construcción de actores de las pruebas de dominio de v1.
     * @param id identidad del actor
     * @param rol equivalencia efectiva de v1
     * @param barberoId perfil opcional del negocio
     * @param debeCambiarPassword marca de contraseña temporal
     */
    public UsuarioAutenticado(long id, Rol rol, Optional<Long> barberoId, boolean debeCambiarPassword) {
        this(id, rol, barberoId, debeCambiarPassword, RolPlataforma.NINGUNO,
                rol == Rol.ADMIN ? Optional.of(RolNegocio.PROPIETARIO)
                        : rol == Rol.BARBERO ? Optional.of(RolNegocio.BARBERO) : Optional.empty());
    }
}
