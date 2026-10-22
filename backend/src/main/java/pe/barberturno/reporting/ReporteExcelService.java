package pe.barberturno.reporting;

import com.google.common.base.Ticker;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.barberturno.reporting.dto.ResumenReporteDto;
import pe.barberturno.reporting.dto.ConteoReporteDto;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.EstadoReserva;

/**
 * Genera el resumen RF-14 en OOXML sin fórmulas ni datos individuales de reservas (DA-29).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ReporteExcelService {
    private static final Logger LOG = LoggerFactory.getLogger(ReporteExcelService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss xxx");
    private final Clock reloj;
    private final Ticker ticker;
    /**
     * Usa el reloj del negocio y el contador monotónico del sistema para la exportación.
     * @param reloj fuente de instantes para la fecha de generación en Lima
     */
    @Autowired
    public ReporteExcelService(Clock reloj) { this(reloj, Ticker.systemTicker()); }

    ReporteExcelService(Clock reloj, Ticker ticker) {
        this.reloj = reloj;
        this.ticker = ticker;
    }

    /**
     * Escribe las tres agrupaciones ya calculadas conservando literalmente los nombres.
     * @param actorId identidad del administrador para el registro técnico
     * @param resumen conteos conciliados de una misma fotografía RF-14
     * @param desde primer día aplicado en Lima
     * @param hasta último día aplicado en Lima
     * @param servicio nombre del servicio filtrado o Todos
     * @param profesional nombre del profesional filtrado o Todos
     * @return libro XLSX completo en memoria
     * @throws IOException si no puede serializar el libro
     * @throws IllegalStateException si las agrupaciones no suman el total
     */
    public byte[] generar(long actorId, ResumenReporteDto resumen, LocalDate desde, LocalDate hasta,
            String servicio, String profesional) throws IOException {
        try {
            Preconditions.checkState(
                    resumen.porEstado().values().stream().mapToLong(Long::longValue).sum() == resumen.total()
                    && resumen.porServicio().stream().mapToLong(ConteoReporteDto::total).sum() == resumen.total()
                    && resumen.porBarbero().stream().mapToLong(ConteoReporteDto::total).sum() == resumen.total(),
                    "Las agrupaciones del reporte no concilian.");
        } catch (IllegalStateException error) {
            LOG.warn("Exportación rechazada por invariante RF-14.");
            throw error;
        }
        var cronometro = Stopwatch.createStarted(ticker);
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Resumen");
            var textoSeguro = libro.createCellStyle();
            textoSeguro.setQuotePrefixed(true);
            fila(hoja, textoSeguro, "Reporte de reservas · BarberTurno", null);
            fila(hoja, textoSeguro, "Fecha de generación (Lima)", FECHA.format(TiempoNegocio.aLima(reloj.instant())));
            fila(hoja, textoSeguro, "Desde", desde == null ? "Inicio" : desde.toString());
            fila(hoja, textoSeguro, "Hasta", hasta == null ? "Hoy" : hasta.toString());
            fila(hoja, textoSeguro, "Servicio", servicio);
            fila(hoja, textoSeguro, "Profesional", profesional);
            fila(hoja, textoSeguro, "Total", resumen.total());
            fila(hoja, textoSeguro, "Por estado", null);
            for (var estado : EstadoReserva.values()) {
                fila(hoja, textoSeguro, etiqueta(estado), resumen.porEstado().getOrDefault(estado, 0L));
            }
            fila(hoja, textoSeguro, "Por servicio", null);
            for (var grupo : resumen.porServicio()) fila(hoja, textoSeguro, grupo.nombre(), grupo.total());
            fila(hoja, textoSeguro, "Por profesional", null);
            for (var grupo : resumen.porBarbero()) fila(hoja, textoSeguro, grupo.nombre(), grupo.total());
            hoja.setColumnWidth(0, 48 * 256);
            hoja.setColumnWidth(1, 48 * 256);
            libro.write(salida);
            byte[] archivo = salida.toByteArray();
            LOG.info("Reporte Excel generado: actor={} filas={} bytes={} duracionMs={}", actorId,
                    hoja.getPhysicalNumberOfRows(), archivo.length, cronometro.stop().elapsed(TimeUnit.MILLISECONDS));
            return archivo;
        }
    }

    private void fila(Sheet hoja, CellStyle textoSeguro, String etiqueta, Object valor) {
        var fila = hoja.createRow(hoja.getPhysicalNumberOfRows());
        texto(fila.createCell(0), etiqueta, textoSeguro);
        if (valor instanceof Long numero) fila.createCell(1).setCellValue(numero.doubleValue());
        else if (valor instanceof String cadena) texto(fila.createCell(1), cadena, textoSeguro);
    }

    private void texto(org.apache.poi.ss.usermodel.Cell celda, String valor, CellStyle textoSeguro) {
        celda.setCellValue(valor);
        if (StringUtils.startsWithAny(valor, "=", "+", "-", "@", "\t", "\r")) celda.setCellStyle(textoSeguro);
    }

    private String etiqueta(EstadoReserva estado) {
        return switch (estado) {
            case PENDIENTE -> "Pendiente";
            case CONFIRMADA -> "Confirmada";
            case EN_ATENCION -> "En atención";
            case COMPLETADA -> "Completada";
            case CANCELADA -> "Cancelada";
            case NO_ASISTIO -> "No asistió";
        };
    }
}
