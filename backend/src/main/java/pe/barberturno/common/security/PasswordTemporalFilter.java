package pe.barberturno.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.web.SpaForwardFilter;

/**
 * Restringe las cuentas con contraseña temporal a auth y GET del perfil (RF-15, arquitectura §7.2).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class PasswordTemporalFilter extends OncePerRequestFilter {
    private final RespuestaSeguridad respuestas;

    /**
     * Inyecta el escritor de errores para rechazar operaciones sin exponer detalles de seguridad.
     * @param respuestas escritor no nulo de problemas públicos RFC 9457
     */
    public PasswordTemporalFilter(RespuestaSeguridad respuestas) { this.respuestas = respuestas; }

    /**
     * Permite auth y GET /api/perfil con contraseña temporal; bloquea el resto con CAMBIO_PASSWORD_REQUERIDO
     * (403) antes de MVC (§7.2). Conserva los recursos públicos de la SPA para cargar el cambio de contraseña.
     * @param request petición HTTP actual no nula
     * @param response respuesta HTTP no nula que recibe el resultado
     * @param chain cadena no nula continuada si la acción está permitida
     * @throws jakarta.servlet.ServletException si la cadena de filtros falla al procesar la petición
     * @throws java.io.IOException si no se puede escribir la respuesta o continuar la cadena
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        String ruta = request.getServletPath();
        if (ruta.isEmpty()) ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean permitida = SpaForwardFilter.esRecursoPublico(request) || ruta.startsWith("/api/auth/")
                || ("GET".equals(request.getMethod()) && "/api/perfil".equals(ruta));
        if (!permitida && autenticacion != null
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.debeCambiarPassword()) {
            respuestas.escribir(request, response, ErrorCodigo.CAMBIO_PASSWORD_REQUERIDO,
                    "Debe cambiar su contraseña antes de continuar.");
            return;
        }
        chain.doFilter(request, response);
    }
}
