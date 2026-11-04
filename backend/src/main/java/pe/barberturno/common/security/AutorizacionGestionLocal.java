package pe.barberturno.common.security;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;

/**
 * Revalida con SQL la cuenta y la membresía actual, incluso después de esperar un bloqueo.
 * No confía en roles del principal ni en entidades retenidas por el contexto de persistencia.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class AutorizacionGestionLocal {
    private final EntityManager entidades;
    private final ProveedorNegocioActual negocio;

    AutorizacionGestionLocal(EntityManager entidades, ProveedorNegocioActual negocio) {
        this.entidades = entidades; this.negocio = negocio;
    }

    /**
     * Comprueba gestión vigente en el negocio de la transacción.
     * @param usuarioId identidad de la sesión, nunca del cuerpo HTTP
     * @param soloPropietario exige titularidad para anonimizar
     * @return negocio autorizado
     * @throws NegocioException PROHIBIDO si falta cuenta o membresía activa con el rol exigido
     */
    public long exigir(long usuarioId, boolean soloPropietario) {
        long id = negocio.negocioActual().orElseThrow();
        var roles = soloPropietario ? java.util.List.of("PROPIETARIO") : java.util.List.of("PROPIETARIO", "ENCARGADO");
        if (entidades.createNativeQuery("""
                SELECT m.id FROM membresia m JOIN usuario u ON u.id = m.usuario_id
                WHERE m.negocio_id = :negocio AND m.usuario_id = :usuario AND m.estado = 'ACTIVA'
                    AND u.activo AND m.rol IN (:roles)
                """, Long.class).setParameter("negocio", id).setParameter("usuario", usuarioId)
                .setParameter("roles", roles).getResultList().isEmpty()) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "Se requiere una membresía de gestión activa.");
        }
        return id;
    }
}
