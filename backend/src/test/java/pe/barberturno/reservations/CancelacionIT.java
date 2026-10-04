package pe.barberturno.reservations;

import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.audit.*;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.Barbero;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CancelacionIT extends ReservaPruebaBase {
    @MockitoSpyBean NotificacionService notificaciones;
    @MockitoSpyBean AuditoriaService auditoria;
    @Autowired PlatformTransactionManager transacciones;

    private ReservaDto crear() throws Exception {
        var dto = crearHttp(cliente, cmd(barbero, "10:00"));
        adelantarHasta("07:00");
        return dto;
    }

    private void adelantarHasta(String hora) {
        reloj.adelantar(Duration.between(reloj.instant(), instante(hora)));
    }

    private Usuario admin() {
        return usuarios.saveAndFlush(new Usuario("Administrador ficticio", "admin@ejemplo.test", null,
                "hash-ficticio-no-utilizable", Rol.ADMIN, null, reloj.instant()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion(
            long id, Integer version, String motivo) {
        return post("/api/reservas/{id}/cancelacion", id).contentType("application/json")
                .content(json.writeValueAsString(new CancelarReservaDto(version, motivo)));
    }

    private org.springframework.test.web.servlet.ResultActions cancelar(ReservaDto dto,
            Usuario actor, String motivo) throws Exception {
        return mvc.perform(conCsrf(peticion(dto.id(), dto.version(), motivo), sesion(actor)));
    }

    private ReservaDto leer(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        return json.readValue(resultado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReservaDto.class);
    }

    private int cancelaciones() {
        return jdbc.queryForObject("select count(*) from auditoria_reserva where accion='CANCELAR'", Integer.class);
    }

    private void rechazar(ReservaDto dto, Usuario actor, Integer version, String motivo,
            int estado, String codigo) throws Exception {
        var fila = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        int historial = cantidad("auditoria_reserva"), avisos = cantidad("notificacion");
        mvc.perform(conCsrf(peticion(dto.id(), version, motivo), sesion(actor)))
                .andExpect(status().is(estado)).andExpect(jsonPath("$.codigo").value(codigo));
        assertThat(jdbc.queryForMap("select * from reserva where id=?", dto.id())).isEqualTo(fila);
        assertThat(cantidad("auditoria_reserva")).isEqualTo(historial);
        assertThat(cantidad("notificacion")).isEqualTo(avisos);
    }

    @Test void cp06_clienteATresHoras_cancelaConservaHistoriaYLiberaDisponibilidad() throws Exception {
        var dto = crear();
        var fila = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        var antes = disponibilidad();
        assertThat(antes.get("franjas").values())
                .noneMatch(f -> f.get("inicio").asString().equals("2026-10-01T10:00:00-05:00"));
        SqlPruebas.limpiar();
        var nueva = leer(cancelar(dto, cliente, null));
        assertThat(nueva.id()).isEqualTo(dto.id());
        assertThat(nueva.estado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(nueva.version()).isEqualTo(dto.version() + 1);
        assertThat(nueva.permisos()).isEqualTo(new ReservaDto.PermisosDto(false, false, List.of()));
        assertThat(nueva.cliente().telefono()).isNull();
        assertThat(nueva.inicio()).isEqualTo(dto.inicio());
        assertThat(nueva.fin()).isEqualTo(dto.fin());
        assertThat(nueva.precioRef()).isEqualByComparingTo(dto.precioRef());
        assertThat(nueva.duracionMin()).isEqualTo(dto.duracionMin());
        var posterior = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        fila.put("estado", "CANCELADA");
        fila.put("version", 1);
        fila.put("actualizado_en", posterior.get("actualizado_en"));
        assertThat(posterior).isEqualTo(fila);
        assertThat(((java.sql.Timestamp) posterior.get("actualizado_en")).toInstant()).isEqualTo(reloj.instant());
        assertThat(cantidad("reserva")).isOne();
        assertThat(cancelaciones()).isOne();
        var sql = SqlPruebas.sentencias().stream().filter(s -> s.contains("for no key update")).toList();
        assertThat(sql).hasSize(1);
        assertThat(sql.getFirst()).contains("from reserva");
        assertThat(disponibilidad().get("franjas").values())
                .anyMatch(f -> f.get("inicio").asString().equals("2026-10-01T10:00:00-05:00")
                        && f.get("barberoIds").get(0).asLong() == barbero.getId());
        var nuevaReserva = crearHttp(datos.cliente("nuevo"), cmd(barbero, "10:00"));
        assertThat(nuevaReserva.id()).isNotEqualTo(dto.id());
    }

    private tools.jackson.databind.JsonNode disponibilidad() throws Exception {
        return json.readTree(mvc.perform(get("/api/disponibilidad").param("servicioId", servicio.getId().toString())
                .param("fecha", "2026-10-01").param("barberoId", barbero.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @ParameterizedTest @CsvSource({"07:00,true", "08:00,true", "08:01,false", "10:00,false", "10:01,false"})
    void cp06_ventanaCliente_limiteExactoPermitido(String hora, boolean permitida) throws Exception {
        var dto = crear();
        adelantarHasta(hora);
        if (permitida) leer(cancelar(dto, cliente, null));
        else rechazar(dto, cliente, dto.version(), null, 422, "FUERA_DE_POLITICA");
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "     ", "abcd", "a b c d", "\u00a0a\u2003b c d"})
    void adminATreintaMinutos_motivoInsuficiente422(String motivo) throws Exception {
        var dto = crear();
        adelantarHasta("09:30");
        rechazar(dto, admin(), dto.version(), motivo, 422, "MOTIVO_REQUERIDO");
    }

    @ParameterizedTest @CsvSource({"07:00,false", "08:00,false", "08:01,true", "09:30,true"})
    void adminConMotivo_registraExcepcionSoloFueraDeVentanaCliente(String hora, boolean excepcional) throws Exception {
        var dto = crear();
        adelantarHasta(hora);
        var admin = admin();
        String motivo = "a b c d e";
        var nueva = leer(cancelar(dto, admin, motivo));
        assertThat(nueva.cliente().telefono()).isEqualTo(cliente.getTelefono());
        comprobarAuditoria(dto, admin, motivo, excepcional);
        comprobarAvisos(cliente.getId(), barbero.getUsuario().getId());
    }

    @Test void adminATresHoras_tambienExigeMotivo() throws Exception {
        var dto = crear();
        rechazar(dto, admin(), dto.version(), null, 422, "MOTIVO_REQUERIDO");
    }

    private void comprobarAuditoria(ReservaDto dto, Usuario actor, String motivo, boolean excepcional) {
        var fila = jdbc.queryForMap("select * from auditoria_reserva where accion='CANCELAR'");
        assertThat(fila.get("reserva_id")).isEqualTo(dto.id());
        assertThat(fila.get("actor_id")).isEqualTo(actor.getId());
        assertThat(fila.get("estado_anterior")).isEqualTo(dto.estado().name());
        assertThat(fila.get("estado_nuevo")).isEqualTo("CANCELADA");
        assertThat(fila.get("motivo")).isEqualTo(motivo);
        assertThat(fila.get("excepcional")).isEqualTo(excepcional);
        assertThat(((java.sql.Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(reloj.instant());
        for (String campo : List.of("datos_anteriores", "datos_nuevos")) {
            var datos = json.readTree(fila.get(campo).toString());
            assertThat(datos.size()).isEqualTo(4);
            assertThat(datos.get("inicio").asString()).isEqualTo(TiempoNegocio.aLima(instante("10:00")).toString());
            assertThat(datos.get("fin").asString()).isEqualTo(TiempoNegocio.aLima(instante("10:30")).toString());
            assertThat(datos.get("barberoId").asLong()).isEqualTo(barbero.getId());
            assertThat(datos.get("estado").asString()).isEqualTo(
                    campo.equals("datos_anteriores") ? dto.estado().name() : "CANCELADA");
        }
    }

    private void comprobarAvisos(Long... destinatarios) {
        assertThat(jdbc.queryForList("select usuario_id from notificacion where tipo='CANCELAR'", Long.class))
                .containsExactlyInAnyOrder(destinatarios);
        assertThat(jdbc.queryForList("select mensaje from notificacion where tipo='CANCELAR'", String.class))
                .allSatisfy(m -> assertThat(m).contains("BT-100", "cancelada", "01/10/2026 10:00", "Lima"));
        assertThat(jdbc.queryForObject("select count(*) from notificacion where tipo='CANCELAR' and not leida",
                Integer.class)).isEqualTo(destinatarios.length);
    }

    @ParameterizedTest @ValueSource(strings = {"10:00", "10:01"})
    void adminAlInicioODespues_422AunqueFalteMotivo(String hora) throws Exception {
        var dto = crear();
        adelantarHasta(hora);
        var admin = admin();
        rechazar(dto, admin, dto.version(), "Motivo válido", 422, "FUERA_DE_POLITICA");
        rechazar(dto, admin, dto.version(), null, 422, "FUERA_DE_POLITICA");
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "x"})
    void clienteMotivoOpcional_auditoriaYAvisos(String motivo) throws Exception {
        var dto = crear();
        leer(cancelar(dto, cliente, motivo));
        comprobarAuditoria(dto, cliente, motivo, false);
        comprobarAvisos(cliente.getId(), barbero.getUsuario().getId());
    }

    @Test void adminConPerfilAsignado_noSeNotificaASiMismo() throws Exception {
        var dto = crear();
        var admin = admin();
        jdbc.update("update barbero set usuario_id=? where id=?", admin.getId(), barbero.getId());
        leer(cancelar(dto, admin, "Ausencia justificada"));
        comprobarAvisos(cliente.getId());
        comprobarAuditoria(dto, admin, "Ausencia justificada", false);
    }

    @Test void repetirConVersionActual_409TransicionYSinAuditoriaDuplicada() throws Exception {
        var dto = crear();
        var nueva = leer(cancelar(dto, cliente, null));
        rechazar(nueva, cliente, nueva.version(), null, 409, "TRANSICION_INVALIDA");
        rechazar(nueva, cliente, dto.version(), null, 409, "VERSION_DESACTUALIZADA");
        assertThat(cancelaciones()).isOne();
    }

    @Test void versionAntiguaConReservaConfirmada_409() throws Exception {
        var dto = crear();
        jdbc.update("update reserva set version=1 where id=?", dto.id());
        rechazar(dto, cliente, dto.version(), null, 409, "VERSION_DESACTUALIZADA");
    }

    @ParameterizedTest @EnumSource(value = EstadoReserva.class,
            names = {"EN_ATENCION", "COMPLETADA", "CANCELADA", "NO_ASISTIO"})
    void estadoNoCancelable_409(EstadoReserva estado) throws Exception {
        var dto = crear();
        jdbc.update("update reserva set estado=? where id=?", estado.name(), dto.id());
        rechazar(dto, cliente, dto.version(), null, 409, "TRANSICION_INVALIDA");
    }

    @Test void barberoAsignado_403TambienEnServicio() throws Exception {
        var dto = crear();
        rechazar(dto, barbero.getUsuario(), dto.version(), null, 403, "PROHIBIDO");
        assertThatThrownBy(() -> servicioReservas.cancelar(dto.id(), new CancelarReservaDto(0, null),
                actor(barbero.getUsuario()))).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.PROHIBIDO));
    }

    @Test void otroCliente_404UniformeInclusoConVersionIncorrecta() throws Exception {
        var dto = crear();
        var otro = datos.cliente("otro");
        rechazar(dto, otro, 987, null, 404, "NO_ENCONTRADO");
        var ajena = mvc.perform(conCsrf(peticion(dto.id(), 0, null), sesion(otro)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var inexistente = mvc.perform(conCsrf(peticion(999, 0, null), sesion(otro)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(ajena).get("detail")).isEqualTo(json.readTree(inexistente).get("detail"));
        assertThatThrownBy(() -> servicioReservas.cancelar(dto.id(), new CancelarReservaDto(0, null),
                actor(otro))).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
        var otroBarbero = perfil("ajeno");
        assertThatThrownBy(() -> servicioReservas.cancelar(dto.id(), new CancelarReservaDto(0, null),
                actor(otroBarbero.getUsuario()))).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test void sinSesionConCsrf_401() throws Exception {
        var dto = crear();
        var csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(peticion(dto.id(), 0, null).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        assertThat(cancelaciones()).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sesionValidaSinCsrf_403ClienteYAdmin(boolean esAdmin) throws Exception {
        var dto = crear();
        mvc.perform(peticion(dto.id(), 0, "Motivo válido").cookie(sesion(esAdmin ? admin() : cliente)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(cancelaciones()).isZero();
        assertThat(cantidad("notificacion")).isEqualTo(2);
        assertThat(reservas.findById(dto.id()).orElseThrow().getVersion()).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sesionValidaCsrfIncorrecto_403ClienteYAdmin(boolean esAdmin) throws Exception {
        var dto = crear();
        var csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(peticion(dto.id(), 0, "Motivo válido").cookie(sesion(esAdmin ? admin() : cliente), csrf)
                .header("X-XSRF-TOKEN", "incorrecto")).andExpect(status().isForbidden());
        assertThat(cancelaciones()).isZero();
        assertThat(cantidad("notificacion")).isEqualTo(2);
        assertThat(reservas.findById(dto.id()).orElseThrow().getVersion()).isZero();
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {-1})
    void versionObligatoriaNoNegativa_400(Integer version) throws Exception {
        var dto = crear();
        rechazar(dto, cliente, version, null, 400, "VALIDACION");
    }

    @Test void motivoTrescientosCaracteresPermitidoExceso400() throws Exception {
        var dto = crear();
        rechazar(dto, cliente, dto.version(), "x".repeat(301), 400, "VALIDACION");
        leer(cancelar(dto, cliente, "x".repeat(300)));
        comprobarAuditoria(dto, cliente, "x".repeat(300), false);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void falloAuditoriaOSegundoAviso_revierteEstadoVersionHistoriaYAvisos(boolean fallaAviso) throws Exception {
        var dto = crear();
        if (fallaAviso) {
            doAnswer(invocacion -> invocacion.callRealMethod())
                    .doThrow(new IllegalStateException("Fallo de aviso simulado"))
                    .when(org.springframework.test.util.AopTestUtils.<NotificacionService>getUltimateTargetObject(notificaciones))
                    .notificar(any(), any(), eq(AccionAuditoria.CANCELAR), anyString());
        } else {
            doThrow(new IllegalStateException("Fallo de auditoría simulado"))
                    .when(org.springframework.test.util.AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria))
                    .registrarCambio(any(), any(), eq(AccionAuditoria.CANCELAR), any(), anyMap(), anyMap(), isNull(), eq(false));
        }
        rechazar(dto, cliente, dto.version(), null, 500, "ERROR_INTERNO");
        assertThat(cancelaciones()).isZero();
    }

    @RepeatedTest(10) void dosCancelacionesSimultaneas_una200Otra409UnaAuditoria() throws Exception {
        var dto = crear();
        var admin = admin();
        var primera = conCsrf(peticion(dto.id(), 0, null), sesion(cliente));
        var segunda = conCsrf(peticion(dto.id(), 0, "Ausencia justificada"), sesion(admin));
        CountDownLatch preparados = new CountDownLatch(2), salida = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            List<Future<org.springframework.mock.web.MockHttpServletResponse>> futuros = new ArrayList<>();
            for (var peticion : List.of(primera, segunda)) {
                futuros.add(ejecutor.submit(() -> {
                    preparados.countDown();
                    esperar(salida);
                    return mvc.perform(peticion).andReturn().getResponse();
                }));
            }
            try { assertThat(preparados.await(3, TimeUnit.SECONDS)).isTrue(); }
            finally { salida.countDown(); }
            var respuestas = new ArrayList<org.springframework.mock.web.MockHttpServletResponse>();
            for (var futuro : futuros) respuestas.add(futuro.get(5, TimeUnit.SECONDS));
            assertThat(respuestas.stream().map(r -> r.getStatus())).containsExactlyInAnyOrder(200, 409);
            var rechazo = respuestas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
            assertThat(json.readTree(rechazo.getContentAsString()).get("codigo").asString()).isEqualTo("VERSION_DESACTUALIZADA");
        }
        assertThat(cancelaciones()).isOne();
        assertThat(reservas.findById(dto.id()).orElseThrow().getVersion()).isEqualTo(1);
        comprobarAvisos(cliente.getId(), barbero.getUsuario().getId());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void esperaRealPorReserva_revalidaVersionORelojTrasBloqueo(boolean cambiaVersion) throws Exception {
        var dto = crear();
        adelantarHasta("08:00");
        CountDownLatch tomado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var propietario = ejecutor.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                var entidad = reservas.bloquearPorId(dto.id()).orElseThrow();
                if (cambiaVersion) {
                    entidad.cambiarEstado(EstadoReserva.CANCELADA, reloj.instant());
                    reservas.saveAndFlush(entidad);
                }
                tomado.countDown();
                esperar(liberar);
            }));
            try {
                assertThat(tomado.await(3, TimeUnit.SECONDS)).isTrue();
                var pid = new java.util.concurrent.atomic.AtomicInteger();
                var resultado = ejecutor.submit(() -> new TransactionTemplate(transacciones).execute(tx -> {
                    pid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    try {
                        servicioReservas.cancelar(dto.id(), new CancelarReservaDto(0, null), actor(cliente));
                        return "OK";
                    } catch (NegocioException e) { tx.setRollbackOnly(); return e.codigo().name(); }
                }));
                observarEspera(pid);
                if (!cambiaVersion) reloj.adelantar(Duration.ofMinutes(1));
                liberar.countDown();
                propietario.get(5, TimeUnit.SECONDS);
                assertThat(resultado.get(5, TimeUnit.SECONDS)).isEqualTo(
                        cambiaVersion ? "VERSION_DESACTUALIZADA" : "FUERA_DE_POLITICA");
            } finally { liberar.countDown(); }
        }
        assertThat(cancelaciones()).isZero();
        assertThat(cantidad("notificacion")).isEqualTo(2);
    }

    private void observarEspera(java.util.concurrent.atomic.AtomicInteger pid) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            if (pid.get() != 0 && Boolean.TRUE.equals(jdbc.queryForObject(
                    "select coalesce((select wait_event_type='Lock' from pg_stat_activity where pid=?),false)",
                    Boolean.class, pid.get()))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL no registró la espera por ③.");
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(4, TimeUnit.SECONDS)) throw new AssertionError("No se liberó el hilo de prueba.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
