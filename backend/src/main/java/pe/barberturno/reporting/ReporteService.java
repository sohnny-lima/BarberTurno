package pe.barberturno.reporting;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.reporting.dto.ResumenReporteDto;
import pe.barberturno.reservations.EstadoReserva;
import pe.barberturno.reservations.ReservaFiltros;
import pe.barberturno.users.Rol;

/**
 * Reportes RF-14 solo ADMIN; las tres agrupaciones leen la misma fotografía de PostgreSQL.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReporteService {
    private final ReporteRepository reportes;

    /**
     * Delega los conteos a proyecciones agregadas sin cargar reservas.
     * @param reportes persistencia escalar de los tres desgloses
     */
    public ReporteService(ReporteRepository reportes) { this.reportes = reportes; }

    /**
     * Aplica RN-22 y comparte filtros con RF-13; completa ceros y suma sin consulta COUNT adicional.
     * @param actor identidad ADMIN revalidada en servidor
     * @param desde primer día inclusivo obligatorio de Lima
     * @param hasta último día inclusivo obligatorio, máximo 366 días
     * @param servicioId servicio opcional; no exige que esté activo
     * @param barberoId perfil opcional; no exige que esté activo
     * @return total y tres desgloses conciliados de una misma fotografía
     * @throws NegocioException PROHIBIDO si el actor no es ADMIN; RANGO_FECHAS_INVALIDO si el rango es inválido
     */
    public ResumenReporteDto resumen(UsuarioAutenticado actor, LocalDate desde, LocalDate hasta,
            Long servicioId, Long barberoId) {
        if (actor.rol() != Rol.ADMIN) {
            throw new NegocioException(ErrorCodigo.PROHIBIDO, "No tiene permiso para consultar reportes.");
        }
        ReservaFiltros.validarRango(desde, hasta, true);
        var filtros = ReservaFiltros.criterio(null, barberoId, servicioId, null, desde, hasta);
        var estados = new EnumMap<EstadoReserva, Long>(EstadoReserva.class);
        for (var estado : EstadoReserva.values()) estados.put(estado, 0L);
        estados.putAll(reportes.porEstado(filtros));
        return new ResumenReporteDto(estados.values().stream().mapToLong(Long::longValue).sum(),
                Collections.unmodifiableMap(estados), reportes.porServicio(filtros), reportes.porBarbero(filtros));
    }
}
