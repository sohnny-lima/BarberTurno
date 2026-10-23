package pe.barberturno.users;

import pe.barberturno.common.tenancy.RepositorioTransaccional;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta identidades RN-24 y serializa acceso y cambios; ofrece el bloqueo ① del protocolo de reservas (§8).
 * Las consultas declaradas abren una transacción de lectura con contexto de negocio;
 * RepositorioTransaccional declara las escrituras heredadas para evitar que sean de solo lectura.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Transactional(readOnly = true)
public interface UsuarioRepository extends RepositorioTransaccional<Usuario, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Usuario> {

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
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.correo = :correo")
    Optional<Usuario> bloquearPorCorreo(@Param("correo") String correo);

    /**
     * Toma PESSIMISTIC_WRITE: bloqueo ① antes de barberos ② y reserva ③ al crear o reprogramar; serializa
     * también perfil y contraseña.
     * @param id identificador persistente positivo del recurso, no nulo
     * @return resultado opcional; vacío si no existe el recurso
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearPorId(@Param("id") Long id);
    /**
     * Serializa cambios de estado antes de bloquear usuarios y contar ADMIN activos.
     * La clave 313131 es exclusiva de RF-19, distinta del cupo de barberos 141414.
     * @return centinela 1 tras adquirir el bloqueo hasta fin de transacción
     */
    @Transactional
    @Query(value = "select 1 from pg_advisory_xact_lock(313131)", nativeQuery = true)
    int bloquearAdministradoresActivos();

    /**
     * Cuenta identidades activas del rol después del bloqueo global de estados RF-19.
     * @param rol categoría de acceso que se cuenta
     * @return número de identidades habilitadas del rol
     */
    long countByRolAndActivoTrue(Rol rol);
}
