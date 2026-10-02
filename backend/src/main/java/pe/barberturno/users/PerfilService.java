package pe.barberturno.users;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.error.ManejadorErrores.ErrorCampo;
import pe.barberturno.users.dto.*;

/**
 * Consulta y actualiza exclusivamente los datos editables del perfil propio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class PerfilService {
    private final UsuarioRepository usuarios;
    private final Clock clock;

    /**
     * Inyecta identidades y reloj para fechar cambios del perfil propio.
     * @param usuarios identidades persistidas
     * @param clock reloj para la fecha de actualización
     */
    public PerfilService(UsuarioRepository usuarios, Clock clock) {
        this.usuarios = usuarios;
        this.clock = clock;
    }

    /**
     * Proyecta el perfil del usuario activo sin datos de acceso; aplica RF-03 a la identidad autenticada.
     * @param id identidad autenticada
     * @return perfil vigente
     * @throws NegocioException si la identidad no existe o está inactiva (NO_AUTENTICADO)
     */
    @Transactional(readOnly = true)
    public PerfilDto obtener(long id) {
        return dto(usuarios.findById(id).filter(Usuario::isActivo).orElseThrow(this::sinSesion));
    }

    /**
     * Serializa con PESSIMISTIC_WRITE frente a login y contraseña; exige teléfono para CLIENTE y conserva correo
     * (RF-03).
     * @param id identidad autenticada
     * @param datos campos editables validados por MVC
     * @return perfil actualizado
     * @throws NegocioException si la identidad no existe o está inactiva (NO_AUTENTICADO), o CLIENTE omite
     * teléfono (VALIDACION)
     */
    @Transactional
    public PerfilDto actualizar(long id, ActualizarPerfilDto datos) {
        Usuario usuario = usuarios.bloquearPorId(id).filter(Usuario::isActivo).orElseThrow(this::sinSesion);
        if (usuario.getRol() == Rol.CLIENTE && datos.telefono() == null) {
            throw new NegocioException("Revise los datos de la solicitud.",
                    List.of(new ErrorCampo("telefono", "El teléfono es obligatorio para el cliente.")));
        }
        usuario.actualizarPerfil(datos.nombre(), datos.telefono(), clock.instant());
        return dto(usuario);
    }

    private PerfilDto dto(Usuario usuario) {
        return new PerfilDto(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getRol(), usuario.getTelefono());
    }

    private NegocioException sinSesion() {
        return new NegocioException(ErrorCodigo.NO_AUTENTICADO, "Se requiere una sesión válida.");
    }
}