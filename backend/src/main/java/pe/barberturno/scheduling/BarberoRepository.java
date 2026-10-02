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
