package pe.barberturno.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio de Bloqueo. */
public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    /** Busca los bloqueos que se cruzan con el intervalo semiabierto indicado. */
    @Query("""
            select b from Bloqueo b
            where b.barbero.id = :barberoId and b.inicio < :fin and b.fin > :inicio
            order by b.inicio, b.id
            """)
    List<Bloqueo> buscarQueSeCruzan(@Param("barberoId") Long barberoId,
            @Param("inicio") Instant inicio, @Param("fin") Instant fin);
}
