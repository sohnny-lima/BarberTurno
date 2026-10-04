package pe.barberturno.reservations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reporting.ReporteService;
import pe.barberturno.reporting.dto.ResumenReporteDto;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.support.SqlPruebas;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CP-10 sobre PostgreSQL real, sin transacción envolvente y con identidad revalidada por cookie. */
@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReporteIT extends ReservaPruebaBase {
    @Autowired ReporteService reportes;
    Usuario admin;
    Barbero alfa;
    Servicio barba;

    @BeforeEach void escenarioConocido() {
        admin = usuarios.saveAndFlush(new Usuario("Admin ficticio", "admin-reportes@ejemplo.test", null,
                "hash-ficticio-no-utilizable", Rol.ADMIN, null, reloj.instant()));
        barbero.getUsuario().actualizarPerfil("Zeta ficticio", null, reloj.instant());
        usuarios.saveAndFlush(barbero.getUsuario());
        alfa = datos.barbero("alfa");
        alfa.getUsuario().actualizarPerfil("Alfa ficticio", null, reloj.instant());
        usuarios.saveAndFlush(alfa.getUsuario());
        barba = servicios.saveAndFlush(new Servicio("Barba ficticia", "Prueba", (short) 20,
                BigDecimal.TEN, reloj.instant()));
        var horas = List.of("00:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "23:30");
        for (int i = 0; i < horas.size(); i++) {
            insertar("2026-10-01", horas.get(i), i % 4 < 2 ? barbero : alfa,
                    i % 2 == 0 ? servicio : barba, EstadoReserva.values()[i % 6]);
        }
        insertar("2026-09-30", "23:30", alfa, servicio, EstadoReserva.COMPLETADA);
        insertar("2026-10-02", "00:10", alfa, barba, EstadoReserva.NO_ASISTIO);
    }

    private void insertar(String fecha, String hora, Barbero perfil, Servicio contratado, EstadoReserva estado) {
        var inicio = TiempoNegocio.inicioDelDia(LocalDate.parse(fecha)).plusSeconds(LocalTime.parse(hora).toSecondOfDay());
        reservas.saveAndFlush(new Reserva(cliente, perfil, contratado, inicio, estado, cliente, reloj.instant()));
    }

    private MockHttpServletRequestBuilder peticion(String ruta, String desde, String hasta, Long servicioId, Long barberoId) {
        var resultado = get(ruta).cookie(sesion(admin)).param("desde", desde).param("hasta", hasta);
        if (servicioId != null) resultado.param("servicioId", servicioId.toString());
        if (barberoId != null) resultado.param("barberoId", barberoId.toString());
        return resultado;
    }

    private ResumenReporteDto conciliar(String desde, String hasta, Long servicioId, Long barberoId, long total) throws Exception {
        var respuesta = mvc.perform(peticion("/api/reportes/resumen", desde, hasta, servicioId, barberoId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var resumen = json.readValue(respuesta, ResumenReporteDto.class);
        var nodo = json.readTree(respuesta);
        assertThat(nodo.propertyNames()).containsExactlyInAnyOrder("total", "porEstado", "porServicio", "porBarbero");
        assertThat(resumen.total()).isEqualTo(total);
        assertThat(resumen.porEstado()).containsOnlyKeys(EstadoReserva.values());
        assertThat(resumen.porEstado().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
        assertThat(resumen.porServicio().stream().mapToLong(r -> r.total()).sum()).isEqualTo(total);
        assertThat(resumen.porBarbero().stream().mapToLong(r -> r.total()).sum()).isEqualTo(total);
        mvc.perform(peticion("/api/reservas", desde, hasta, servicioId, barberoId).param("tamano", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(total));
        return resumen;
    }

    @ParameterizedTest @CsvSource({"0,8", "1,4", "2,4", "3,2", "4,2", "5,0", "6,0", "7,0", "8,0"})
    void cp10_conciliaTodasLasSumasYElHistorial(int combinacion, long total) throws Exception {
        Long servicioId = switch (combinacion) {
            case 1, 3, 4, 8 -> servicio.getId();
            case 5, 7 -> Long.MAX_VALUE;
            default -> null;
        };
        Long barberoId = switch (combinacion) {
            case 2, 3 -> barbero.getId();
            case 4 -> alfa.getId();
            case 6, 7, 8 -> Long.MAX_VALUE;
            default -> null;
        };
        conciliar("2026-10-01", "2026-10-01", servicioId, barberoId, total);
    }

    @Test void estadosExactosYOrdenDeEmpatesPorNombre() throws Exception {
        var resumen = conciliar("2026-10-01", "2026-10-01", null, null, 8);
        assertThat(resumen.porEstado()).containsEntry(EstadoReserva.PENDIENTE, 2L)
                .containsEntry(EstadoReserva.CONFIRMADA, 2L).containsEntry(EstadoReserva.EN_ATENCION, 1L)
                .containsEntry(EstadoReserva.COMPLETADA, 1L).containsEntry(EstadoReserva.CANCELADA, 1L)
                .containsEntry(EstadoReserva.NO_ASISTIO, 1L);
        assertThat(resumen.porServicio()).extracting(r -> r.id()).containsExactly(barba.getId(), servicio.getId());
        assertThat(resumen.porServicio()).extracting(r -> r.nombre()).containsExactly("Barba ficticia", servicio.getNombre());
        assertThat(resumen.porBarbero()).extracting(r -> r.id()).containsExactly(alfa.getId(), barbero.getId());
        assertThat(resumen.porBarbero()).extracting(r -> r.nombre()).containsExactly("Alfa ficticio", "Zeta ficticio");
        var ampliado = conciliar("2026-09-30", "2026-10-02", null, null, 10);
        // El total prevalece sobre el nombre: Zeta se coloca primero al tener más reservas.
        insertar("2026-10-02", "10:00", barbero, servicio, EstadoReserva.CANCELADA);
        insertar("2026-10-02", "11:00", barbero, servicio, EstadoReserva.COMPLETADA);
        insertar("2026-10-02", "12:00", barbero, servicio, EstadoReserva.CONFIRMADA);
        var desigual = conciliar("2026-09-30", "2026-10-02", null, null, 13);
        assertThat(desigual.porServicio()).extracting(r -> r.id()).containsExactly(servicio.getId(), barba.getId());
        assertThat(desigual.porBarbero()).extracting(r -> r.id()).containsExactly(barbero.getId(), alfa.getId());
        assertThat(desigual.porBarbero()).extracting(r -> r.total()).containsExactly(7L, 6L);
        assertThat(ampliado.porBarbero()).extracting(r -> r.total()).containsExactly(6L, 4L);
    }

    @Test void fronteraLimaUsaInicioYNoSolapeNiFechaUtc() throws Exception {
        var anterior = conciliar("2026-09-30", "2026-09-30", null, null, 1);
        assertThat(anterior.porEstado()).containsEntry(EstadoReserva.COMPLETADA, 1L)
                .containsEntry(EstadoReserva.PENDIENTE, 0L);
        // 23:30 Lima es 04:30 UTC del día siguiente y pertenece al día 1; 00:10 pertenece al día 2.
        conciliar("2026-10-01", "2026-10-01", null, null, 8);
        var siguiente = conciliar("2026-10-02", "2026-10-02", null, null, 1);
        assertThat(siguiente.porEstado()).containsEntry(EstadoReserva.NO_ASISTIO, 1L)
                .containsEntry(EstadoReserva.CONFIRMADA, 0L);
        conciliar("2026-09-30", "2026-10-02", null, null, 10);
    }

    @Test void sinReservasLosSeisEstadosSonCeroYLosDesglosesVacios() throws Exception {
        var resumen = conciliar("2026-10-03", "2026-10-03", null, null, 0);
        assertThat(resumen.porEstado().values()).containsOnly(0L);
        assertThat(resumen.porServicio()).isEmpty();
        assertThat(resumen.porBarbero()).isEmpty();
    }

    @Test void recursosInactivosConservanElHistorial() throws Exception {
        servicio.desactivar(reloj.instant());
        servicios.saveAndFlush(servicio);
        barbero.desactivar(reloj.instant());
        barberos.saveAndFlush(barbero);
        conciliar("2026-10-01", "2026-10-01", servicio.getId(), barbero.getId(), 2);
        conciliar("2026-10-01", "2026-10-01", null, null, 8);
    }

    @Test void limite366DiasInclusivosAceptado() throws Exception {
        conciliar("2026-01-01", "2027-01-01", null, null, 10);
    }

    @ParameterizedTest @CsvSource({"2026-10-02,2026-10-01", "2026-01-01,2027-01-02", "2026-10-01,+999999999-12-31"})
    void rangoInvalidoDevuelve400(String desde, String hasta) throws Exception {
        mvc.perform(peticion("/api/reportes/resumen", desde, hasta, null, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @ParameterizedTest @ValueSource(strings = {"desde", "hasta", "ambas"})
    void fechasAusentesDevuelvenRangoInvalido(String omitir) throws Exception {
        var peticion = get("/api/reportes/resumen").cookie(sesion(admin));
        if (omitir.equals("desde")) peticion.param("hasta", "2026-10-01");
        if (omitir.equals("hasta")) peticion.param("desde", "2026-10-01");
        mvc.perform(peticion).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @ParameterizedTest @CsvSource({"desde,texto", "hasta,2026-02-30", "servicioId,texto", "barberoId,texto"})
    void formatoInvalidoDevuelveValidacion(String parametro, String valor) throws Exception {
        var peticion = get("/api/reportes/resumen").cookie(sesion(admin));
        if (!parametro.equals("desde")) peticion.param("desde", "2026-10-01");
        if (!parametro.equals("hasta")) peticion.param("hasta", "2026-10-01");
        mvc.perform(peticion.param(parametro, valor)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @ParameterizedTest @ValueSource(strings = {"desde", "hasta", "servicioId", "barberoId"})
    void parametrosVaciosDevuelvenValidacion(String parametro) throws Exception {
        var peticion = get("/api/reportes/resumen").cookie(sesion(admin));
        if (!parametro.equals("desde")) peticion.param("desde", "2026-10-01");
        if (!parametro.equals("hasta")) peticion.param("hasta", "2026-10-01");
        mvc.perform(peticion.param(parametro, " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test void soloAdminEnHttpYEnServicio() throws Exception {
        for (var usuario : List.of(cliente, barbero.getUsuario())) {
            mvc.perform(get("/api/reportes/resumen").cookie(sesion(usuario))
                    .param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
            assertThatThrownBy(() -> reportes.resumen(actor(usuario), LocalDate.of(2026, 10, 1),
                    LocalDate.of(2026, 10, 1), null, null)).isInstanceOf(NegocioException.class)
                    .extracting(e -> ((NegocioException) e).codigo()).isEqualTo(ErrorCodigo.PROHIBIDO);
        }
        mvc.perform(get("/api/reportes/resumen")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        admin.cambiarPassword("hash-ficticio-no-utilizable", true, reloj.instant());
        usuarios.saveAndFlush(admin);
        mvc.perform(peticion("/api/reportes/resumen", "2026-10-01", "2026-10-01", null, null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }

    @Test void exactamenteTresAgregacionesSinCargarEntidadesConYSinFiltros() throws Exception {
        for (boolean filtrado : List.of(false, true)) {
            Long servicioId = filtrado ? servicio.getId() : null;
            Long barberoId = filtrado ? alfa.getId() : null;
            SqlPruebas.limpiar();
            reportes.resumen(actor(admin), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), servicioId, barberoId);
            assertThat(SqlPruebas.sentencias()).hasSize(3).allSatisfy(s -> assertThat(s).contains("count(", "group by"));
            SqlPruebas.limpiar();
            mvc.perform(peticion("/api/reportes/resumen", "2026-10-01", "2026-10-01", servicioId, barberoId))
                    .andExpect(status().isOk());
            var sql = SqlPruebas.sentencias();
            assertThat(sql).hasSize(5); // Dos revalidaciones preexistentes: usuario y perfil; tres agregaciones.
            assertThat(sql.stream().filter(s -> s.contains("from reserva"))).hasSize(3)
                    .allSatisfy(s -> assertThat(s).contains("count(", "group by").doesNotContain("fetch first"));
        }
    }
}
