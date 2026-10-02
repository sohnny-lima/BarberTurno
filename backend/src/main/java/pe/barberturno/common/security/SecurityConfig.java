package pe.barberturno.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import pe.barberturno.common.error.ErrorCodigo;

/**
 * Autenticación JWT por cookie, CSRF SPA y permisos de rutas implementadas.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * Spring crea esta configuración al arrancar el contexto para registrar la cadena de filtros.
     */
    public SecurityConfig() {
    }
    /**
     * Configura JWT por cookie sin sesiones HTTP y permisos §7.2; impone DEFAULT_CSRF_MATCHER también con JWT
     * para proteger toda escritura. Añade filtro de contraseña temporal y errores RFC 9457.
     * @param http constructor de la seguridad HTTP
     * @param converter revalidación de identidad
     * @param respuestas errores RFC 9457
     * @param apiDocsHabilitada habilitación de OpenAPI
     * @param swaggerHabilitado habilitación de Swagger
     * @param cookieSecure seguridad de cookies según perfil
     * @return cadena sin sesiones HTTP
     * @throws Exception si no se puede construir la cadena
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioJwtConverter converter,
            RespuestaSeguridad respuestas,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsHabilitada,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerHabilitado,
            @Value("${barberturno.seguridad.cookie-secure:false}") boolean cookieSecure) throws Exception {
        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, error) ->
                respuestas.escribir(request, response, ErrorCodigo.NO_AUTENTICADO, "Se requiere una sesión válida.");
        var denegado = (org.springframework.security.web.access.AccessDeniedHandler) (request, response, error) ->
                respuestas.escribir(request, response, ErrorCodigo.PROHIBIDO, "No tiene permiso para realizar esta acción.");
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(cookieSecure));
        http.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(config -> config.spa().csrfTokenRepository(csrf)
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            /**
                             * Restaura CSRF en toda escritura aun con bearer; evita la exclusión automática de
                             * solicitudes con JWT en cookie.
                             * @param filtro filtro CSRF no nulo creado por Spring Security
                             * @return mismo filtro con CSRF obligatorio en escrituras
                             */
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filtro) {
                                // El resource server excluye bearer de CSRF por defecto.
                                // Con JWT en cookie toda escritura sigue necesitando CSRF.
                                filtro.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                                return filtro;
                            }
                        }))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errores -> errores.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(denegado))
                .oauth2ResourceServer(server -> server
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(denegado)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterAfter(new PasswordTemporalFilter(respuestas), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(permisos -> {
                    permisos.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    permisos.requestMatchers(HttpMethod.HEAD, "/actuator/health").permitAll();
                    if (apiDocsHabilitada) {
                        permisos.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**").permitAll();
                    }
                    if (swaggerHabilitado) {
                        permisos.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    permisos.requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/login",
                            "/api/auth/logout").permitAll();
                    permisos.requestMatchers(HttpMethod.GET, "/api/auth/sesion").permitAll();
                    permisos.requestMatchers(HttpMethod.PUT, "/api/auth/password").authenticated();
                    permisos.requestMatchers(HttpMethod.GET, "/api/perfil").authenticated();
                    permisos.requestMatchers(HttpMethod.PUT, "/api/perfil").authenticated();
                    permisos.requestMatchers(HttpMethod.GET, "/api/servicios").permitAll();
                    permisos.requestMatchers(HttpMethod.POST, "/api/servicios").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.PUT, "/api/servicios/{id}").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.PATCH, "/api/servicios/{id}/estado").hasRole("ADMIN");
                    permisos.anyRequest().denyAll();
                });
        return http.build();
    }
}
