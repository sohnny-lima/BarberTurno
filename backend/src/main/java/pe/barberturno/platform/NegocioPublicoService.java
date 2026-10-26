package pe.barberturno.platform;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.*;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.platform.dto.NegocioPublicoDto;

/**
 * Proyecta exclusivamente los parámetros públicos del negocio de la ruta, sin caché ni ids internos.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
public class NegocioPublicoService {
    private final EntityManager entidades;
    private final ProveedorNegocioActual negocio;

    /**
     * Recibe el ámbito resuelto por el servidor y la sesión transaccional.
     * @param entidades acceso a la tabla de plataforma con guarda de transacción
     * @param negocio negocio resuelto por la ruta
     */
    public NegocioPublicoService(EntityManager entidades, ProveedorNegocioActual negocio) {
        this.entidades = entidades; this.negocio = negocio;
    }

    /**
     * Lee los seis campos aprobados y vuelve a comprobar el estado activo en esta transacción.
     * @return parámetros actuales del negocio solicitado
     * @throws NegocioException si el negocio no existe o está suspendido
     */
    @Transactional(readOnly = true)
    public NegocioPublicoDto consultar() {
        var filas = entidades.createNativeQuery("""
                select slug, nombre, horizonte_dias, anticipacion_cambio_cliente_min,
                    limite_reservas_activas, confirmacion_manual
                from negocio where id=:id and estado='ACTIVO'
                """, Object[].class).setParameter("id", negocio.negocioActual().orElseThrow()).getResultList();
        if (filas.isEmpty()) throw new NegocioException(ErrorCodigo.NO_ENCONTRADO, "No se encontró el negocio.");
        Object[] f = (Object[]) filas.getFirst();
        return new NegocioPublicoDto((String) f[0], (String) f[1], ((Number) f[2]).intValue(),
                ((Number) f[3]).intValue(), ((Number) f[4]).intValue(), (Boolean) f[5]);
    }
}
