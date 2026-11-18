package pe.barberturno.notifications;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pe.barberturno.common.tenancy.RepositorioTransaccional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistencia de avisos internos RF-16; el servicio debe limitar la lectura al destinatario autenticado.
 * Las consultas declaradas abren una transacción de lectura con contexto de negocio;
 * RepositorioTransaccional declara las escrituras heredadas para evitar que sean de solo lectura.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Transactional(readOnly = true)
public interface NotificacionRepository extends RepositorioTransaccional<Notificacion, Long> {
    /**
     * Restringe contenido y total al destinatario, con filtro opcional de lectura.
     * @param usuarioId identidad autenticada, nunca proporcionada por el cliente HTTP
     * @param negocioId ámbito explícito de la lista y su conteo, adicional a RLS
     * @param soloNoLeidas true para excluir los avisos leídos
     * @param pagina límites y orden descendente estable por fecha e identidad
     * @return página de avisos propios
     */
    @Query("select n from Notificacion n where n.negocioId = :negocioId and n.usuario.id = :usuarioId and (:soloNoLeidas = false or n.leida = false)")
    Page<Notificacion> listarPropias(@Param("negocioId") long negocioId, @Param("usuarioId") long usuarioId,
            @Param("soloNoLeidas") boolean soloNoLeidas, Pageable pagina);

    /**
     * Cuenta en PostgreSQL sin cargar entidades y aprovecha notificacion_usuario_ix.
     * @param usuarioId destinatario autenticado
     * @param negocioId ámbito explícito del contador, adicional a RLS
     * @return número de avisos propios sin leer
     */
    @Query("select count(n) from Notificacion n where n.negocioId = :negocioId and n.usuario.id = :usuarioId and n.leida = false")
    long contarNoLeidas(@Param("negocioId") long negocioId, @Param("usuarioId") long usuarioId);

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
     * @param negocioId ámbito explícito de la actualización, adicional a RLS
     * @return cantidad de avisos cuya lectura cambió
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notificacion n set n.leida = true where n.negocioId = :negocioId and n.usuario.id = :usuarioId and n.leida = false")
    int marcarTodasLeidas(@Param("negocioId") long negocioId, @Param("usuarioId") long usuarioId);
}
