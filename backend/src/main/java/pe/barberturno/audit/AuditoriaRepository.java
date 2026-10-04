package pe.barberturno.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistencia de los cambios de reserva para RF-17 y RN-15; la transacción la dirige el servicio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface AuditoriaRepository extends JpaRepository<AuditoriaReserva, Long> {
    /**
     * Recupera el historial con sus actores sin N+1 y desempata fechas iguales por identidad.
     * @param reservaId reserva previamente autorizada por el servicio
     * @return cambios del más antiguo al más reciente
     */
    @Query("select a from AuditoriaReserva a join fetch a.actor where a.reserva.id = :reservaId order by a.creadoEn, a.id")
    List<AuditoriaReserva> buscarHistorial(@Param("reservaId") long reservaId);
}
