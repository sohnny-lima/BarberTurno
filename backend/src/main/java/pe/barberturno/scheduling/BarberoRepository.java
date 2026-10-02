package pe.barberturno.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consulta perfiles de atención y toma el bloqueo ② ordenado de disponibilidad (arquitectura §8).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface BarberoRepository extends JpaRepository<Barbero, Long> {

    /**
     * Serializa altas y cambios de estado RN-19 hasta fin de transacción; clave global 141414 reservada a T-14.
     * Se toma antes de usuario y barbero. La función en FROM evita mapear el tipo PostgreSQL void.
     * @return centinela 1 después de adquirir pg_advisory_xact_lock
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(141414)", nativeQuery = true)
    int bloquearLimiteActivos();

    /**
     * Cuenta perfiles habilitados después del bloqueo global RN-19, sin depender del rol vinculado.
     * @return cantidad actual de perfiles activos
     */
    long countByActivoTrue();

    /**
     * Carga perfiles y usuarios para la proyección RF-05, ordenados por nombre sin distinguir mayúsculas e id.
     * @param incluirInactivos permiso concedido solo a ADMIN por el controlador
     * @return perfiles ordenados con usuario disponible
     */
    @Query("select b from Barbero b join fetch b.usuario u where :incluirInactivos = true or b.activo = true order by lower(u.nombre), b.id")
    List<Barbero> listar(@Param("incluirInactivos") boolean incluirInactivos);

    /**
     * Obtiene solo la identidad vinculada para bloquear usuario antes que perfil, sin cargar entidades obsoletas.
     * @param id identificador del perfil buscado
     * @return identidad vinculada o vacío cuando el perfil no existe
     */
    @Query("select b.usuario.id from Barbero b where b.id = :id")
    java.util.Optional<Long> buscarUsuarioId(@Param("id") Long id);

    /**
     * Lee id del perfil sin bloqueo ni carga de relaciones; no filtra por estado activo.
     * @param usuarioId identificador persistente no nulo de la cuenta
     * @return resultado opcional; vacío si no existe el recurso
     */
    @Query("select b.id from Barbero b where b.usuario.id = :usuarioId")
    java.util.Optional<Long> buscarIdPorUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Toma PESSIMISTIC_WRITE en orden ascendente de id: bloqueo ② después del cliente ① y antes de reserva ③
     * (§8).
     * @param ids identificadores no nulos de perfiles; la consulta impone orden ascendente
     * @return resultados con el orden descrito, sin comprobación adicional de permisos
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Barbero b where b.id in :ids order by b.id")
    List<Barbero> bloquearPorIds(@Param("ids") Collection<Long> ids);
}
