package pe.barberturno.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/**
 * Configura firma HS256, caducidad estricta y BCrypt de coste 12 para RNF-03 y arquitectura §7.1.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    /**
     * Spring crea esta configuración al arrancar el contexto para registrar los beans de seguridad.
     */
    public JwtConfig() {
    }

    /**
     * Construye clave HS256 de al menos 32 bytes; genera clave efímera solo en dev/test sin prod, sin secretos
     * predeterminados.
     * @param secreto clave Base64; vacía solo para generar clave efímera dev/test
     * @param entorno configuración y perfiles activos no nulos; nunca se imprimen valores
     * @return clave HmacSHA256 para firmar y validar sesiones
     * @throws IllegalStateException si falta el secreto fuera de dev/test, está mal codificado o tiene menos de
     * 32 bytes
     */
    @Bean
    public SecretKey claveJwt(@Value("${barberturno.seguridad.jwt-secret:}") String secreto,
            Environment entorno) {
        byte[] bytes;
        if (secreto.isBlank()) {
            if (!entorno.acceptsProfiles(Profiles.of("dev", "test"))
                    || entorno.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("Debe configurar el secreto JWT.");
            }
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            LoggerFactory.getLogger(JwtConfig.class)
                    .warn("Secreto JWT efímero: las sesiones caducarán al reiniciar.");
        } else {
            try {
                bytes = Base64.getDecoder().decode(secreto);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("El secreto JWT debe estar codificado en Base64.");
            }
            if (bytes.length < 32) throw new IllegalStateException("El secreto JWT requiere al menos 32 bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Configura el firmante HS256 con la clave local, sin consultar proveedores externos.
     * @param claveJwt clave HS256 no nula de al menos 32 bytes
     * @return firmante HS256 configurado
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    /**
     * Valida HS256 con Clock: exp estrictamente futuro, iat no futuro y nbf, si existe, alcanzado; sin
     * tolerancia añadida.
     * @param claveJwt clave HS256 no nula de al menos 32 bytes
     * @param clock Clock inyectado no nulo; aporta instantes absolutos para America/Lima
     * @return validador HS256 con límites estrictos
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey claveJwt, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveJwt)
                .macAlgorithm(MacAlgorithm.HS256).build();
        // Sin tolerancia añadida: a las ocho horas exactas la sesión ya caducó.
        decoder.setJwtValidator(jwt -> {
            var ahora = clock.instant();
            if (jwt.getExpiresAt() == null || !ahora.isBefore(jwt.getExpiresAt())
                    || jwt.getIssuedAt() == null || ahora.isBefore(jwt.getIssuedAt())
                    || (jwt.getNotBefore() != null && ahora.isBefore(jwt.getNotBefore()))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "La sesión no está vigente.", null));
            }
            return OAuth2TokenValidatorResult.success();
        });
        return decoder;
    }

    /**
     * {@return codificador BCrypt de coste 12 para proteger las credenciales según RNF-03}
     */
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
