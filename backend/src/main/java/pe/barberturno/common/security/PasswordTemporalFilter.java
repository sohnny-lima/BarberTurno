package pe.barberturno.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.barberturno.common.error.ErrorCodigo;

/** Impide operaciones fuera de auth y la lectura del perfil con contraseña temporal. */
public class PasswordTemporalFilter extends OncePerRequestFilter {
    private final RespuestaSeguridad respuestas;

    public PasswordTemporalFilter(RespuestaSeguridad respuestas) { this.respuestas = respuestas; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        String ruta = request.getServletPath();
        if (ruta.isEmpty()) ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean permitida = ruta.startsWith("/api/auth/")
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
