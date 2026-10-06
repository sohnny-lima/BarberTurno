package pe.barberturno.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import pe.barberturno.auth.dto.*;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.common.security.CookieSesion;
import pe.barberturno.users.Usuario;

/**
 * API de identidad en /api/auth; permite registro y login públicos y exige CSRF en toda escritura (§7.2).
 * Renueva explícitamente XSRF-TOKEN en la misma respuesta al iniciar, registrar o cerrar sesión (DA-26).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.3
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final JwtService jwt;
    private final UsuarioActual actual;
    private final CookieSesion cookies;
    private final CsrfTokenRepository csrf;

    /**
     * Inyecta identidad, firmante y las políticas comunes de sesión DA-22 y renovación CSRF DA-26.
     * @param auth servicio no nulo de identidad y revalidación
     * @param jwt servicio no nulo que emite sesiones firmadas
     * @param actual identidad revalidada; nula solo en consultas públicas
     * @param cookies política compartida de emisión y borrado de BT_SESION según el perfil
     * @param csrf repositorio usado por el filtro SPA para emitir XSRF-TOKEN nuevo al cambiar la sesión
     */
    public AuthController(AuthService auth, JwtService jwt, UsuarioActual actual,
            CookieSesion cookies, CsrfTokenRepository csrf) {
        this.auth = auth;
        this.jwt = jwt;
        this.actual = actual;
        this.cookies = cookies;
        this.csrf = csrf;
    }

    /**
     * GET /api/auth/sesion: CLIENTE, BARBERO y ADMIN reciben su identidad; sin sesión devuelve 401
     * NO_AUTENTICADO. La cadena emite XSRF-TOKEN si falta, también sin sesión, y conserva el token existente (DA-26).
     * @return representación pública resultante de la operación
     */
    @GetMapping("/sesion")
    public UsuarioSesionDto sesion() { return auth.sesion(actual.id()); }

    /**
     * POST /api/auth/registro público: crea CLIENTE (201) y sustituye BT_SESION aun si fue revocada (DA-22). Exige CSRF; rechaza VALIDACION
     * (400), CORREO_DUPLICADO (409) o PROHIBIDO (403) ante CSRF inválido.
     * Tras el éxito emite XSRF-TOKEN nuevo junto con BT_SESION, sin exigir otra lectura (DA-26).
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @param peticion solicitud cuyo CSRF ya fue validado por la cadena de seguridad
     * @param respuesta respuesta servlet que añade ambas cookies con sus políticas compartidas
     * @return identidad pública y estado 201; las cookies se añaden a la respuesta servlet
     */
    @PostMapping("/registro")
    public ResponseEntity<UsuarioSesionDto> registrar(@Valid @RequestBody RegistroDto datos,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        return respuesta(auth.registrar(datos), HttpStatus.CREATED, peticion, respuesta);
    }

    /**
     * POST /api/auth/login público: autentica y sustituye BT_SESION (200), aun si era inválida (DA-22). Exige CSRF; rechaza CREDENCIALES_INVALIDAS
     * o CUENTA_BLOQUEADA_TEMPORALMENTE (401), y PROHIBIDO (403) por CSRF.
     * Tras el éxito emite XSRF-TOKEN nuevo junto con BT_SESION, sin exigir otra lectura (DA-26).
     * @param datos entrada no nula validada por MVC; el servicio aplica las reglas de negocio
     * @param peticion solicitud cuyo CSRF ya fue validado por la cadena de seguridad
     * @param respuesta respuesta servlet que añade ambas cookies con sus políticas compartidas
     * @return identidad pública y estado 200; las cookies se añaden a la respuesta servlet
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioSesionDto> login(@Valid @RequestBody LoginDto datos,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        return respuesta(auth.login(datos), HttpStatus.OK, peticion, respuesta);
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
                cookies.emitir(jwt.emitir(usuario)).toString()).build();
    }

    /**
     * POST /api/auth/logout público e idempotente: borra BT_SESION (204), incluso caducada. Exige CSRF (403
     * PROHIBIDO si falta o es incorrecto); no revoca otras sesiones.
     * Emite XSRF-TOKEN anónimo nuevo en la misma respuesta para poder volver a entrar sin recarga (DA-26).
     * @param peticion solicitud cuyo CSRF ya fue validado, incluso sin sesión válida
     * @param respuesta respuesta servlet que borra BT_SESION y añade XSRF-TOKEN nuevo sin reemplazar cabeceras
     * @return estado 204; las cookies se añaden a la respuesta servlet
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest peticion, HttpServletResponse respuesta) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookies.borrar().toString());
        csrf.saveToken(csrf.generateToken(peticion), peticion, respuesta);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<UsuarioSesionDto> respuesta(Usuario usuario, HttpStatus estado,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        var resultado = ResponseEntity.status(estado).body(auth.sesion(usuario));
        // Ambas cookies se añaden al servlet; ResponseEntity no debe reemplazar Set-Cookie.
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookies.emitir(jwt.emitir(usuario)).toString());
        csrf.saveToken(csrf.generateToken(peticion), peticion, respuesta);
        return resultado;
    }
}
