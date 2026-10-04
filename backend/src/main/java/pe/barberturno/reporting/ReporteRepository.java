package pe.barberturno.reporting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import pe.barberturno.reporting.dto.ConteoReporteDto;
import pe.barberturno.reservations.EstadoReserva;
import pe.barberturno.reservations.Reserva;

/**
 * Tres consultas GROUP BY de RF-14 con proyecciones escalares; no materializa entidades ni usa N+1.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Repository
public class ReporteRepository {
    private final EntityManager em;

    /**
     * Recibe el contexto de persistencia de la transacción de lectura del servicio.
     * @param em acceso JPA a PostgreSQL para consultas Criteria agregadas
     */
    public ReporteRepository(EntityManager em) { this.em = em; }

    /**
     * Cuenta todos los estados del conjunto filtrado, incluidos los terminales.
     * @param filtros criterio compartido con el historial, sin fetch
     * @return mapa de grupos existentes; el servicio completa los ceros
     */
    public Map<EstadoReserva, Long> porEstado(Specification<Reserva> filtros) {
        var cb = em.getCriteriaBuilder();
        var consulta = cb.createTupleQuery();
        var raiz = consulta.from(Reserva.class);
        var estado = raiz.<EstadoReserva>get("estado");
        consulta.multiselect(estado, cb.count(raiz)).where(filtros.toPredicate(raiz, consulta, cb)).groupBy(estado);
        var resultado = new EnumMap<EstadoReserva, Long>(EstadoReserva.class);
        for (var fila : em.createQuery(consulta).getResultList()) {
            resultado.put(fila.get(0, EstadoReserva.class), fila.get(1, Long.class));
        }
        return resultado;
    }

    /**
     * Agrupa por servicio con su nombre actual y orden determinista para RF-14.
     * @param filtros criterio compartido con el historial, sin fetch
     * @return conteos por total descendente y nombre ascendente, sin grupos vacíos
     */
    public List<ConteoReporteDto> porServicio(Specification<Reserva> filtros) {
        return porRecurso(filtros, false);
    }

    /**
     * Agrupa por perfil y nombre del usuario del barbero, sin cargar relaciones LAZY.
     * @param filtros criterio compartido con el historial, sin fetch
     * @return conteos por total descendente y nombre ascendente, sin grupos vacíos
     */
    public List<ConteoReporteDto> porBarbero(Specification<Reserva> filtros) {
        return porRecurso(filtros, true);
    }

    private List<ConteoReporteDto> porRecurso(Specification<Reserva> filtros, boolean profesional) {
        var cb = em.getCriteriaBuilder();
        var consulta = cb.createTupleQuery();
        var raiz = consulta.from(Reserva.class);
        var recurso = raiz.join(profesional ? "barbero" : "servicio");
        var id = recurso.<Long>get("id");
        var nombre = (profesional ? recurso.join("usuario") : recurso).<String>get("nombre");
        var total = cb.count(raiz);
        consulta.multiselect(id, nombre, total).where(filtros.toPredicate(raiz, consulta, cb))
                .groupBy(id, nombre).orderBy(cb.desc(total), cb.asc(nombre), cb.asc(id));
        return em.createQuery(consulta).getResultList().stream().map(ReporteRepository::conteo).toList();
    }

    private static ConteoReporteDto conteo(Tuple fila) {
        return new ConteoReporteDto(fila.get(0, Long.class), fila.get(1, String.class), fila.get(2, Long.class));
    }
}
