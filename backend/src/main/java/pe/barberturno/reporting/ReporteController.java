package pe.barberturno.reporting;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.reporting.dto.ResumenReporteDto;

/**
 * Consulta HTTP RF-14 con días de Lima y autorización ADMIN en ruta y servicio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@RestController
public class ReporteController {
    private final ReporteService reportes;

    /**
     * Conecta el contrato §6.3 con los conteos transaccionales RN-22.
     * @param reportes servicio de lectura con validación y autorización
     */
    public ReporteController(ReporteService reportes) { this.reportes = reportes; }

    /**
     * Devuelve los tres desgloses sobre inicio; rechaza filtros presentes vacíos igual que el historial.
     * @param actor identidad ADMIN autenticada
     * @param desde primer día obligatorio yyyy-MM-dd
     * @param hasta último día obligatorio yyyy-MM-dd
     * @param servicioId servicio opcional
     * @param barberoId perfil opcional
     * @param parametros valores HTTP originales para distinguir vacío de omitido
     * @return HTTP 200 con los seis estados y conteos por servicio y profesional
     * @throws NegocioException VALIDACION ante valores vacíos; PROHIBIDO ante rol inválido;
     * RANGO_FECHAS_INVALIDO ante fechas ausentes, invertidas o rango excesivo
     */
    @GetMapping("/api/reportes/resumen")
    public ResumenReporteDto resumen(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long servicioId, @RequestParam(required = false) Long barberoId,
            @RequestParam Map<String, String> parametros) {
        for (String clave : new String[]{"desde", "hasta", "servicioId", "barberoId"}) {
            if (parametros.containsKey(clave) && parametros.get(clave).isBlank()) {
                throw new NegocioException(ErrorCodigo.VALIDACION, "El parámetro " + clave + " no puede estar vacío.");
            }
        }
        return reportes.resumen(actor, desde, hasta, servicioId, barberoId);
    }
}
