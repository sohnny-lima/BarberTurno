package pe.barberturno.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import pe.barberturno.common.web.SpaForwardFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import pe.barberturno.common.error.ErrorCodigo;

/**
 * Autenticación JWT por cookie, CSRF SPA estable durante la sesión (DA-26) y permisos de rutas implementadas.
 * @author Sohnny Walter Lima Infanzón
 * @version 3.1
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /**
     * Spring crea esta configuración al arrancar el contexto para registrar la cadena de filtros.
     */
    public SecurityConfig() {
    }

    /**
     * Comparte la política XSRF-TOKEN entre el filtro SPA y la invalidación explícita de login, registro y logout.
     * La cookie es legible por Angular, restringida al mismo sitio y segura según el perfil.
     * @param cookieSecure uso de HTTPS para la cookie, desactivado solo en desarrollo y pruebas locales
     * @return repositorio común con Path=/, SameSite=Strict y HttpOnly=false
     */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${barberturno.seguridad.cookie-secure:false}") boolean cookieSecure) {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(cookieSecure));
        return csrf;
    }

    /**
     * Configura JWT por cookie sin sesiones HTTP y permisos §7.2; impone DEFAULT_CSRF_MATCHER también con JWT
     * para proteger toda escritura. Permite el GET público exacto de disponibilidad RF-07 y POST /api/reservas a CLIENTE o ADMIN (RF-08/18).
     * Conserva el token CSRF entre peticiones JWT; AuthController lo invalida al cambiar la sesión (DA-26).
     * Permite reprogramación RF-09 y cancelación RF-10 solo a CLIENTE o ADMIN con CSRF. Autoriza los GET RF-11/13 por rol; el servicio impone propiedad y asignación en cada consulta.
     * Permite transiciones RF-12 solo a BARBERO o ADMIN; el servicio comprueba asignación y RN-12.
     * Permite auditoría RF-17 y avisos RF-16 con sesión; el servicio impone propiedad y asignación.
     * Autoriza el resumen RF-14 y gestión de usuarios RF-19 exclusivamente a ADMIN.
     * Añade filtro de contraseña temporal y errores RFC 9457; borra BT_SESION ante fallos del token (DA-22).
     * Sirve GET/HEAD de la SPA fuera de prefijos técnicos, sin abrir API. Impone CSP sin scripts en línea,
     * estilos Angular/Material en línea y HSTS en HTTPS o prod (TLS terminado por el proxy).
     * @param entorno perfiles activos usados para imponer HSTS también detrás del proxy en prod
     * @param http constructor de la seguridad HTTP
     * @param converter revalidación de identidad
     * @param respuestas errores RFC 9457
     * @param cookies política compartida de borrado de sesión que conserva los atributos de emisión
     * @param csrf repositorio compartido de CSRF para la SPA y los cambios explícitos de sesión
     * @param apiDocsHabilitada habilitación de OpenAPI
     * @param swaggerHabilitado habilitación de Swagger
     * @return cadena sin sesiones HTTP
     * @throws Exception si no se puede construir la cadena
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment entorno, UsuarioJwtConverter converter,
            RespuestaSeguridad respuestas, CookieSesion cookies, CookieCsrfTokenRepository csrf,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsHabilitada,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerHabilitado) throws Exception {
        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, error) -> {
            if (error instanceof OAuth2AuthenticationException) {
                response.addHeader(HttpHeaders.SET_COOKIE, cookies.borrar().toString());
            }
            respuestas.escribir(request, response, ErrorCodigo.NO_AUTENTICADO, "Se requiere una sesión válida.");
        };
        var denegado = (org.springframework.security.web.access.AccessDeniedHandler) (request, response, error) ->
                respuestas.escribir(request, response, ErrorCodigo.PROHIBIDO, "No tiene permiso para realizar esta acción.");
        http.headers(cabeceras -> cabeceras
                .httpStrictTransportSecurity(hsts -> hsts
                        .requestMatcher(peticion -> peticion.isSecure() || entorno.acceptsProfiles(Profiles.of("prod")))
                        .maxAgeInSeconds(31536000).includeSubDomains(true))
                .contentTypeOptions(opciones -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'self'; "
                        + "form-action 'self'; object-src 'none'; frame-ancestors 'none'")));
        http.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(config -> config.spa().csrfTokenRepository(csrf)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
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
                .addFilterAfter(new SpaForwardFilter(), AuthorizationFilter.class)
                .authorizeHttpRequests(permisos -> {
                    permisos.requestMatchers(SpaForwardFilter::esRecursoPublico).permitAll();
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
                    permisos.requestMatchers(HttpMethod.GET, "/api/barberos").permitAll();
                    permisos.requestMatchers(HttpMethod.POST, "/api/barberos").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.PUT, "/api/barberos/{id}").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.PATCH, "/api/barberos/{id}/estado").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.GET, "/api/barberos/{id}/jornadas").hasAnyRole("ADMIN", "BARBERO");
                    permisos.requestMatchers(HttpMethod.PUT, "/api/barberos/{id}/jornadas").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.GET, "/api/barberos/{id}/bloqueos").hasAnyRole("ADMIN", "BARBERO");
                    permisos.requestMatchers(HttpMethod.POST, "/api/barberos/{id}/bloqueos", "/api/bloqueos/lote").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.DELETE, "/api/bloqueos/{id}").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.GET, "/api/disponibilidad").permitAll();
                    permisos.requestMatchers(HttpMethod.POST, "/api/reservas").hasAnyRole("CLIENTE", "ADMIN");
                    permisos.requestMatchers(HttpMethod.GET, "/api/reservas/mias").hasRole("CLIENTE");
                    permisos.requestMatchers(HttpMethod.GET, "/api/reservas").hasAnyRole("BARBERO", "ADMIN");
                    permisos.requestMatchers(HttpMethod.GET, "/api/reservas/{id}").authenticated();
                    permisos.requestMatchers(HttpMethod.POST, "/api/reservas/{id}/cancelacion", "/api/reservas/{id}/reprogramacion").hasAnyRole("CLIENTE", "ADMIN");
                    permisos.requestMatchers(HttpMethod.POST, "/api/reservas/{id}/transiciones").hasAnyRole("BARBERO", "ADMIN");
                    permisos.requestMatchers(HttpMethod.GET, "/api/reservas/{id}/auditoria").authenticated();
                    permisos.requestMatchers(HttpMethod.GET, "/api/notificaciones", "/api/notificaciones/conteo").authenticated();
                    permisos.requestMatchers(HttpMethod.POST, "/api/notificaciones/{id}/lectura", "/api/notificaciones/lectura").authenticated();
                    permisos.requestMatchers(HttpMethod.GET, "/api/reportes/resumen").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.GET, "/api/usuarios").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.POST, "/api/usuarios/{id}/restablecer-password").hasRole("ADMIN");
                    permisos.requestMatchers(HttpMethod.PATCH, "/api/usuarios/{id}/estado").hasRole("ADMIN");
                    permisos.anyRequest().denyAll();
                });
        return http.build();
    }
}
