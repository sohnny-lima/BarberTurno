package pe.barberturno.common.security;

import java.util.Optional;
import pe.barberturno.users.Rol;

/**
 * Identidad verificada contra la base de datos en cada petición.
 * @param id identificador del usuario
 * @param rol rol vigente
 * @param barberoId perfil opcional, incluido el administrador que atiende
 * @param debeCambiarPassword indica contraseña temporal
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record UsuarioAutenticado(long id, Rol rol, Optional<Long> barberoId,
        boolean debeCambiarPassword) { }