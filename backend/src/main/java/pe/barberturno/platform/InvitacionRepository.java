package pe.barberturno.platform;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Superficie mínima V2-DA-26: ámbito explícito o capacidad secreta de consulta y aceptación global.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Transactional(readOnly = true)
public interface InvitacionRepository extends Repository<Invitacion, Long> {
    /**
     * Serializa reenvíos sobre la primera invitación del propietario, creada junto con el negocio.
     * @param negocioId negocio explícitamente autorizado por plataforma
     * @param rol PROPIETARIO para localizar la acreditación inicial
     * @return primera emisión histórica, que conserva el correo destinatario original
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitacion> findFirstByNegocioIdAndRolOrderByIdAsc(long negocioId, RolNegocio rol);
    /**
     * Consulta global limitada a la capacidad secreta, nunca por id de plataforma sin negocio.
     * @param tokenHash SHA-256 calculado del token recibido
     * @return única invitación identificada por esa capacidad
     */
    Optional<Invitacion> findByTokenHash(byte[] tokenHash);

    /**
     * Serializa consumo y revocación sobre la misma fila hasta confirmar la transacción.
     * @param tokenHash SHA-256 del secreto presentado
     * @return invitación exclusivamente identificada por el secreto
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitacion i where i.tokenHash = :tokenHash")
    Optional<Invitacion> bloquearPorToken(@Param("tokenHash") byte[] tokenHash);

    /**
     * Localiza y bloquea una invitación del negocio autorizado para revocarla.
     * @param negocioId ámbito de la petición
     * @param id invitación solicitada
     * @return fila de ese negocio, nunca de otro
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitacion i where i.negocioId = :negocioId and i.id = :id")
    Optional<Invitacion> bloquearPorNegocio(@Param("negocioId") long negocioId, @Param("id") long id);

    /**
     * Lista pendientes del negocio, sin exponer secretos ni recuperar invitaciones consumidas.
     * @param negocioId ámbito explícito autorizado
     * @return pendientes ordenadas por identificador
     */
    @Query("select i from Invitacion i where i.negocioId = :negocioId and i.estado = 'PENDIENTE' order by i.id")
    List<Invitacion> listarPendientes(@Param("negocioId") long negocioId);

    /**
     * Persiste una emisión cuyo ámbito y actor proceden del servidor.
     * @param invitacion acreditación con hash, sin token original
     * @return acreditación persistida
     */
    @Transactional
    Invitacion save(Invitacion invitacion);
}
