package pe.barberturno.common.config;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee reglas del negocio de la transacción, sin caché ni parámetros globales de reserva (V2-DA-30).
 * Utiliza Hibernate y su guarda transaccional; no introduce JDBC directo.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class ReglasNegocioService {
    private final EntityManager entidades;
    private final RejillaReserva rejilla;
    private final Validator validador;

    /**
     * Prepara la lectura dentro de la transacción del servicio llamador.
     * @param entidades sesión sometida a la guarda de V2-DA-23
     * @param rejilla único parámetro que sigue siendo global
     * @param validador validaciones conservadas de ParametrosReserva
     */
    public ReglasNegocioService(EntityManager entidades, RejillaReserva rejilla, Validator validador) {
        this.entidades = entidades;
        this.rejilla = rejilla;
        this.validador = validador;
    }

    /**
     * Obtiene una instantánea validada de las cinco reglas de la fila del negocio actual.
     * @return reglas locales y rejilla global para políticas puras y permisos del DTO
     * @throws jakarta.persistence.NoResultException si falta el contexto o la fila del negocio
     * @throws ConstraintViolationException si los parámetros persistidos incumplen sus validaciones
     */
    public ParametrosReserva reservas() {
        Object[] fila = (Object[]) entidades.createNativeQuery("""
                select anticipacion_cambio_cliente_min, horizonte_dias, limite_reservas_activas,
                    confirmacion_manual, tolerancia_inicio_min
                from negocio where id=app_negocio_actual()
                """, Object[].class).getSingleResult();
        var parametros = new ParametrosReserva(Duration.ofMinutes(((Number) fila[0]).longValue()),
                ((Number) fila[1]).intValue(), rejilla.rejillaMin(), ((Number) fila[2]).intValue(),
                (Boolean) fila[3], ((Number) fila[4]).intValue());
        var errores = validador.validate(parametros);
        if (!errores.isEmpty()) throw new ConstraintViolationException(errores);
        return parametros;
    }

    /**
     * Lee el cupo local después del bloqueo asesor de RN-19.
     * @return máximo de profesionales activos del negocio de la transacción
     * @throws jakarta.persistence.NoResultException si falta contexto o negocio
     */
    public int limiteBarberosActivos() {
        return ((Number) entidades.createNativeQuery(
                "select limite_barberos_activos from negocio where id=app_negocio_actual()", Integer.class)
                .getSingleResult()).intValue();
    }
}
