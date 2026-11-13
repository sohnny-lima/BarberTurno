package pe.barberturno.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Extrae JWT exclusivamente de la cookie BT_SESION para impedir credenciales por cabecera o parámetros (§7.1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    /**
     * SecurityConfig crea este lector sin estado para resolver la cookie de sesión en cada petición.
     */
    public CookieBearerTokenResolver() {
    }

    /**
     * Lee solo BT_SESION y solo bajo {@code /api/} (V2-DA-48): la entrada Angular, sus archivos y las rutas
     * técnicas no usan identidad, y validar ahí una cookie caducada o revocada respondería 401 en lugar de la
     * aplicación. Omite el token en POST login, registro (también de invitados) y logout para recuperar sesiones
     * (DA-22). Dos cookies no vacías producen un token inválido y evitan identidad ambigua.
     * @param request petición HTTP actual no nula
     * @return token de cookie; nulo fuera de {@code /api/}, si no existe o en POST de login, registro o logout,
     *         e inválido ante cookies ambiguas
     */
    @Override
    public String resolve(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        // Una ruta codificada (p. ej. /%61pi/) queda sin identidad: el fallo es siempre hacia menos acceso.
        if (!ruta.startsWith("/api/")) return null;
        // La autenticación pública permite sustituir o borrar una cookie caducada o revocada.
        if ("POST".equals(request.getMethod()) && ("/api/auth/logout".equals(ruta)
                || "/api/auth/login".equals(ruta)
                || "/api/registro".equals(ruta) || ruta.matches("/api/invitaciones/[A-Za-z0-9_-]+/registro"))) return null;
        if (request.getCookies() == null) return null;
        String token = null;
        for (var cookie : request.getCookies()) {
            if ("BT_SESION".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                // No aceptar cookies ambiguas de distintos paths.
                if (token != null) return "cookie-ambigua";
                token = cookie.getValue();
            }
        }
        return token;
    }
}
