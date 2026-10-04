package pe.barberturno.reservations;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.time.TiempoNegocio;

/**
 * Criterio único de inicio y recursos para historial RF-13 y reportes RF-14 (RN-22).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class ReservaFiltros {
    private ReservaFiltros() { }

    /**
     * Construye predicados sin fetch ni restricciones de estado implícitas; incluye días de Lima completos.
     * @param clienteId propietario opcional ya autorizado
     * @param barberoId perfil opcional ya autorizado
     * @param servicioId servicio opcional, incluidos los inactivos
     * @param estado estado opcional; null incluye los seis estados
     * @param desde primer día inclusivo opcional
     * @param hasta último día inclusivo opcional, previamente validado
     * @return especificación reutilizable en contenido, conteo y GROUP BY
     */
    public static Specification<Reserva> criterio(Long clienteId, Long barberoId, Long servicioId,
            EstadoReserva estado, LocalDate desde, LocalDate hasta) {
        return (raiz, consulta, cb) -> {
            var condiciones = new ArrayList<Predicate>();
            if (clienteId != null) condiciones.add(cb.equal(raiz.get("cliente").get("id"), clienteId));
            if (barberoId != null) condiciones.add(cb.equal(raiz.get("barbero").get("id"), barberoId));
            if (servicioId != null) condiciones.add(cb.equal(raiz.get("servicio").get("id"), servicioId));
            if (estado != null) condiciones.add(cb.equal(raiz.get("estado"), estado));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(raiz.get("inicio"), TiempoNegocio.inicioDelDia(desde)));
            if (hasta != null) condiciones.add(cb.lessThan(raiz.get("inicio"), TiempoNegocio.finDelDia(hasta)));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    /**
     * Aplica el mismo límite inclusivo a agenda y reportes; las reservas propias admiten fechas opcionales.
     * @param desde primer día, requerido cuando obligatorio es true
     * @param hasta último día, requerido cuando obligatorio es true
     * @param obligatorio exige ambos extremos y máximo 366 días inclusivos
     * @throws NegocioException RANGO_FECHAS_INVALIDO si faltan extremos requeridos, están invertidos,
     * superan 366 días o el fin no puede convertirse a medianoche siguiente
     */
    public static void validarRango(LocalDate desde, LocalDate hasta, boolean obligatorio) {
        boolean incompleto = obligatorio && (desde == null || hasta == null);
        boolean invertido = desde != null && hasta != null && desde.isAfter(hasta);
        boolean excesivo = obligatorio && desde != null && hasta != null
                && ChronoUnit.DAYS.between(desde, hasta) + 1 > 366;
        if (incompleto || invertido || excesivo || LocalDate.MAX.equals(hasta)) {
            throw new NegocioException(ErrorCodigo.RANGO_FECHAS_INVALIDO,
                    "Indique un rango ordenado; la agenda y los reportes admiten como máximo 366 días inclusivos.");
        }
    }
}
