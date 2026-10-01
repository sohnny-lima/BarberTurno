package pe.barberturno.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Cierra provisionalmente la API hasta implementar la autenticación en T-10.
 * No crea sesiones ni configura usuarios con contraseñas generadas.
 * CSRF permanece desactivado mientras toda escritura está denegada; la protección
 * definitiva para JWT en cookie y la SPA corresponde a T-10.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * Permite salud y documentación habilitada, y deniega todas las demás rutas.
     *
     * @param http constructor de la seguridad HTTP
     * @param apiDocsHabilitada indica si el documento OpenAPI está habilitado
     * @param swaggerHabilitado indica si Swagger UI está habilitado
     * @return cadena de filtros sin sesión de servidor
     * @throws Exception si no se puede construir la cadena de seguridad
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsHabilitada,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerHabilitado) throws Exception {
        http.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(permisos -> {
                    permisos.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    permisos.requestMatchers(HttpMethod.HEAD, "/actuator/health").permitAll();
                    if (apiDocsHabilitada) {
                        permisos.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }
                    if (swaggerHabilitado) {
                        permisos.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    permisos.anyRequest().denyAll();
                });
        return http.build();
    }
}
