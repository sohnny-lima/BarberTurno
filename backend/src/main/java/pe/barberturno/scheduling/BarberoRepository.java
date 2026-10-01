package pe.barberturno.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio de Barbero. */
public interface BarberoRepository extends JpaRepository<Barbero, Long> {

    /** Bloquea los barberos en orden ascendente para evitar interbloqueos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Barbero b where b.id in :ids order by b.id")
    List<Barbero> bloquearPorIds(@Param("ids") Collection<Long> ids);
}
