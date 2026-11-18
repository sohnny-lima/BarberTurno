package pe.barberturno.common.security;

import java.util.ArrayList;
import pe.barberturno.platform.RolPlataforma;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pe.barberturno.auth.AuthService;

/**
 * Revalida cuenta, token_version y membresía por petición; separa la autoridad de plataforma
 * del rol efectivo del negocio (V2-DA-25).
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
     * Verifica sub positivo y tv entero; rechaza identidad ausente, inactiva o sesión revocada.
     * Una membresía revocada no concede autoridad de negocio; ADMIN_PLATAFORMA se incorpora aparte.
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
            var autoridades = new ArrayList<SimpleGrantedAuthority>();
            if (usuario.rol() != null) autoridades.add(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name()));
            if (usuario.rolPlataforma() == RolPlataforma.ADMIN_PLATAFORMA) {
                autoridades.add(new SimpleGrantedAuthority("ROLE_ADMIN_PLATAFORMA"));
            }
            return UsernamePasswordAuthenticationToken.authenticated(usuario, null, autoridades);
        } catch (IllegalArgumentException | ClassCastException error) {
            throw new InvalidBearerTokenException("Se requiere una sesión válida.");
        }
    }
}
