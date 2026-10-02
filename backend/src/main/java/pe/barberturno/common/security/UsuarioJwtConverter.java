package pe.barberturno.common.security;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pe.barberturno.auth.AuthService;

/**
 * Revalida usuario activo y token_version en la base; construye autoridades con el rol vigente (§7.1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class UsuarioJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthService auth;

    /**
     * Inyecta revalidación contra la base para no confiar en el rol firmado de sesiones antiguas.
     * @param auth servicio no nulo de identidad y revalidación
     */
    public UsuarioJwtConverter(AuthService auth) { this.auth = auth; }

    /**
     * Verifica sub positivo y tv entero; rechaza identidad ausente, inactiva o revocada y usa rol vigente para
     * construir ROLE_ (§7.1).
     * @param jwt token no nulo con firma y vigencia ya verificadas por el decoder
     * @return principal y autoridades vigentes
     * @throws org.springframework.security.oauth2.server.resource.InvalidBearerTokenException si sub o tv no son
     * válidos o el usuario no existe, está inactivo o tiene otra token_version
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            Number version = jwt.getClaim("tv");
            if (id <= 0 || version == null || version.longValue() != version.intValue()
                    || version.doubleValue() != version.intValue()) {
                throw new IllegalArgumentException("Identidad inválida.");
            }
            var usuario = auth.autenticar(id, version.intValue())
                    .orElseThrow(() -> new InvalidBearerTokenException("Se requiere una sesión válida."));
            return UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name())));
        } catch (IllegalArgumentException | ClassCastException error) {
            throw new InvalidBearerTokenException("Se requiere una sesión válida.");
        }
    }
}
