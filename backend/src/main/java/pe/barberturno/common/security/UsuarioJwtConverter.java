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

/** Revalida estado, versión y rol vigente sin confiar en los permisos del token. */
@Component
public class UsuarioJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthService auth;

    public UsuarioJwtConverter(AuthService auth) { this.auth = auth; }

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
