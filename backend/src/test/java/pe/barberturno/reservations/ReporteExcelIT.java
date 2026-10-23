package pe.barberturno.reservations;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.reporting.ReporteExcelService;
import pe.barberturno.reporting.ReporteService;
import pe.barberturno.reporting.dto.ResumenReporteDto;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.support.SqlPruebas;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CP-20: descarga real, negociación de errores y permisos con PostgreSQL y sesión revalidada. */
@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReporteExcelIT extends ReservaPruebaBase {
    private static final String RUTA = "/api/reportes/resumen.xlsx";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    @Autowired ReporteService reportes;
    @MockitoSpyBean ReporteExcelService excel;
    Usuario admin;
    Servicio segundo;
    Barbero otro;

    @BeforeEach void escenario() {
        admin = pe.barberturno.support.DatosPrueba.guardarPersonalV1(usuarios, membresias, new Usuario("Admin ficticio", "excel@ejemplo.test", null,
                "hash-ficticio-no-utilizable", Rol.ADMIN, null, reloj.instant()));
        servicio.editar("=Corte ficticio", "Prueba", (short) 30, BigDecimal.TEN, reloj.instant());
        servicios.saveAndFlush(servicio);
        segundo = servicios.saveAndFlush(new Servicio("Barba ficticia", "Prueba", (short) 20, BigDecimal.TEN, reloj.instant()));
        otro = datos.barbero("otro");
        for (int i = 0; i < 6; i++) {
            reservas.saveAndFlush(new Reserva(cliente, i % 2 == 0 ? barbero : otro,
                    i % 3 == 0 ? segundo : servicio, instante(String.format("%02d:00", 9 + i)), EstadoReserva.values()[i], cliente, reloj.instant()));
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion() {
        return get(RUTA).cookie(sesion(admin)).accept(XLSX).param("desde", "2026-10-01").param("hasta", "2026-10-01");
    }

    private double numero(Sheet hoja, String nombre) {
        for (var fila : hoja) {
            if (fila.getCell(0).getStringCellValue().equals(nombre)) {
                assertThat(fila.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
                return fila.getCell(1).getNumericCellValue();
            }
        }
        throw new AssertionError("Falta " + nombre);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void adminDescargaMismasCifrasQueJsonConYSinFiltros(int filtros) throws Exception {
        var archivo = peticion();
        var consulta = get("/api/reportes/resumen").cookie(sesion(admin))
                .param("desde", "2026-10-01").param("hasta", "2026-10-01");
        if ((filtros & 1) != 0) {
            archivo.param("servicioId", servicio.getId().toString());
            consulta.param("servicioId", servicio.getId().toString());
        }
        if ((filtros & 2) != 0) {
            archivo.param("barberoId", barbero.getId().toString());
            consulta.param("barberoId", barbero.getId().toString());
        }
        var resumen = json.readValue(mvc.perform(consulta).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(), ResumenReporteDto.class);
        SqlPruebas.limpiar();
        var respuesta = mvc.perform(archivo).andExpect(status().isOk()).andExpect(content().contentType(XLSX))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-barberturno-2026-10-01_2026-10-01.xlsx\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy")).andReturn().getResponse();
        assertThat(SqlPruebas.sentencias().stream().filter(s -> s.contains("from reserva"))).hasSize(3)
                .allSatisfy(s -> assertThat(s).contains("count(", "group by"));
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            var hoja = libro.getSheet("Resumen");
            assertThat(numero(hoja, "Total")).isEqualTo(resumen.total());
            int i = 8;
            for (var estado : EstadoReserva.values()) {
                assertThat(hoja.getRow(i++).getCell(1).getNumericCellValue()).isEqualTo(resumen.porEstado().get(estado).doubleValue());
            }
            for (var grupo : resumen.porServicio()) assertThat(numero(hoja, grupo.nombre())).isEqualTo(grupo.total());
            for (var grupo : resumen.porBarbero()) assertThat(numero(hoja, grupo.nombre())).isEqualTo(grupo.total());
            for (var fila : hoja) for (var celda : fila) {
                assertThat(celda.getCellType()).isNotEqualTo(CellType.FORMULA);
                if (celda.getCellType() == CellType.STRING && celda.getStringCellValue().startsWith("="))
                    assertThat(celda.getCellStyle().getQuotePrefixed()).isTrue();
            }
            assertThat(hoja.getRow(4).getCell(1).getStringCellValue()).isEqualTo((filtros & 1) != 0 ? servicio.getNombre() : "Todos");
            assertThat(hoja.getRow(5).getCell(1).getStringCellValue()).isEqualTo((filtros & 2) != 0 ? barbero.getUsuario().getNombre() : "Todos");
        }
    }

    @Test void filtrosSinReservasResuelvenNombresDeCatalogosInclusoInactivos() throws Exception {
        servicio.desactivar(reloj.instant()); servicios.saveAndFlush(servicio);
        barbero.desactivar(reloj.instant()); barberos.saveAndFlush(barbero);
        var respuesta = mvc.perform(get(RUTA).cookie(sesion(admin)).accept(XLSX)
                .param("desde", "2026-10-03").param("hasta", "2026-10-03")
                .param("servicioId", servicio.getId().toString()).param("barberoId", barbero.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse();
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            var hoja = libro.getSheetAt(0);
            assertThat(numero(hoja, "Total")).isZero();
            assertThat(hoja.getRow(4).getCell(1).getStringCellValue()).isEqualTo(servicio.getNombre());
            assertThat(hoja.getRow(5).getCell(1).getStringCellValue()).isEqualTo(barbero.getUsuario().getNombre());
        }
    }

    @Test void sinSesion401ConProblemaAunqueAcceptSeaExcel() throws Exception {
        mvc.perform(get(RUTA).accept(XLSX)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test void clienteYBarbero403ConProblemaAunqueAcceptSeaExcel() throws Exception {
        for (var usuario : List.of(cliente, barbero.getUsuario())) {
            mvc.perform(get(RUTA).cookie(sesion(usuario)).accept(XLSX)).andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        }
    }

    @Test void passwordTemporal403ConProblemaAunqueAcceptSeaExcel() throws Exception {
        admin.cambiarPassword("hash-ficticio-no-utilizable", true, reloj.instant()); usuarios.saveAndFlush(admin);
        mvc.perform(peticion()).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }

    @Test void exportacionImponeAdminTambienAlInvocarServicio() {
        for (var usuario : List.of(cliente, barbero.getUsuario())) {
            assertThatThrownBy(() -> reportes.exportar(actor(usuario), LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 1), null, null)).isInstanceOf(NegocioException.class)
                    .extracting(e -> ((NegocioException) e).codigo()).isEqualTo(ErrorCodigo.PROHIBIDO);
        }
    }

    @ParameterizedTest @CsvSource({"2026-10-02,2026-10-01", "2026-01-01,2027-01-02"})
    void rangoInvalido400AunqueAcceptSeaExcel(String desde, String hasta) throws Exception {
        mvc.perform(get(RUTA).cookie(sesion(admin)).accept(XLSX).param("desde", desde).param("hasta", hasta))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @ParameterizedTest @ValueSource(strings = {"desde", "hasta", "servicioId", "barberoId"})
    void vacios400AunqueAcceptSeaExcel(String parametro) throws Exception {
        var solicitud = get(RUTA).cookie(sesion(admin)).accept(XLSX);
        if (!parametro.equals("desde")) solicitud.param("desde", "2026-10-01");
        if (!parametro.equals("hasta")) solicitud.param("hasta", "2026-10-01");
        mvc.perform(solicitud.param(parametro, " ")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test void fechaAusenteConservaValidacionDelResumen() throws Exception {
        mvc.perform(get(RUTA).cookie(sesion(admin)).accept(XLSX)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @Test void formatoInvalido400AunqueAcceptSeaExcel() throws Exception {
        mvc.perform(get(RUTA).cookie(sesion(admin)).accept(XLSX).param("desde", "texto"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test void invarianteRotaDevuelve500GenericoSinArchivoNiDatos() throws Exception {
        doThrow(new IllegalStateException("dato-sensible@ejemplo.test")).when(excel)
                .generar(anyLong(), any(), any(), any(), anyString(), anyString());
        mvc.perform(peticion()).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("dato-sensible"))));
    }
}
