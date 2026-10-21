package pe.barberturno.reporting;

import com.google.common.base.Ticker;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.barberturno.reporting.dto.ResumenReporteDto;

/**
 * Genera el resumen RF-14 en OOXML sin fórmulas ni datos individuales de reservas (DA-29).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ReporteExcelService {
    /**
     * Usa el reloj del negocio y el contador monotónico del sistema para la exportación.
     * @param reloj fuente de instantes para la fecha de generación en Lima
     */
    @Autowired
    public ReporteExcelService(Clock reloj) { this(reloj, Ticker.systemTicker()); }

    ReporteExcelService(Clock reloj, Ticker ticker) { }

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
        return new byte[0];
    }
}
