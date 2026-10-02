package pe.barberturno.auth;

import java.time.Clock;
import java.time.Duration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import pe.barberturno.users.Usuario;

/**
 * Emite sesiones HS256 de ocho horas con la versión de revocación vigente (arquitectura §7.1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class JwtService {
    /**
     * Sesión de ocho horas; al alcanzar exp la sesión ya no es válida.
     */
    public static final Duration VIGENCIA = Duration.ofHours(8);
    private final JwtEncoder encoder;
    private final Clock clock;

    /**
     * Inyecta el firmante y el reloj para compartir la referencia temporal de emisión y validación.
     * @param encoder firmante HS256
     * @param clock reloj de emisión
     */
    public JwtService(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    /**
     * Firma sub, rol y tv de la identidad vigente; usa el Clock para iat y exp y limita la sesión a ocho horas.
     * @param usuario identidad autenticada
     * @return token para la cookie HttpOnly; nunca para logs o DTO
     */
    public String emitir(Usuario usuario) {
        var ahora = clock.instant();
        var claims = JwtClaimsSet.builder().subject(usuario.getId().toString())
                .claim("rol", usuario.getRol().name()).claim("tv", usuario.getTokenVersion())
                .issuedAt(ahora).expiresAt(ahora.plus(VIGENCIA)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
