package pe.barberturno.customers;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecturas mínimas del índice de plataforma: identidad compartida y negocios propios activos.
 * No expone datos personales ni escrituras (V2-DA-26/28/35).
 * Usa Cliente como tipo de dominio de Spring Data sin heredar operaciones sobre él.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Transactional(readOnly = true)
public interface VinculoClienteRepository extends Repository<Cliente, Long> {
    /**
     * Lee el índice mantenido por el disparador, sin consultar ni abrir la RLS de cliente.
     * @param usuarioId identidad del principal autenticado, nunca recibida del navegador
     * @return negocios con ficha propia activa, sin datos personales de las fichas
     */
    @Query(value = "select negocio_id from vinculo_cliente where usuario_id = :usuarioId and activo", nativeQuery = true)
    java.util.List<Long> listarNegociosPropiosActivos(@Param("usuarioId") long usuarioId);

    /**
     * Comprueba identidad compartida sin revelar el negocio ajeno ni los datos de la ficha.
     * @param usuarioId cuenta cuya identidad pretende cambiar el negocio
     * @param negocioId negocio actual excluido de la comprobación
     * @return si existe ficha, incluso inactiva, en algún otro negocio
     */
    @Query(value = "select exists(select 1 from vinculo_cliente where usuario_id = :usuarioId and negocio_id <> :negocioId)", nativeQuery = true)
    boolean existeEnOtroNegocio(@Param("usuarioId") long usuarioId, @Param("negocioId") long negocioId);
}
