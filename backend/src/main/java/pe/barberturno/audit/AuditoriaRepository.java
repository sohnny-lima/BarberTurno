package pe.barberturno.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de AuditoriaReserva. */
public interface AuditoriaRepository extends JpaRepository<AuditoriaReserva, Long> {
}
