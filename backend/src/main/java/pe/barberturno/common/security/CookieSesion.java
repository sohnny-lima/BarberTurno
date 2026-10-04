package pe.barberturno.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import pe.barberturno.auth.JwtService;

/**
 * Centraliza los atributos de BT_SESION al emitirla y borrarla, incluida la recuperación de DA-22.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class CookieSesion {
    private final boolean secure;

    /**
     * Aplica la seguridad de transporte del perfil a todas las cookies de sesión.
     * @param secure si el navegador debe enviar BT_SESION exclusivamente mediante HTTPS
     */
    public CookieSesion(@Value("${barberturno.seguridad.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    /**
     * Prepara una sesión de ocho horas, inaccesible desde JavaScript y limitada al mismo sitio (§7.1).
     * @param token JWT firmado que identifica la sesión válida
     * @return cookie con Path=/, HttpOnly, SameSite=Strict y Secure según el perfil
     */
    public ResponseCookie emitir(String token) {
        return cookie(token).maxAge(JwtService.VIGENCIA).build();
    }

    /**
     * Expira la sesión conservando sus atributos para que el navegador elimine la cookie original (DA-22).
     * @return cookie vacía con Max-Age=0 y los mismos atributos de emisión
     */
    public ResponseCookie borrar() {
        return cookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String valor) {
        return ResponseCookie.from("BT_SESION", valor).httpOnly(true)
                .secure(secure).sameSite("Strict").path("/");
    }
}