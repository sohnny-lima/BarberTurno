package pe.barberturno.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.auth.dto.*;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.users.Usuario;

/**
 * API de identidad en /api/auth; permite registro y login públicos y exige CSRF en toda escritura (§7.2).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final JwtService jwt;
    private final UsuarioActual actual;
    private final boolean secure;

    /**
     * Inyecta identidad, firmante y servicios; aplica Secure a la cookie según la configuración del entorno.
     * @param auth servicio no nulo de identidad y revalidación
     * @param jwt servicio no nulo que emite sesiones firmadas
     * @param actual identidad revalidada; nula solo en consultas públicas
     * @param secure si BT_SESION usa Secure; true en producción
     */
    public AuthController(AuthService auth, JwtService jwt, UsuarioActual actual,
            @Value("${barberturno.seguridad.cookie-secure:false}") boolean secure) {
        this.auth = auth;
        this.jwt = jwt;
        this.actual = actual;
        this.secure = secure;
    }

    /**
     * GET /api/auth/sesion: CLIENTE, BARBERO y ADMIN reciben su identidad; sin sesión devuelve 401
     * NO_AUTENTICADO. La cadena de seguridad emite XSRF-TOKEN también sin sesión.
     * @return representación pública resultante de la operación
     */
    @GetMapping("/sesion")
    public UsuarioSesionDto sesion() { return auth.sesion(actual.id()); }

    /**
     * POST /api/auth/registro público: crea CLIENTE (201) y fija BT_SESION. Exige CSRF; rechaza VALIDACION
     * (400), CORREO_DUPLICADO (409) o PROHIBIDO (403) ante CSRF inválido.
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @return identidad pública y cookie BT_SESION, con el estado HTTP indicado
     */
    @PostMapping("/registro")
    public ResponseEntity<UsuarioSesionDto> registrar(@Valid @RequestBody RegistroDto datos) {
        return respuesta(auth.registrar(datos), HttpStatus.CREATED);
    }

    /**
     * POST /api/auth/login público: autentica y fija BT_SESION (200). Exige CSRF; rechaza CREDENCIALES_INVALIDAS
     * o CUENTA_BLOQUEADA_TEMPORALMENTE (401), y PROHIBIDO (403) por CSRF.
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @return identidad pública y cookie BT_SESION, con el estado HTTP indicado
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioSesionDto> login(@Valid @RequestBody LoginDto datos) {
        return respuesta(auth.login(datos), HttpStatus.OK);
    }

    /**
     * PUT /api/auth/password: CLIENTE, BARBERO y ADMIN cambian su credencial con CSRF. Revoca sesiones previas y
     * renueva BT_SESION (204); rechaza VALIDACION (400), NO_AUTENTICADO (401) o PROHIBIDO (403).
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @return respuesta 204 con cookie BT_SESION actualizada
     */
    @PutMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambiarPasswordDto datos) {
        Usuario usuario = auth.cambiarPassword(actual.id(), datos);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                cookie(jwt.emitir(usuario)).maxAge(JwtService.VIGENCIA).build().toString()).build();
    }

    /**
     * POST /api/auth/logout público e idempotente: borra BT_SESION (204), incluso caducada. Exige CSRF (403
     * PROHIBIDO si falta o es incorrecto); no revoca otras sesiones.
     * @return respuesta 204 con cookie BT_SESION actualizada
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                cookie("").maxAge(0).build().toString()).build();
    }

    private ResponseEntity<UsuarioSesionDto> respuesta(Usuario usuario, HttpStatus estado) {
        return ResponseEntity.status(estado).header(HttpHeaders.SET_COOKIE,
                cookie(jwt.emitir(usuario)).maxAge(JwtService.VIGENCIA).build().toString())
                .body(auth.sesion(usuario));
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String valor) {
        return ResponseCookie.from("BT_SESION", valor).httpOnly(true)
                .secure(secure).sameSite("Strict").path("/");
    }
}
