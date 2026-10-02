package pe.barberturno.common.config;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Comprueba las variables obligatorias de producción antes de crear los servicios.
 * Los errores solo contienen nombres de variables, nunca sus valores.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ConfiguracionProduccion {

    private static final List<String> VARIABLES_OBLIGATORIAS = List.of(
            "BT_DB_URL", "BT_DB_USER", "BT_DB_PASSWORD", "BT_JWT_SECRET",
            "BT_ADMIN_CORREO", "BT_ADMIN_PASSWORD", "BT_ADMIN_NOMBRE", "BT_COOKIE_SECURE");

    /**
     * Valida la configuración antes de abrir conexiones a la base de datos.
     * @param entorno configuración externa del proceso
     * @return comprobación previa a la creación de los beans
     * @throws IllegalStateException si faltan variables o la configuración es insegura
     */
    @Bean
    public static BeanFactoryPostProcessor validarVariablesProduccion(Environment entorno) {
        return fabrica -> {
            if (Arrays.stream(entorno.getActiveProfiles()).anyMatch(perfil ->
                    List.of("dev", "test", "demo").contains(perfil))) {
                throw new IllegalStateException("El perfil prod no puede combinarse con dev, test o demo.");
            }
            List<String> faltantes = VARIABLES_OBLIGATORIAS.stream()
                    .filter(nombre -> !StringUtils.hasText(entorno.getProperty(nombre)))
                    .toList();
            if (!faltantes.isEmpty()) {
                throw new IllegalStateException("Faltan variables obligatorias de producción: "
                        + String.join(", ", faltantes));
            }
            byte[] secreto;
            try {
                secreto = Base64.getDecoder().decode(entorno.getProperty("BT_JWT_SECRET"));
            } catch (IllegalArgumentException excepcion) {
                throw new IllegalStateException("BT_JWT_SECRET debe estar codificado en Base64.");
            }
            if (secreto.length < 32) {
                throw new IllegalStateException("BT_JWT_SECRET debe contener al menos 32 bytes decodificados.");
            }
            if (!"true".equalsIgnoreCase(entorno.getProperty("BT_COOKIE_SECURE"))) {
                throw new IllegalStateException("BT_COOKIE_SECURE debe ser true en producción.");
            }
        };
    }
}
