package pe.barberturno.notifications;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistencia de avisos internos RF-16; el servicio debe limitar la lectura al destinatario autenticado.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    /**
     * Restringe contenido y total al destinatario, con filtro opcional de lectura.
     * @param usuarioId identidad autenticada, nunca proporcionada por el cliente HTTP
     * @param soloNoLeidas true para excluir los avisos leídos
     * @param pagina límites y orden descendente estable por fecha e identidad
     * @return página de avisos propios
     */
    @Query("select n from Notificacion n where n.usuario.id = :usuarioId and (:soloNoLeidas = false or n.leida = false)")
    Page<Notificacion> listarPropias(@Param("usuarioId") long usuarioId,
            @Param("soloNoLeidas") boolean soloNoLeidas, Pageable pagina);

    /**
     * Cuenta en PostgreSQL sin cargar entidades y aprovecha notificacion_usuario_ix.
     * @param usuarioId destinatario autenticado
     * @return número de avisos propios sin leer
     */
    @Query("select count(n) from Notificacion n where n.usuario.id = :usuarioId and n.leida = false")
    long contarNoLeidas(@Param("usuarioId") long usuarioId);

    /**
     * Busca por identidad y destinatario para no revelar avisos ajenos.
     * @param id identidad solicitada
     * @param usuarioId destinatario autenticado
     * @return aviso propio o vacío tanto si falta como si es ajeno
     */
    Optional<Notificacion> findByIdAndUsuarioId(long id, long usuarioId);

    /**
     * Marca en una sola sentencia los avisos propios aún no leídos, sin afectar a otros usuarios.
     * @param usuarioId destinatario autenticado
     * @return cantidad de avisos cuya lectura cambió
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notificacion n set n.leida = true where n.usuario.id = :usuarioId and n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") long usuarioId);
}
