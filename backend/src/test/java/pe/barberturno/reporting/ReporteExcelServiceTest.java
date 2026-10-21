package pe.barberturno.reporting;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.base.Ticker;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import pe.barberturno.reporting.dto.ConteoReporteDto;
import pe.barberturno.reporting.dto.ResumenReporteDto;
import pe.barberturno.reservations.EstadoReserva;
import static org.assertj.core.api.Assertions.*;

/** DA-29: contenido recuperado desde OOXML, integridad RF-14 y registro sin nombres ni filtros. */
class ReporteExcelServiceTest {
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-01T04:30:00Z"), ZoneOffset.UTC);
    private final LocalDate fecha = LocalDate.of(2026, 10, 1);
    private final Ticker ticker = new Ticker() {
        private long nanos;
        @Override public long read() { long anterior = nanos; nanos += TimeUnit.MILLISECONDS.toNanos(125); return anterior; }
    };
    private final ReporteExcelService excel = new ReporteExcelService(reloj, ticker);

    private ResumenReporteDto resumen(String nombre) {
        var estados = new EnumMap<EstadoReserva, Long>(EstadoReserva.class);
        for (var estado : EstadoReserva.values()) estados.put(estado, 1L);
        return new ResumenReporteDto(6, estados, List.of(new ConteoReporteDto(3, nombre, 6)),
                List.of(new ConteoReporteDto(5, "Profesional ficticio", 6)));
    }

    private XSSFWorkbook abrir(ResumenReporteDto resumen, String servicio, String profesional) throws Exception {
        byte[] archivo = excel.generar(7, resumen, fecha, fecha, servicio, profesional);
        assertThat(archivo).as("el generador entrega un libro OOXML").isNotEmpty();
        return new XSSFWorkbook(new ByteArrayInputStream(archivo));
    }

    private Cell valor(Sheet hoja, String etiqueta) {
        for (var fila : hoja) {
            if (fila.getCell(0).getStringCellValue().equals(etiqueta)) return fila.getCell(1);
        }
        throw new AssertionError("Falta la etiqueta " + etiqueta);
    }

    @Test void contenidoCompletoNumericoSinFormulasNiHojasOcultas() throws Exception {
        try (var libro = abrir(resumen("Corte ficticio"), "Todos", "Todos")) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(1);
            assertThat(libro.isSheetHidden(0)).isFalse();
            assertThat(libro.isSheetVeryHidden(0)).isFalse();
            assertThat(libro.isMacroEnabled()).isFalse();
            var hoja = libro.getSheet("Resumen");
            assertThat(hoja).isNotNull();
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Reporte de reservas · BarberTurno");
            assertThat(valor(hoja, "Fecha de generación (Lima)").getStringCellValue()).isEqualTo("30/09/2026 23:30:00 -05:00");
            assertThat(valor(hoja, "Desde").getStringCellValue()).isEqualTo("2026-10-01");
            assertThat(valor(hoja, "Hasta").getStringCellValue()).isEqualTo("2026-10-01");
            assertThat(valor(hoja, "Servicio").getStringCellValue()).isEqualTo("Todos");
            assertThat(valor(hoja, "Profesional").getStringCellValue()).isEqualTo("Todos");
            assertThat(valor(hoja, "Total").getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(valor(hoja, "Total").getNumericCellValue()).isEqualTo(6);
            var etiquetas = List.of("Pendiente", "Confirmada", "En atención", "Completada", "Cancelada", "No asistió");
            for (int i = 0; i < etiquetas.size(); i++) {
                assertThat(hoja.getRow(8 + i).getCell(0).getStringCellValue()).isEqualTo(etiquetas.get(i));
                assertThat(valor(hoja, etiquetas.get(i)).getNumericCellValue()).isEqualTo(1);
            }
            assertThat(valor(hoja, "Corte ficticio").getNumericCellValue()).isEqualTo(6);
            assertThat(valor(hoja, "Profesional ficticio").getNumericCellValue()).isEqualTo(6);
            assertThat(hoja.getColumnWidth(0)).isGreaterThanOrEqualTo(30 * 256);
            for (var fila : hoja) for (var celda : fila) assertThat(celda.getCellType()).isNotEqualTo(CellType.FORMULA);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"=SUM(A1:A2)", "+Corte", "-Corte", "@Corte", "\tCorte", "\rCorte"})
    void conservaTextoDeRiesgoYActivaQuotePrefixed(String nombre) throws Exception {
        try (var libro = abrir(resumen(nombre), nombre, nombre)) {
            var hoja = libro.getSheetAt(0);
            for (var celda : List.of(hoja.getRow(15).getCell(0), valor(hoja, "Servicio"), valor(hoja, "Profesional"))) {
                assertThat(celda.getCellType()).isEqualTo(CellType.STRING);
                assertThat(celda.getStringCellValue()).isEqualTo(nombre);
                assertThat(celda.getCellStyle().getQuotePrefixed()).isTrue();
            }
            assertThat(hoja.getRow(0).getCell(0).getCellStyle().getQuotePrefixed()).isFalse();
        }
    }

    @Test void resumenVacioConservaSeisEstadosYFiltros() throws Exception {
        var vacio = new ResumenReporteDto(0, new EnumMap<>(EstadoReserva.class), List.of(), List.of());
        try (var libro = abrir(vacio, "Servicio sin reservas", "Profesional sin reservas")) {
            var hoja = libro.getSheetAt(0);
            assertThat(valor(hoja, "Total").getNumericCellValue()).isZero();
            for (int i = 8; i < 14; i++) assertThat(hoja.getRow(i).getCell(1).getNumericCellValue()).isZero();
            assertThat(valor(hoja, "Servicio").getStringCellValue()).isEqualTo("Servicio sin reservas");
            assertThat(valor(hoja, "Profesional").getStringCellValue()).isEqualTo("Profesional sin reservas");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"estado", "servicio", "profesional"})
    void rechazaCadaDesgloseIncoherenteConWarnSinDatos(String dimension) {
        var original = resumen("=Nombre privado");
        var incorrecto = new ResumenReporteDto(6,
                dimension.equals("estado") ? new EnumMap<>(EstadoReserva.class) : original.porEstado(),
                dimension.equals("servicio") ? List.of() : original.porServicio(),
                dimension.equals("profesional") ? List.of() : original.porBarbero());
        var logger = (Logger) LoggerFactory.getLogger(ReporteExcelService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> excel.generar(7, incorrecto, fecha, fecha, "=Nombre privado", "correo@ejemplo.test"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("Nombre", "correo", "2026", "=");
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void infoSoloIncluyeActorFilasBytesYDuracionMonotonica() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(ReporteExcelService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            byte[] archivo = excel.generar(7, resumen("=Nombre privado"), fecha, fecha, "=Nombre privado", "correo@ejemplo.test");
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
            assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo(
                    "Reporte Excel generado: actor=7 filas=18 bytes=" + archivo.length + " duracionMs=125");
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
