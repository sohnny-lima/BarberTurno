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
     * Lee solo BT_SESION; omite token en POST logout para limpiar cookies inválidas. Dos cookies no vacías
     * producen un token inválido y evitan identidad ambigua.
     * @param request petición HTTP actual no nula
     * @return token de cookie; nulo si no existe o es logout, e inválido ante cookies ambiguas
     */
    @Override
    public String resolve(HttpServletRequest request) {
        // Logout debe borrar también una cookie caducada o revocada.
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && "/api/auth/logout".equals(ruta)) return null;
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
