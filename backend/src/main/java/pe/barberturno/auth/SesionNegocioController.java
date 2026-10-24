package pe.barberturno.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.barberturno.auth.dto.UsuarioSesionDto;
import pe.barberturno.common.security.UsuarioActual;

/**
 * Proyecta la sesión revalidada dentro del negocio resuelto por ruta o alias.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
public class SesionNegocioController {
    private final AuthService auth;
    private final UsuarioActual actual;

    /**
     * Comparte la identidad y la proyección de sesión, sin elegir un negocio desde parámetros HTTP.
     * @param auth proyección transaccional de identidad y rol local
     * @param actual cuenta autenticada y revalidada
     */
    public SesionNegocioController(AuthService auth, UsuarioActual actual) { this.auth = auth; this.actual = actual; }

    /**
     * Devuelve el rol efectivo en el negocio de la ruta, o null si la cuenta no pertenece a él.
     * @return identidad y papel local vigente
     */
    @GetMapping("/api/sesion")
    public UsuarioSesionDto sesion() { return auth.sesion(actual.id()); }
}
