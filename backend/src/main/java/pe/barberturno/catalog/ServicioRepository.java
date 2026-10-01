package pe.barberturno.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio de Servicio. */
public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    /** Lista alfabética del catálogo con el mismo criterio de nombre del índice único. */
    @Query("select s from Servicio s where :incluirInactivos = true or s.activo = true order by lower(s.nombre)")
    List<Servicio> listar(@Param("incluirInactivos") boolean incluirInactivos);

    /** Busca duplicados con lower, igual que servicio_nombre_uk en PostgreSQL. */
    @Query("select s from Servicio s where lower(s.nombre) = lower(:nombre)")
    Optional<Servicio> buscarPorNombre(@Param("nombre") String nombre);

    /** Serializa edición y cambio de estado para evitar sobrescrituras concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Servicio s where s.id = :id")
    Optional<Servicio> bloquearPorId(@Param("id") Long id);
}
