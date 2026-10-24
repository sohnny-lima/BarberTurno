package pe.barberturno.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.auth.dto.*;

/**
 * Registro público de cuenta y ficha en el negocio de la ruta, con el mismo contrato y cookies del alias.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@RestController
public class RegistroNegocioController {
    private final AuthController identidad;

    /**
     * Reutiliza validaciones, transacción atómica y renovación de cookies DA-22/26.
     * @param identidad adaptador común de registro, con ámbito ya fijado por el resolutor
     */
    public RegistroNegocioController(AuthController identidad) { this.identidad = identidad; }

    /**
     * Crea la cuenta y su ficha local; ninguna entrada concede membresías ni cambia el negocio.
     * @param datos campos y consentimiento del registro existente
     * @param peticion solicitud que ya superó CSRF
     * @param respuesta salida que recibe la sesión y el token CSRF nuevo
     * @return identidad creada con estado 201
     * @throws pe.barberturno.common.error.NegocioException si el correo ya existe o la contraseña incumple RN-25
     */
    @PostMapping("/api/registro")
    public ResponseEntity<UsuarioSesionDto> registrar(@Valid @RequestBody RegistroDto datos,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        return identidad.registrar(datos, peticion, respuesta);
    }
}
