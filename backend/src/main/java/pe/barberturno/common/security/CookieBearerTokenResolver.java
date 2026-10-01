package pe.barberturno.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/** Acepta JWT solo en BT_SESION; ignora cabeceras y parámetros. */
public class CookieBearerTokenResolver implements BearerTokenResolver {
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
