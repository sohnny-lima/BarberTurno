package pe.barberturno.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta identidades RN-24 y serializa acceso y cambios; ofrece el bloqueo ① del protocolo de reservas (§8).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca correo ya normalizado RN-24, sin bloqueo ni orden adicional.
     * @param correo correo de acceso único sin distinguir mayúsculas; normalizado según RN-24, no nulo
     * @return resultado opcional; vacío si no existe el recurso
     */
    Optional<Usuario> findByCorreo(String correo);

    /**
     * Comprueba rol activo sin bloqueo para omitir la creación del ADMIN inicial.
     * @param rol rol persistido que determina los permisos del servidor; no nulo
     * @return true si el criterio se cumple; false en otro caso
     */
    boolean existsByRolAndActivoTrue(Rol rol);

    /**
     * Serializa intentos de acceso con PESSIMISTIC_WRITE sobre usuario hasta fin de transacción.
     * @param correo correo de acceso único sin distinguir mayúsculas; normalizado según RN-24, no nulo
     * @return resultado opcional; vacío si no existe el recurso
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.correo = :correo")
    Optional<Usuario> bloquearPorCorreo(@Param("correo") String correo);

    /**
     * Toma PESSIMISTIC_WRITE: bloqueo ① antes de barberos ② y reserva ③ al crear o reprogramar; serializa
     * también perfil y contraseña.
     * @param id identificador persistente positivo del recurso, no nulo
     * @return resultado opcional; vacío si no existe el recurso
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearPorId(@Param("id") Long id);
}
