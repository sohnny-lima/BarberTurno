package pe.barberturno.auth;

import java.time.Clock;
import java.time.Duration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import pe.barberturno.users.Usuario;

/**
 * Emite sesiones firmadas con vigencia de ocho horas.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class JwtService {
    public static final Duration VIGENCIA = Duration.ofHours(8);
    private final JwtEncoder encoder;
    private final Clock clock;

    /** @param encoder firmante HS256
     * @param clock reloj de emisión */
    public JwtService(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    /** @param usuario identidad autenticada
     * @return token para la cookie HttpOnly; nunca para logs o DTO */
    public String emitir(Usuario usuario) {
        var ahora = clock.instant();
        var claims = JwtClaimsSet.builder().subject(usuario.getId().toString())
                .claim("rol", usuario.getRol().name()).claim("tv", usuario.getTokenVersion())
                .issuedAt(ahora).expiresAt(ahora.plus(VIGENCIA)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
