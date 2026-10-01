package pe.barberturno.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de Notificacion. */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
}
