package pe.barberturno.users;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pe.barberturno.common.security.UsuarioActual;
import pe.barberturno.users.dto.*;

/** Endpoints del perfil propio; el correo no es un campo editable. */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {
    private final PerfilService perfiles;
    private final UsuarioActual actual;

    public PerfilController(PerfilService perfiles, UsuarioActual actual) {
        this.perfiles = perfiles;
        this.actual = actual;
    }

    @GetMapping
    public PerfilDto obtener() { return perfiles.obtener(actual.id()); }

    @PutMapping
    public PerfilDto actualizar(@Valid @RequestBody ActualizarPerfilDto datos) {
        return perfiles.actualizar(actual.id(), datos);
    }
}