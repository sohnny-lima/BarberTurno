package pe.barberturno.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistencia de los cambios de reserva para RF-17 y RN-15; la transacción la dirige el servicio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface AuditoriaRepository extends JpaRepository<AuditoriaReserva, Long> {
}
