package pe.barberturno.reporting;

import java.time.LocalDate;
import java.io.IOException;
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
import pe.barberturno.catalog.Servicio;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.reporting.dto.ConteoReporteDto;

/**
 * Reportes RF-14 solo ADMIN; las tres agrupaciones leen la misma fotografía de PostgreSQL.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReporteService {
    private final pe.barberturno.common.tenancy.ProveedorNegocioActual negocio;
    private final ReporteRepository reportes;
    private final ReporteExcelService excel;
    private final ServicioRepository servicios;
    private final BarberoRepository barberos;

    /**
     * Delega los conteos a proyecciones agregadas sin cargar reservas.
     * @param negocio ámbito de la petición, adicional a la barrera RLS
     * @param reportes persistencia escalar de los tres desgloses
     * @param excel serialización OOXML con comprobación de la invariante RF-14
     * @param servicios catálogo para resolver el filtro cuando no hay reservas
     * @param barberos perfiles para resolver el filtro cuando no hay reservas
     */
    public ReporteService(ReporteRepository reportes, ReporteExcelService excel,
            ServicioRepository servicios, BarberoRepository barberos, pe.barberturno.common.tenancy.ProveedorNegocioActual negocio) {
        this.negocio = negocio;
        this.reportes = reportes;
        this.excel = excel;
        this.servicios = servicios;
        this.barberos = barberos;
    }

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
        var filtros = ReservaFiltros.criterio(negocio.negocioActual().orElseThrow(), null, barberoId, servicioId, null, desde, hasta);
        var estados = new EnumMap<EstadoReserva, Long>(EstadoReserva.class);
        for (var estado : EstadoReserva.values()) estados.put(estado, 0L);
        estados.putAll(reportes.porEstado(filtros));
        return new ResumenReporteDto(estados.values().stream().mapToLong(Long::longValue).sum(),
                Collections.unmodifiableMap(estados), reportes.porServicio(filtros), reportes.porBarbero(filtros));
    }

    /**
     * Exporta la misma fotografía y autorización del resumen; no vuelve a consultar reservas.
     * Resuelve filtros sin resultados en catálogos existentes, incluso inactivos.
     * @param actor identidad ADMIN revalidada en servidor
     * @param desde primer día inclusivo obligatorio de Lima
     * @param hasta último día inclusivo obligatorio, máximo 366 días
     * @param servicioId servicio opcional
     * @param barberoId perfil opcional
     * @return libro OOXML conciliado sin datos individuales de reservas
     * @throws NegocioException PROHIBIDO si no es ADMIN; RANGO_FECHAS_INVALIDO si el rango es inválido
     * @throws IOException si no puede serializar el archivo
     * @throws IllegalStateException si los conteos violan la invariante RF-14
     */
    public byte[] exportar(UsuarioAutenticado actor, LocalDate desde, LocalDate hasta,
            Long servicioId, Long barberoId) throws IOException {
        var resumen = resumen(actor, desde, hasta, servicioId, barberoId);
        String servicio = servicioId == null ? "Todos" : resumen.porServicio().stream()
                .filter(g -> g.id() == servicioId).map(ConteoReporteDto::nombre).findFirst()
                .orElseGet(() -> servicios.findById(servicioId).map(Servicio::getNombre).orElse("Sin coincidencias"));
        String profesional = barberoId == null ? "Todos" : resumen.porBarbero().stream()
                .filter(g -> g.id() == barberoId).map(ConteoReporteDto::nombre).findFirst()
                .orElseGet(() -> barberos.findById(barberoId).map(pe.barberturno.scheduling.Barbero::getNombreVisible).orElse("Sin coincidencias"));
        return excel.generar(actor.id(), resumen, desde, hasta, servicio, profesional);
    }
}
