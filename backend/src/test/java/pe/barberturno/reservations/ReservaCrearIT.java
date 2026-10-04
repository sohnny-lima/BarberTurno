package pe.barberturno.reservations;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.barberturno.audit.*;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.Bloqueo;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ReservaCrearIT extends ReservaPruebaBase {
    @MockitoSpyBean NotificacionService notificaciones;
    @MockitoSpyBean AuditoriaService auditoria;

    @Test void crear_valida_referenciasAuditoriaAvisosLocationYPermisos() throws Exception {
        SqlPruebas.limpiar();
        var resultado = mvc.perform(conCsrf(peticion(cmd(barbero, "10:00")), sesion(cliente)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/reservas/100"))
                .andExpect(jsonPath("$.cliente.telefono").doesNotExist())
                .andExpect(jsonPath("$.inicio").value("2026-10-01T10:00:00-05:00"))
                .andReturn();
        var dto = json.readValue(resultado.getResponse().getContentAsString(), ReservaDto.class);
        assertThat(dto.codigo()).isEqualTo("BT-100");
        assertThat(dto.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(dto.fin()).isEqualTo(TiempoNegocio.aLima(instante("10:30")));
        assertThat(dto.duracionMin()).isEqualTo((short) 30);
        assertThat(dto.precioRef()).isEqualByComparingTo("20.00");
        assertThat(dto.version()).isZero();
        assertThat(dto.permisos()).isEqualTo(new ReservaDto.PermisosDto(true, true, List.of()));
        assertThat(cantidad("reserva")).isOne();
        assertThat(cantidad("auditoria_reserva")).isOne();
        var historial = jdbc.queryForMap("select * from auditoria_reserva");
        assertThat(historial.get("actor_id")).isEqualTo(cliente.getId());
        assertThat(historial.get("accion")).isEqualTo("CREAR");
        assertThat(historial.get("estado_anterior")).isNull();
        assertThat(historial.get("datos_anteriores")).isNull();
        assertThat(historial.get("estado_nuevo")).isEqualTo("CONFIRMADA");
        assertThat(historial.get("excepcional")).isEqualTo(false);
        var nuevos = json.readTree(historial.get("datos_nuevos").toString());
        assertThat(nuevos.size()).isEqualTo(5);
        assertThat(nuevos.get("inicio").asString()).isEqualTo("2026-10-01T10:00-05:00");
        assertThat(nuevos.get("fin").asString()).isEqualTo("2026-10-01T10:30-05:00");
        assertThat(nuevos.get("barberoId").asLong()).isEqualTo(barbero.getId());
        assertThat(nuevos.get("servicioId").asLong()).isEqualTo(servicio.getId());
        assertThat(nuevos.get("estado").asString()).isEqualTo("CONFIRMADA");
        assertThat(jdbc.queryForList("select usuario_id from notificacion order by usuario_id", Long.class))
                .containsExactlyInAnyOrder(cliente.getId(), barbero.getUsuario().getId());
        assertThat(jdbc.queryForList("select mensaje from notificacion", String.class))
                .allSatisfy(m -> assertThat(m).contains("BT-100", servicio.getNombre(),
                        barbero.getUsuario().getNombre(), "01/10/2026 10:00", "Lima"));
        assertThat(jdbc.queryForObject("select count(*) from notificacion where tipo='CREAR' and not leida", Integer.class)).isEqualTo(2);
        var sql = SqlPruebas.sentencias();
        int usuario = indice(sql, "from usuario", "for no key update");
        int perfil = indice(sql, "from barbero", "for no key update");
        int agenda = indice(sql, "from jornada", "");
        assertThat(usuario).isGreaterThanOrEqualTo(0);
        assertThat(perfil).isGreaterThan(usuario);
        assertThat(agenda).isGreaterThan(perfil);
        servicio.editar("Corte modificado", "", (short) 60, new BigDecimal("55.00"), reloj.instant());
        servicios.saveAndFlush(servicio);
        assertThat(jdbc.queryForObject("select precio_ref from reserva", BigDecimal.class)).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("select duracion_ref_min from reserva", Short.class)).isEqualTo((short) 30);
    }

    private int indice(List<String> sql, String tabla, String sufijo) {
        for (int i = 0; i < sql.size(); i++) if (sql.get(i).contains(tabla) && sql.get(i).contains(sufijo)) return i;
        return -1;
    }

    @Test void cp04_contiguas_ambas201() throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00"));
        crearHttp(cliente, cmd(barbero, "10:30"));
        assertThat(cantidad("reserva")).isEqualTo(2);
    }

    @Test void franjaOcupada_409() throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00"));
        rechazar(datos.cliente("otro"), cmd(barbero, "10:00"), 409, "FRANJA_NO_DISPONIBLE");
    }

    @Test void cp13_clienteConDosBarberos_409() throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00"));
        rechazar(cliente, cmd(perfil("dos"), "10:00"), 409, "CLIENTE_CON_RESERVA_SOLAPADA");
    }

    @Test void cp15_cuartaFutura_422() throws Exception {
        for (String hora : List.of("10:00", "11:00", "12:00")) crearHttp(cliente, cmd(barbero, hora));
        rechazar(cliente, cmd(barbero, "13:00"), 422, "LIMITE_RESERVAS_ACTIVAS");
    }

    @ParameterizedTest @ValueSource(strings = {"PENDIENTE", "CONFIRMADA", "EN_ATENCION", "COMPLETADA", "NO_ASISTIO"})
    void todosLosEstadosOcupantes_impidenNuevaReserva(String estado) throws Exception {
        reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, instante("10:00"),
                EstadoReserva.valueOf(estado), cliente, reloj.instant()));
        rechazar(datos.cliente("otro"), cmd(barbero, "10:00"), 409, "FRANJA_NO_DISPONIBLE");
    }

    @Test void cancelada_noCuentaNiSolapa() throws Exception {
        for (String hora : List.of("10:00", "11:00", "12:00")) {
            reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, instante(hora),
                    EstadoReserva.CANCELADA, cliente, reloj.instant()));
        }
        crearHttp(cliente, cmd(barbero, "10:00"));
    }

    @ParameterizedTest @CsvSource({
        "08:00,FUERA_DE_HORARIO", "17:40,FUERA_DE_HORARIO",
        "10:05,FUERA_DE_HORARIO", "10:00:01,FUERA_DE_HORARIO", "10:00:00.000000001,FUERA_DE_HORARIO"})
    void jornadaRejillaYPrecision_422(String hora, String codigo) throws Exception {
        rechazar(cliente, cmd(barbero, hora), 422, codigo);
    }

    @ParameterizedTest @CsvSource({
        "2026-09-28T09:00:00-05:00,INICIO_EN_PASADO",
        "2026-09-28T08:50:00-05:00,INICIO_EN_PASADO",
        "2026-10-29T10:00:00-05:00,FUERA_DE_HORIZONTE"})
    void limitesTemporales_422(String inicio, String codigo) throws Exception {
        rechazar(cliente, new CrearReservaDto(servicio.getId(), barbero.getId(),
                OffsetDateTime.parse(inicio), null), 422, codigo);
    }

    @Test void horizonteInclusivoYOtroDesfase_201() throws Exception {
        var dto = crearHttp(cliente, new CrearReservaDto(servicio.getId(), barbero.getId(),
                OffsetDateTime.parse("2026-10-28T15:00:00Z"), null));
        assertThat(dto.inicio().getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test void servicioInactivo_422() throws Exception {
        servicio.desactivar(reloj.instant());
        servicios.saveAndFlush(servicio);
        rechazar(cliente, cmd(barbero, "10:00"), 422, "RECURSO_INACTIVO");
    }

    @Test void barberoInactivo_422() throws Exception {
        jdbc.update("update barbero set activo=false where id=?", barbero.getId());
        rechazar(cliente, cmd(barbero, "10:00"), 422, "RECURSO_INACTIVO");
    }

    @Test void bloqueoOcupante_409() throws Exception {
        jdbc.update("insert into bloqueo (barbero_id,inicio,fin,motivo,creado_por,creado_en) values (?,?,?,?,?,?)",
                barbero.getId(), OffsetDateTime.parse("2026-10-01T10:10-05:00"),
                OffsetDateTime.parse("2026-10-01T10:20-05:00"), "Prueba ficticia",
                barbero.getUsuario().getId(), TiempoNegocio.aLima(reloj.instant()));
        rechazar(cliente, cmd(barbero, "10:00"), 409, "FRANJA_NO_DISPONIBLE");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void recursosInexistentes_404(boolean faltaServicio) throws Exception {
        rechazar(cliente, new CrearReservaDto(faltaServicio ? 999L : servicio.getId(),
                faltaServicio ? barbero.getId() : 999L, TiempoNegocio.aLima(instante("10:00")), null),
                404, "NO_ENCONTRADO");
    }

    @Test void clienteIdEnAutoservicio_403() throws Exception {
        rechazar(cliente, new CrearReservaDto(servicio.getId(), barbero.getId(),
                TiempoNegocio.aLima(instante("10:00")), cliente.getId()), 403, "PROHIBIDO");
    }

    @ParameterizedTest @EnumSource(value = Rol.class, names = {"BARBERO"})
    void rolNoAutorizado_403(Rol rol) throws Exception {
        var usuario = usuarios.saveAndFlush(new Usuario("Personal ficticio", rol.name().toLowerCase()+"@ejemplo.test",
                null, "hash-ficticio", rol, null, reloj.instant()));
        rechazar(usuario, cmd(barbero, "10:00"), 403, "PROHIBIDO");
        assertThatThrownBy(() -> servicioReservas.crear(cmd(barbero, "10:00"), actor(usuario)))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.PROHIBIDO));
    }

    @Test void sinSesionConCsrf_401() throws Exception {
        var csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(peticion(cmd(barbero, "10:00")).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test void sesionValidaSinCsrf_403() throws Exception {
        mvc.perform(peticion(cmd(barbero, "10:00")).cookie(sesion(cliente)))
                .andExpect(status().isForbidden());
        assertThat(cantidad("reserva")).isZero();
    }

    @Test void sesionValidaCsrfIncorrecto_403() throws Exception {
        var csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(peticion(cmd(barbero, "10:00")).cookie(sesion(cliente), csrf).header("X-XSRF-TOKEN", "incorrecto"))
                .andExpect(status().isForbidden());
        assertThat(cantidad("reserva")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"servicioId\":0,\"barberoId\":1,\"inicio\":\"2026-10-01T10:00-05:00\"}",
        "{\"servicioId\":1,\"barberoId\":1,\"inicio\":\"2026-10-01T10:00\"}",
        "{\"servicioId\":1,\"barberoId\":1,\"inicio\":\"ilegible\"}"})
    void cuerpoInvalido_400(String cuerpo) throws Exception {
        mvc.perform(conCsrf(post("/api/reservas").contentType("application/json").content(cuerpo), sesion(cliente)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @ParameterizedTest @ValueSource(strings = {
        "GET /api/reservas", "PUT /api/reservas", "POST /api/reservas/100/cancelacion",
        "POST /api/reservas/100/reprogramacion", "POST /api/reservas/100/transiciones", "GET /api/reservas/100"})
    void rutasPendientesYConsultasSinPermiso_rechazan(String ruta) throws Exception {
        var partes = ruta.split(" ");
        var resultado = mvc.perform(conCsrf(request(org.springframework.http.HttpMethod.valueOf(partes[0]), partes[1]).contentType("application/json").content("{\"version\":0,\"inicio\":\"2026-10-01T11:00:00-05:00\"}"), sesion(cliente)));
        if (ruta.equals("GET /api/reservas/100") || ruta.equals("POST /api/reservas/100/cancelacion") || ruta.equals("POST /api/reservas/100/reprogramacion")) {
            // T-21/T-22/T-23 abren detalle, cancelación y reprogramación: recurso inexistente y ajeno comparten 404 (CP-02).
            resultado.andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        } else {
            resultado.andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        }
    }

    @Test void falloDelSegundoAviso_revierteReservaAuditoriaYPrimerAviso() throws Exception {
        doAnswer(invocacion -> invocacion.callRealMethod()).doThrow(new IllegalStateException("Fallo de aviso simulado"))
                .when(org.springframework.test.util.AopTestUtils.<NotificacionService>getUltimateTargetObject(notificaciones)).notificar(any(), any(), any(), anyString());
        mvc.perform(conCsrf(peticion(cmd(barbero, "10:00")), sesion(cliente)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("No se pudo completar la solicitud. Intente más tarde."));
        comprobarRollback();
    }

    @Test void falloAuditoria_revierteReserva() throws Exception {
        doThrow(new IllegalStateException("Fallo de auditoría simulado"))
                .when(org.springframework.test.util.AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria)).registrarCambio(any(), any(), any(), isNull(), isNull(), anyMap(), isNull(), eq(false));
        mvc.perform(conCsrf(peticion(cmd(barbero, "10:00")), sesion(cliente)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"));
        comprobarRollback();
    }

    private void comprobarRollback() {
        assertThat(cantidad("reserva")).isZero();
        assertThat(cantidad("auditoria_reserva")).isZero();
        assertThat(cantidad("notificacion")).isZero();
    }

    private void rechazar(Usuario usuario, CrearReservaDto cmd, int estado, String codigo) throws Exception {
        int antes = cantidad("reserva");
        mvc.perform(conCsrf(peticion(cmd), sesion(usuario))).andExpect(status().is(estado))
                .andExpect(jsonPath("$.codigo").value(codigo));
        assertThat(cantidad("reserva")).isEqualTo(antes);
    }
}
