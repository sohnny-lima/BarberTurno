package pe.barberturno.reservations;

import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.audit.*;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.reservations.dto.*;
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
class TransicionesIT extends ReservaPruebaBase {
    @MockitoSpyBean NotificacionService notificaciones;
    @MockitoSpyBean AuditoriaService auditoria;
    @Autowired PlatformTransactionManager transacciones;

    private Usuario admin() {
        return usuarios.saveAndFlush(new Usuario("Administrador ficticio", "admin@ejemplo.test", null,
                "hash-ficticio-no-utilizable", Rol.ADMIN, null, reloj.instant()));
    }

    // Fixture de estados iniciales: reutiliza el contexto sin cambiar la confirmación del producto.
    private ReservaDto reserva(EstadoReserva estado, String hora) throws Exception {
        var entidad = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, instante(hora),
                estado, cliente, reloj.instant()));
        return json.readValue(mvc.perform(get("/api/reservas/{id}", entidad.getId()).cookie(sesion(barbero.getUsuario())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ReservaDto.class);
    }

    private void hasta(String hora) { reloj.adelantar(Duration.between(reloj.instant(), instante(hora))); }

    private MockHttpServletRequestBuilder peticion(long id, EstadoReserva estado, Integer version) {
        return post("/api/reservas/{id}/transiciones", id).contentType("application/json")
                .content(json.writeValueAsString(new TransicionarReservaDto(estado, version)));
    }

    private ResultActions transicionar(ReservaDto dto, EstadoReserva destino, Usuario actor) throws Exception {
        return mvc.perform(conCsrf(peticion(dto.id(), destino, dto.version()), sesion(actor)));
    }

    private ReservaDto leer(ResultActions resultado) throws Exception {
        return json.readValue(resultado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ReservaDto.class);
    }

    private void rechazar(ReservaDto dto, EstadoReserva destino, Integer version, Usuario actor,
            int estadoHttp, String codigo) throws Exception {
        var fila = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        int historia = cantidad("auditoria_reserva"), avisos = cantidad("notificacion");
        mvc.perform(conCsrf(peticion(dto.id(), destino, version), sesion(actor)))
                .andExpect(status().is(estadoHttp)).andExpect(jsonPath("$.codigo").value(codigo));
        assertThat(jdbc.queryForMap("select * from reserva where id=?", dto.id())).isEqualTo(fila);
        assertThat(cantidad("auditoria_reserva")).isEqualTo(historia);
        assertThat(cantidad("notificacion")).isEqualTo(avisos);
    }

    private void comprobarCambio(ReservaDto antes, ReservaDto despues, Usuario actor, AccionAuditoria accion) {
        assertThat(despues.version()).isEqualTo(antes.version() + 1);
        assertThat(despues.inicio().toInstant()).isEqualTo(antes.inicio().toInstant());
        assertThat(despues.fin().toInstant()).isEqualTo(antes.fin().toInstant());
        assertThat(despues.precioRef()).isEqualByComparingTo(antes.precioRef());
        assertThat(despues.duracionMin()).isEqualTo(antes.duracionMin());
        assertThat(despues.barbero()).isEqualTo(antes.barbero());
        assertThat(despues.servicio()).isEqualTo(antes.servicio());
        assertThat(despues.cliente().telefono()).isEqualTo(cliente.getTelefono());
        var fila = jdbc.queryForMap("select * from auditoria_reserva where reserva_id=? and accion=?", antes.id(), accion.name());
        assertThat(fila.get("actor_id")).isEqualTo(actor.getId());
        assertThat(fila.get("estado_anterior")).isEqualTo(antes.estado().name());
        assertThat(fila.get("estado_nuevo")).isEqualTo(despues.estado().name());
        assertThat(fila.get("motivo")).isNull();
        assertThat(fila.get("excepcional")).isEqualTo(false);
        assertThat(((java.sql.Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(reloj.instant());
        assertThat(json.readTree(fila.get("datos_anteriores").toString())).isEqualTo(json.valueToTree(Map.of("estado", antes.estado().name())));
        assertThat(json.readTree(fila.get("datos_nuevos").toString())).isEqualTo(json.valueToTree(Map.of("estado", despues.estado().name())));
        assertThat(jdbc.queryForObject("select actualizado_en from reserva where id=?", java.sql.Timestamp.class, antes.id())
                .toInstant()).isEqualTo(reloj.instant());
        var destinatarios = jdbc.queryForList("select usuario_id from notificacion where reserva_id=? and tipo=?",
                Long.class, antes.id(), accion.name());
        if (accion == AccionAuditoria.CONFIRMAR && actor.getId() != barbero.getUsuario().getId()) {
            assertThat(destinatarios).containsExactlyInAnyOrder(cliente.getId(), barbero.getUsuario().getId());
        } else assertThat(destinatarios).containsExactly(cliente.getId());
        assertThat(jdbc.queryForList("select mensaje from notificacion where reserva_id=? and tipo=?", String.class, antes.id(), accion.name()))
                .allSatisfy(m -> assertThat(m).contains(antes.codigo(), "estado actualizado", "Lima"));
        assertThat(jdbc.queryForObject("select count(*) from notificacion where leida", Integer.class)).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cp09_flujoCompleto_asignadoYAdminTresAuditoriasYAvisos(boolean esAdmin) throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00");
        Usuario actor = esAdmin ? admin() : barbero.getUsuario();
        assertThat(dto.permisos().transiciones()).containsExactly(EstadoReserva.CONFIRMADA);
        SqlPruebas.limpiar();
        var confirmada = leer(transicionar(dto, EstadoReserva.CONFIRMADA, actor));
        comprobarCambio(dto, confirmada, actor, AccionAuditoria.CONFIRMAR);
        assertThat(confirmada.permisos().transiciones()).isEmpty();
        var bloqueos = SqlPruebas.sentencias().stream().filter(s -> s.contains("for no key update")).toList();
        assertThat(bloqueos).hasSize(1);
        assertThat(bloqueos.getFirst()).contains("from reserva");
        hasta("10:00");
        var atencion = leer(transicionar(confirmada, EstadoReserva.EN_ATENCION, actor));
        comprobarCambio(confirmada, atencion, actor, AccionAuditoria.INICIAR);
        assertThat(atencion.permisos().transiciones()).containsExactly(EstadoReserva.COMPLETADA);
        var completada = leer(transicionar(atencion, EstadoReserva.COMPLETADA, actor));
        comprobarCambio(atencion, completada, actor, AccionAuditoria.COMPLETAR);
        assertThat(completada.permisos()).isEqualTo(new ReservaDto.PermisosDto(false, false, List.of()));
        assertThat(cantidad("auditoria_reserva")).isEqualTo(3);
        assertThat(cantidad("notificacion")).isEqualTo(esAdmin ? 4 : 3);
        rechazar(completada, EstadoReserva.COMPLETADA, completada.version(), actor, 409, "TRANSICION_INVALIDA");
        rechazar(completada, EstadoReserva.COMPLETADA, atencion.version(), actor, 409, "VERSION_DESACTUALIZADA");
    }

    @ParameterizedTest @CsvSource({
        "false,EN_ATENCION,09:44,false", "false,EN_ATENCION,09:45,true", "false,EN_ATENCION,10:00,true",
        "false,NO_ASISTIO,09:59,false", "false,NO_ASISTIO,10:00,true", "false,NO_ASISTIO,10:01,true",
        "true,EN_ATENCION,09:44,false", "true,EN_ATENCION,09:45,true", "true,EN_ATENCION,10:00,true",
        "true,NO_ASISTIO,09:59,false", "true,NO_ASISTIO,10:00,true", "true,NO_ASISTIO,10:01,true"})
    void cp09_ventanasRN12_limitesInclusivos(boolean esAdmin, EstadoReserva destino, String hora, boolean permitida) throws Exception {
        var dto = reserva(EstadoReserva.CONFIRMADA, "10:00");
        var actor = esAdmin ? admin() : barbero.getUsuario();
        hasta(hora);
        var visible = json.readTree(mvc.perform(get("/api/reservas/{id}", dto.id()).cookie(sesion(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(visible.get("permisos").get("transiciones").values().stream()
                .anyMatch(v -> v.asString().equals(destino.name()))).isEqualTo(permitida);
        if (!permitida) rechazar(dto, destino, dto.version(), actor, 422, "FUERA_DE_VENTANA");
        else {
            var nueva = leer(transicionar(dto, destino, actor));
            comprobarCambio(dto, nueva, actor, destino == EstadoReserva.EN_ATENCION ? AccionAuditoria.INICIAR : AccionAuditoria.NO_ASISTIO);
            assertThat(nueva.permisos().transiciones()).containsExactlyElementsOf(destino == EstadoReserva.EN_ATENCION
                    ? List.of(EstadoReserva.COMPLETADA) : List.of());
        }
    }

    @ParameterizedTest @EnumSource(value = EstadoReserva.class, names = {"COMPLETADA", "CANCELADA", "NO_ASISTIO"})
    void cp09_estadoTerminal_rechazaTodosLosDestinos(EstadoReserva terminal) throws Exception {
        var dto = reserva(terminal, "10:00"); hasta("10:00");
        for (var destino : List.of(EstadoReserva.CONFIRMADA, EstadoReserva.EN_ATENCION, EstadoReserva.COMPLETADA, EstadoReserva.NO_ASISTIO))
            rechazar(dto, destino, dto.version(), barbero.getUsuario(), 409, "TRANSICION_INVALIDA");
    }

    @ParameterizedTest @CsvSource({"PENDIENTE,EN_ATENCION", "PENDIENTE,COMPLETADA", "PENDIENTE,NO_ASISTIO",
        "CONFIRMADA,CONFIRMADA", "CONFIRMADA,COMPLETADA", "EN_ATENCION,CONFIRMADA", "EN_ATENCION,EN_ATENCION", "EN_ATENCION,NO_ASISTIO"})
    void saltarEstadoORepetir_409(EstadoReserva actual, EstadoReserva destino) throws Exception {
        var dto = reserva(actual, "10:00");
        rechazar(dto, destino, dto.version(), barbero.getUsuario(), 409, "TRANSICION_INVALIDA");
    }

    @Test void cp09_versionAntigua_precedeAPoliticaTemporal() throws Exception {
        var dto = reserva(EstadoReserva.CONFIRMADA, "10:00");
        jdbc.update("update reserva set version=1 where id=?", dto.id());
        rechazar(dto, EstadoReserva.EN_ATENCION, 0, barbero.getUsuario(), 409, "VERSION_DESACTUALIZADA");
    }

    @Test void cp09_otroBarbero404Uniforme_sinRevelarEstadoOVersion() throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00"); var otro = perfil("ajeno").getUsuario();
        rechazar(dto, EstadoReserva.CANCELADA, 987, otro, 404, "NO_ENCONTRADO");
        var ajena = mvc.perform(conCsrf(peticion(dto.id(), EstadoReserva.CONFIRMADA, 0), sesion(otro)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var inexistente = mvc.perform(conCsrf(peticion(999, EstadoReserva.CONFIRMADA, 0), sesion(otro)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(ajena).get("detail")).isEqualTo(json.readTree(inexistente).get("detail"));
        assertThatThrownBy(() -> servicioReservas.transicionar(dto.id(), new TransicionarReservaDto(EstadoReserva.CONFIRMADA, 0), actor(otro)))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test void cp09_cliente403_enEndpointYServicio() throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00");
        rechazar(dto, EstadoReserva.CONFIRMADA, 0, cliente, 403, "PROHIBIDO");
        assertThatThrownBy(() -> servicioReservas.transicionar(dto.id(), new TransicionarReservaDto(EstadoReserva.CONFIRMADA, 0), actor(cliente)))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.PROHIBIDO));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sesionValidaSinCsrfOIncorrecto_403SinEscrituras(boolean esAdmin) throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00"); var actor = esAdmin ? admin() : barbero.getUsuario();
        var fila = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        mvc.perform(peticion(dto.id(), EstadoReserva.CONFIRMADA, 0).cookie(sesion(actor)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        Cookie csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(peticion(dto.id(), EstadoReserva.CONFIRMADA, 0).cookie(sesion(actor), csrf)
                .header("X-XSRF-TOKEN", "incorrecto")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("select * from reserva where id=?", dto.id())).isEqualTo(fila);
        assertThat(cantidad("auditoria_reserva")).isZero(); assertThat(cantidad("notificacion")).isZero();
    }

    @Test void sinSesionConCsrf_401() throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00");
        Cookie csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(peticion(dto.id(), EstadoReserva.CONFIRMADA, 0).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @ParameterizedTest @NullSource @ValueSource(ints = {-1})
    void versionObligatoriaNoNegativa_400(Integer version) throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00");
        rechazar(dto, EstadoReserva.CONFIRMADA, version, barbero.getUsuario(), 400, "VALIDACION");
    }

    @ParameterizedTest @NullSource @EnumSource(value = EstadoReserva.class, names = {"PENDIENTE", "CANCELADA"})
    void destinoAusenteONoOperativo_400AntesDeVersion(EstadoReserva destino) throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00");
        rechazar(dto, destino, 987, barbero.getUsuario(), 400, "VALIDACION");
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"estado\":\"DESCONOCIDO\",\"version\":0}", "{\"estado\":\"CONFIRMADA\"}", "{\"estado\":\"CONFIRMADA\",\"version\":\"abc\"}", "{"})
    void cuerpoInvalido_400(String cuerpo) throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00");
        mvc.perform(conCsrf(post("/api/reservas/{id}/transiciones", dto.id()).contentType("application/json")
                .content(cuerpo), sesion(barbero.getUsuario()))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        assertThat(cantidad("auditoria_reserva")).isZero();
    }

    @Test void adminConPerfilAsignado_noRecibeAvisoPropio() throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00"); var admin = admin();
        jdbc.update("update barbero set usuario_id=? where id=?", admin.getId(), barbero.getId());
        leer(transicionar(dto, EstadoReserva.CONFIRMADA, admin));
        assertThat(jdbc.queryForList("select usuario_id from notificacion", Long.class)).containsExactly(cliente.getId());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void falloAuditoriaOPrimerOSegundoAviso_revierteTodaLaTransaccion(int fallo) throws Exception {
        var dto = reserva(EstadoReserva.PENDIENTE, "10:00");
        if (fallo == 0) {
            doThrow(new IllegalStateException("Fallo de auditoría simulado"))
                .when(org.springframework.test.util.AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria))
                .registrarCambio(any(), any(), eq(AccionAuditoria.CONFIRMAR), any(), anyMap(), anyMap(), isNull(), eq(false));
        } else {
            var comportamiento = fallo == 1 ? doThrow(new IllegalStateException("Fallo de aviso simulado"))
                    : doAnswer(i -> i.callRealMethod()).doThrow(new IllegalStateException("Fallo de aviso simulado"));
            comportamiento.when(org.springframework.test.util.AopTestUtils.<NotificacionService>getUltimateTargetObject(notificaciones))
                    .notificar(any(), any(), eq(AccionAuditoria.CONFIRMAR), anyString());
        }
        rechazar(dto, EstadoReserva.CONFIRMADA, 0, admin(), 500, "ERROR_INTERNO");
    }

    @RepeatedTest(10) void dosCompletarSimultaneos_una200Otra409UnaAuditoria() throws Exception {
        var dto = reserva(EstadoReserva.EN_ATENCION, "10:00");
        var primera = conCsrf(peticion(dto.id(), EstadoReserva.COMPLETADA, 0), sesion(barbero.getUsuario()));
        var segunda = conCsrf(peticion(dto.id(), EstadoReserva.COMPLETADA, 0), sesion(admin()));
        CountDownLatch preparados = new CountDownLatch(2), salida = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            List<Future<org.springframework.mock.web.MockHttpServletResponse>> futuros = new ArrayList<>();
            for (var p : List.of(primera, segunda)) futuros.add(ejecutor.submit(() -> {
                preparados.countDown(); esperar(salida); return mvc.perform(p).andReturn().getResponse();
            }));
            try { assertThat(preparados.await(3, TimeUnit.SECONDS)).isTrue(); } finally { salida.countDown(); }
            long plazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            var respuestas = new ArrayList<org.springframework.mock.web.MockHttpServletResponse>();
            for (var f : futuros) respuestas.add(f.get(Math.max(1, plazo - System.nanoTime()), TimeUnit.NANOSECONDS));
            assertThat(respuestas.stream().map(r -> r.getStatus())).containsExactlyInAnyOrder(200, 409);
            assertThat(json.readTree(respuestas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow()
                    .getContentAsString()).get("codigo").asString()).isEqualTo("VERSION_DESACTUALIZADA");
        }
        assertThat(cantidad("auditoria_reserva")).isOne(); assertThat(cantidad("notificacion")).isOne();
        assertThat(reservas.findById(dto.id()).orElseThrow().getVersion()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"version", "estado", "reloj"})
    void esperaPostgresql_revalidaTrasBloqueo(String cambio) throws Exception {
        var dto = reserva(EstadoReserva.CONFIRMADA, "10:00"); hasta("09:44"); var actor = admin();
        CountDownLatch tomado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var propietario = ejecutor.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                reservas.bloquearPorId(dto.id()).orElseThrow();
                if (cambio.equals("version")) jdbc.update("update reserva set version=version+1 where id=?", dto.id());
                if (cambio.equals("estado")) jdbc.update("update reserva set estado='CANCELADA' where id=?", dto.id());
                tomado.countDown(); esperar(liberar);
            }));
            try {
                assertThat(tomado.await(3, TimeUnit.SECONDS)).isTrue(); var pid = new AtomicInteger();
                var resultado = ejecutor.submit(() -> new TransactionTemplate(transacciones).execute(tx -> {
                    pid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    try {
                        servicioReservas.transicionar(dto.id(), new TransicionarReservaDto(EstadoReserva.EN_ATENCION, 0), actor(actor));
                        return "OK";
                    } catch (NegocioException e) { tx.setRollbackOnly(); return e.codigo().name(); }
                }));
                observarEspera(pid);
                if (cambio.equals("reloj")) reloj.adelantar(Duration.ofMinutes(1));
                liberar.countDown(); propietario.get(5, TimeUnit.SECONDS);
                assertThat(resultado.get(5, TimeUnit.SECONDS)).isEqualTo(switch (cambio) {
                    case "version" -> "VERSION_DESACTUALIZADA"; case "estado" -> "TRANSICION_INVALIDA"; default -> "OK";
                });
            } finally { liberar.countDown(); }
        }
        assertThat(cantidad("auditoria_reserva")).isEqualTo(cambio.equals("reloj") ? 1 : 0);
        assertThat(cantidad("notificacion")).isEqualTo(cambio.equals("reloj") ? 1 : 0);
    }

    private void observarEspera(AtomicInteger pid) throws InterruptedException {
        long plazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < plazo) {
            if (pid.get() != 0 && Boolean.TRUE.equals(jdbc.queryForObject(
                    "select coalesce((select wait_event_type='Lock' from pg_stat_activity where pid=?),false)", Boolean.class, pid.get()))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL no registró la espera por ③.");
    }

    private static void esperar(CountDownLatch latch) {
        try { if (!latch.await(4, TimeUnit.SECONDS)) throw new AssertionError("No se liberó el hilo de prueba."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    @Test void corridaManualPaso6_r102Confirmada28SeptiembreAtendida1Octubre1030A1050() throws Exception {
        reserva(EstadoReserva.PENDIENTE, "09:00"); reserva(EstadoReserva.PENDIENTE, "10:00");
        servicio.editar("Barba ficticia", "Perfilado", (short) 20, new java.math.BigDecimal("20.00"), reloj.instant());
        servicios.saveAndFlush(servicio);
        var r102 = reserva(EstadoReserva.PENDIENTE, "10:30"); assertThat(r102.id()).isEqualTo(102);
        var confirmada = leer(transicionar(r102, EstadoReserva.CONFIRMADA, barbero.getUsuario()));
        comprobarCambio(r102, confirmada, barbero.getUsuario(), AccionAuditoria.CONFIRMAR);
        assertThat(TiempoNegocio.aLima(reloj.instant()).toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 28));
        hasta("10:30"); var atencion = leer(transicionar(confirmada, EstadoReserva.EN_ATENCION, barbero.getUsuario()));
        comprobarCambio(confirmada, atencion, barbero.getUsuario(), AccionAuditoria.INICIAR);
        hasta("10:50"); var completada = leer(transicionar(atencion, EstadoReserva.COMPLETADA, barbero.getUsuario()));
        comprobarCambio(atencion, completada, barbero.getUsuario(), AccionAuditoria.COMPLETAR);
        assertThat(completada.estado()).isEqualTo(EstadoReserva.COMPLETADA);
        assertThat(completada.fin().toInstant()).isEqualTo(instante("10:50"));
        assertThat(jdbc.queryForList("select creado_en from auditoria_reserva where reserva_id=102 order by id", java.sql.Timestamp.class)
                .stream().map(java.sql.Timestamp::toInstant)).containsExactly(RelojAjustable.INICIAL, instante("10:30"), instante("10:50"));
    }
}
