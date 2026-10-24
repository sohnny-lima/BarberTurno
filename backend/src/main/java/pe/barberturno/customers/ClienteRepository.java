package pe.barberturno.customers;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.tenancy.RepositorioTransaccional;

/**
 * Acceso a fichas limitado al negocio de cada transacción por RLS FORCE.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Transactional(readOnly = true)
public interface ClienteRepository extends RepositorioTransaccional<Cliente, Long> {
    /**
     * Traduce el id de cuenta del contrato web a su ficha local.
     * @param usuarioId identidad global buscada
     * @return ficha visible, si existe
     */
    Optional<Cliente> findByUsuarioId(long usuarioId);

    /**
     * Traduce la cuenta sin precargar la entidad que deberá revalidarse tras esperar por ①.
     * @param usuarioId identidad global del contrato web
     * @return id escalar de la ficha del negocio actual, si existe
     */
    @Query("select c.id from Cliente c where c.usuario.id = :usuarioId")
    Optional<Long> buscarIdPorUsuario(@Param("usuarioId") long usuarioId);

    /**
     * Revalida el papel de cliente en cada petición.
     * @param usuarioId identidad global autenticada
     * @return si tiene una ficha activa en el negocio actual
     */
    boolean existsByUsuarioIdAndActivoTrue(long usuarioId);

    /**
     * Toma el bloqueo ① por ficha antes de los profesionales ② y la reserva ③.
     * @param id identificador de ficha visible
     * @return ficha bloqueada, si pertenece al negocio actual
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> bloquearPorId(@Param("id") Long id);
}
