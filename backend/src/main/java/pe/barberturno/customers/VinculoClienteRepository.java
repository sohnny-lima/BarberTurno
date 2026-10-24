package pe.barberturno.customers;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única lectura del índice de plataforma: no expone filas, identidades ni escrituras (V2-DA-26/28).
 * Usa Cliente como tipo de dominio de Spring Data sin heredar operaciones sobre él.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Transactional(readOnly = true)
public interface VinculoClienteRepository extends Repository<Cliente, Long> {
    /**
     * Comprueba identidad compartida sin revelar el negocio ajeno ni los datos de la ficha.
     * @param usuarioId cuenta cuya identidad pretende cambiar el negocio
     * @param negocioId negocio actual excluido de la comprobación
     * @return si existe ficha, incluso inactiva, en algún otro negocio
     */
    @Query(value = "select exists(select 1 from vinculo_cliente where usuario_id = :usuarioId and negocio_id <> :negocioId)", nativeQuery = true)
    boolean existeEnOtroNegocio(@Param("usuarioId") long usuarioId, @Param("negocioId") long negocioId);
}
