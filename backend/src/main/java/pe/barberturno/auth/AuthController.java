package pe.barberturno.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.auth.dto.*;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.users.Usuario;

/** Endpoints de sesión mediante cookies; el cambio de contraseña corresponde a T-11. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final JwtService jwt;
    private final UsuarioActual actual;
    private final boolean secure;

    public AuthController(AuthService auth, JwtService jwt, UsuarioActual actual,
            @Value("${barberturno.seguridad.cookie-secure:false}") boolean secure) {
        this.auth = auth;
        this.jwt = jwt;
        this.actual = actual;
        this.secure = secure;
    }

    @GetMapping("/sesion")
    public UsuarioSesionDto sesion() { return auth.sesion(actual.id()); }

    @PostMapping("/registro")
    public ResponseEntity<UsuarioSesionDto> registrar(@Valid @RequestBody RegistroDto datos) {
        return respuesta(auth.registrar(datos), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioSesionDto> login(@Valid @RequestBody LoginDto datos) {
        return respuesta(auth.login(datos), HttpStatus.OK);
    }

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
