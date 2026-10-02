package pe.barberturno.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistencia de intervalos semanales RN-17; el servicio valida solapes bajo el bloqueo ② del barbero.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface JornadaRepository extends JpaRepository<Jornada, Long> {
}
