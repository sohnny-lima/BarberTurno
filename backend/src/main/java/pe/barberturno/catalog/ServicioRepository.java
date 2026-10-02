package pe.barberturno.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas del catálogo RF-04 y bloqueo de sus escrituras; respeta el índice servicio_nombre_uk de V1.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    /**
     * Lee sin bloqueo; filtra activos salvo permiso para incluir inactivos y ordena por lower(nombre), coherente
     * con servicio_nombre_uk.
     * @param incluirInactivos true incluye inactivos; el controlador autoriza solo a ADMIN
     * @return resultados con el orden descrito, sin comprobación adicional de permisos
     */
    @Query("select s from Servicio s where :incluirInactivos = true or s.activo = true order by lower(s.nombre)")
    List<Servicio> listar(@Param("incluirInactivos") boolean incluirInactivos);

    /**
     * Busca sin bloqueo por lower, igual que servicio_nombre_uk; PostgreSQL garantiza unicidad concurrente.
     * @param nombre nombre visible no nulo; DTO o servicio valida longitud y formato
     * @return resultado opcional; vacío si no existe el recurso
     */
    @Query("select s from Servicio s where lower(s.nombre) = lower(:nombre)")
    Optional<Servicio> buscarPorNombre(@Param("nombre") String nombre);

    /**
     * Toma PESSIMISTIC_WRITE hasta fin de transacción para serializar edición y estado del servicio.
     * @param id identificador persistente positivo del recurso, no nulo
     * @return resultado opcional; vacío si no existe el recurso
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Servicio s where s.id = :id")
    Optional<Servicio> bloquearPorId(@Param("id") Long id);
}
