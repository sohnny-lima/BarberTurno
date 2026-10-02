package pe.barberturno.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistencia de avisos internos RF-16; el servicio debe limitar la lectura al destinatario autenticado.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
}
