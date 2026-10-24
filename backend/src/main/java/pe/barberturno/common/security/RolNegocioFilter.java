package pe.barberturno.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.tenancy.ContextoNegocio;

/**
 * Aplica V2-DA-34 a las operaciones protegidas, tras revalidar la sesión en el negocio de la ruta.
 * La sesión local y el alta propia están disponibles sin pertenencia previa; las lecturas públicas
 * conservan su acceso. Los permisos de cada rol siguen en la única SecurityConfig y en los servicios.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public class RolNegocioFilter extends OncePerRequestFilter {
    private final ContextoNegocio contexto;
    private final RespuestaSeguridad errores;

    /**
     * Recibe el mismo ámbito que fijó el resolutor y el escritor común RFC 9457.
     * @param contexto ámbito de la petición, nunca elegido por el JWT o una cabecera
     * @param errores salida pública sin trazas
     */
    public RolNegocioFilter(ContextoNegocio contexto, RespuestaSeguridad errores) {
        this.contexto = contexto; this.errores = errores;
    }

    /**
     * Deniega con 403 a una identidad válida sin papel local, sin confundirla con una sesión inválida.
     * @param request solicitud normalizada por el resolutor
     * @param response respuesta que recibe PROHIBIDO cuando corresponde
     * @param chain autorización por rol y controles de recurso del resto de la aplicación
     * @throws ServletException si falla el procesamiento posterior
     * @throws IOException si falla la salida HTTP
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean publica = "GET".equals(request.getMethod()) && (ruta.equals("/api/servicios")
                || ruta.equals("/api/barberos") || ruta.equals("/api/disponibilidad") || ruta.equals("/api/sesion"));
        boolean altaPropia = "POST".equals(request.getMethod()) && (ruta.equals("/api/clientes/alta")
                || ruta.equals("/api/registro") || ruta.equals("/api/auth/registro"));
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (contexto.negocioActual().isPresent() && !publica && !altaPropia && autenticacion != null
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario && usuario.rol() == null) {
            errores.escribir(request, response, ErrorCodigo.PROHIBIDO, "No tiene permiso para realizar esta acción.");
            return;
        }
        chain.doFilter(request, response);
    }
}
