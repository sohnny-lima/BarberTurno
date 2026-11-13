package pe.barberturno.platform;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.platform.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import pe.barberturno.auth.AuthService;
import pe.barberturno.auth.JwtService;
import pe.barberturno.auth.dto.UsuarioSesionDto;
import pe.barberturno.common.security.CookieSesion;

/**
 * Gestión local y capacidades globales; CSRF obligatorio en todas las escrituras.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
@RequestMapping("/api/invitaciones")
public class InvitacionController {
    private final InvitacionService invitaciones;
    private final AuthService auth;
    private final JwtService jwt;
    private final CookieSesion cookies;
    private final CsrfTokenRepository csrf;

    /**
     * Conecta las rutas con la autorización y las transacciones del servicio.
     * @param invitaciones emisión, consulta y consumo atómicos
     * @param auth proyección global de la nueva identidad sin usar el alias de negocio
     * @param jwt firmante compartido de sesión
     * @param cookies política común de emisión de BT_SESION
     * @param csrf renovación del token tras registrar la cuenta (DA-26)
     */
    public InvitacionController(InvitacionService invitaciones, AuthService auth, JwtService jwt,
            CookieSesion cookies, CsrfTokenRepository csrf) {
        this.invitaciones = invitaciones; this.auth = auth; this.jwt = jwt;
        this.cookies = cookies; this.csrf = csrf;
    }

    /**
     * Crea la cuenta del correo invitado y su membresía; inicia sesión renovando ambas cookies.
     * La capacidad es pública y CSRF obligatorio, incluso si hay una sesión previa.
     * @param token secreto que acredita el correo y rol, nunca tomados del cuerpo
     * @param datos identidad y consentimiento validados
     * @param peticion solicitud cuyo CSRF validó la cadena
     * @param respuesta salida que añade BT_SESION y XSRF-TOKEN con su política compartida
     * @return 201 con la identidad global y sin caché
     * @throws NegocioException si la capacidad, el correo o la validación impiden el alta atómica
     */
    @PostMapping("/{token}/registro")
    public ResponseEntity<UsuarioSesionDto> registrar(@PathVariable String token,
            @Valid @RequestBody RegistroInvitadoDto datos, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        var usuario = invitaciones.registrar(token, datos);
        var dto = auth.sesion(usuario);
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookies.emitir(jwt.emitir(usuario)).toString());
        csrf.saveToken(csrf.generateToken(peticion), peticion, respuesta);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(dto);
    }

    /**
     * Emite el enlace una sola vez dentro del negocio resuelto.
     * @param datos correo y rol solicitado por el gestor
     * @param actor personal revalidado en ese negocio
     * @return 201 con enlace no almacenable en caché
     * @throws NegocioException si falta permiso o ya existe una pendiente
     */
    @PostMapping
    public ResponseEntity<InvitacionEmitidaDto> crear(@Valid @RequestBody CrearInvitacionDto datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(invitaciones.crear(datos, actor));
    }

    /**
     * Lista solo pendientes locales sin recuperar tokens.
     * @param actor gestor del negocio actual
     * @return metadatos de gestión sin hash ni enlace
     * @throws NegocioException si falta permiso
     */
    @GetMapping
    public List<InvitacionDto> listar(@AuthenticationPrincipal UsuarioAutenticado actor) { return invitaciones.listar(actor); }

    /**
     * Revoca una pendiente del ámbito actual con permiso sobre su rol.
     * @param id identificador local de la pendiente
     * @param actor personal revalidado
     * @return 200 sin cuerpo
     * @throws NegocioException si es ajena, no está vigente o falta permiso
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revocar(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        invitaciones.revocar(id, actor); return ResponseEntity.ok().build();
    }

    /**
     * Consulta pública de una capacidad global, sin elegir un negocio desde el navegador.
     * @param token secreto opaco recibido en el enlace
     * @return información mínima con correo enmascarado y sin caché
     * @throws NegocioException si el enlace no es válido
     */
    @GetMapping("/{token}")
    public ResponseEntity<InvitacionPublicaDto> consultar(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invitaciones.consultar(token));
    }

    /**
     * Consume la capacidad para la identidad de sesión; los campos de un cuerpo no se leen.
     * @param token secreto opaco que acredita rol y negocio
     * @param actor identidad autenticada que debe acreditar el correo invitado
     * @return 200 sin cuerpo ni enlace reutilizable
     * @throws NegocioException si el enlace, correo, membresía o estado impiden aceptar
     */
    @PostMapping("/{token}/aceptacion")
    public ResponseEntity<Void> aceptar(@PathVariable String token, @AuthenticationPrincipal UsuarioAutenticado actor) {
        invitaciones.aceptar(token, actor); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
    }
}
