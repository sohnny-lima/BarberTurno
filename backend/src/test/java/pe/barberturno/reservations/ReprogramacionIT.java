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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
class ReprogramacionIT extends ReservaPruebaBase {
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

    private ReprogramarReservaDto mover(String hora, Long destino, Integer version, String motivo) {
        return new ReprogramarReservaDto(TiempoNegocio.aLima(instante(hora)), destino, version, motivo);
    }

    private MockHttpServletRequestBuilder peticion(long id, ReprogramarReservaDto cmd) {
        return post("/api/reservas/{id}/reprogramacion", id).contentType("application/json")
                .content(json.writeValueAsString(cmd));
    }

    private ReservaDto reprogramar(ReservaDto dto, Usuario actor, ReprogramarReservaDto cmd) throws Exception {
        return json.readValue(mvc.perform(conCsrf(peticion(dto.id(), cmd), sesion(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ReservaDto.class);
    }

    private void rechazar(ReservaDto dto, Usuario actor, ReprogramarReservaDto cmd,
            int estado, String codigo) throws Exception {
        var fila = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        int historial = cantidad("auditoria_reserva"), avisos = cantidad("notificacion");
        mvc.perform(conCsrf(peticion(dto.id(), cmd), sesion(actor)))
                .andExpect(status().is(estado)).andExpect(jsonPath("$.codigo").value(codigo));
        assertThat(jdbc.queryForMap("select * from reserva where id=?", dto.id())).isEqualTo(fila);
        assertThat(cantidad("auditoria_reserva")).isEqualTo(historial);
        assertThat(cantidad("notificacion")).isEqualTo(avisos);
    }

    private void avisos(Long... destinatarios) {
        assertThat(jdbc.queryForList("select usuario_id from notificacion where tipo='REPROGRAMAR'", Long.class))
                .containsExactlyInAnyOrder(destinatarios);
        assertThat(jdbc.queryForList("select mensaje from notificacion where tipo='REPROGRAMAR'", String.class))
                .allSatisfy(m -> assertThat(m).contains("reprogramada", "Lima"));
    }

    @Test void cp05_franjaOcupada_conservaFilaCompletaVersionReferenciasYAuditoria() throws Exception {
        var dto = crear();
        crearHttp(datos.cliente("otro"), cmd(barbero, "11:00"));
        rechazar(dto, cliente, mover("11:00", null, 0, null), 409, "FRANJA_NO_DISPONIBLE");
    }

    @Test void cp05_franjaLibre_conservaPrecioDuracionYServicioAunqueCatalogoCambieYSeDesactive() throws Exception {
        var dto = crear();
        jdbc.update("update servicio set precio=99.99, duracion_min=60, activo=false where id=?", servicio.getId());
        var nueva = reprogramar(dto, cliente, mover("11:00", null, 0, null));
        assertThat(nueva.precioRef()).isEqualByComparingTo(dto.precioRef());
        assertThat(nueva.duracionMin()).isEqualTo(dto.duracionMin());
        assertThat(nueva.servicio().id()).isEqualTo(dto.servicio().id());
        assertThat(nueva.inicio().toInstant()).isEqualTo(instante("11:00"));
        assertThat(nueva.fin().toInstant()).isEqualTo(instante("11:30"));
        assertThat(nueva.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(nueva.version()).isEqualTo(1);
        avisos(barbero.getUsuario().getId());
    }

    @Test void cp05_desplazarDiezMinutos_excluyeReservaPropiaEnAmbosSolapes() throws Exception {
        var dto = crear();
        var nueva = reprogramar(dto, cliente, mover("10:10", null, 0, null));
        assertThat(nueva.inicio().toInstant()).isEqualTo(instante("10:10"));
        assertThat(nueva.fin().toInstant()).isEqualTo(instante("10:40"));
    }

    @Test void cambiarBarbero_adminAuditaSnapshotsExactosYAvisaClienteYAmbosProfesionales() throws Exception {
        var dto = crear();
        var nuevo = perfil("miguel");
        var actor = admin();
        var nueva = reprogramar(dto, actor, mover("11:00", nuevo.getId(), 0, "Cambio solicitado"));
        assertThat(nueva.barbero().id()).isEqualTo(nuevo.getId());
        avisos(cliente.getId(), barbero.getUsuario().getId(), nuevo.getUsuario().getId());
        var fila = jdbc.queryForMap("select * from auditoria_reserva where accion='REPROGRAMAR'");
        assertThat(fila.get("actor_id")).isEqualTo(actor.getId());
        assertThat(fila.get("reserva_id")).isEqualTo(dto.id());
        assertThat(fila.get("estado_anterior")).isEqualTo("CONFIRMADA");
        assertThat(fila.get("estado_nuevo")).isEqualTo("CONFIRMADA");
        assertThat(fila.get("motivo")).isEqualTo("Cambio solicitado");
        assertThat(fila.get("excepcional")).isEqualTo(false);
        assertThat(((java.sql.Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(reloj.instant());
        assertThat(json.readTree(fila.get("datos_anteriores").toString())).isEqualTo(json.readTree(
                json.writeValueAsString(Map.of("inicio", TiempoNegocio.aLima(instante("10:00")).toString(),
                        "fin", TiempoNegocio.aLima(instante("10:30")).toString(), "barberoId", barbero.getId()))));
        assertThat(json.readTree(fila.get("datos_nuevos").toString())).isEqualTo(json.readTree(
                json.writeValueAsString(Map.of("inicio", TiempoNegocio.aLima(instante("11:00")).toString(),
                        "fin", TiempoNegocio.aLima(instante("11:30")).toString(), "barberoId", nuevo.getId()))));
    }

    @Test void mismoBarberoExplicito_noDuplicaAvisosYOrdenaBloqueos() throws Exception {
        var dto = crear();
        var actor = admin();
        SqlPruebas.limpiar();
        reprogramar(dto, actor, mover("11:00", barbero.getId(), 0, "Cambio solicitado"));
        avisos(cliente.getId(), barbero.getUsuario().getId());
        var sql = SqlPruebas.sentencias().stream().filter(s -> s.contains("for no key update")).toList();
        assertThat(sql).hasSize(3);
        assertThat(sql.get(0)).contains("from usuario");
        assertThat(sql.get(1)).contains("from barbero", "order by");
        assertThat(sql.get(2)).contains("from reserva");
    }

    @Test void adminConPerfil_noRecibeAutoaviso() throws Exception {
        var dto = crear();
        var actor = admin();
        jdbc.update("update barbero set usuario_id=? where id=?", actor.getId(), barbero.getId());
        var nuevo = perfil("nuevo");
        reprogramar(dto, actor, mover("11:00", nuevo.getId(), 0, "Cambio solicitado"));
        avisos(cliente.getId(), nuevo.getUsuario().getId());
    }

    @Test void solapeClienteConOtroBarbero_409SinCambios() throws Exception {
        var dto = crear();
        var otro = perfil("otro");
        crearHttp(cliente, cmd(otro, "11:00"));
        rechazar(dto, cliente, mover("11:00", null, 0, null), 409, "CLIENTE_CON_RESERVA_SOLAPADA");
    }

    @ParameterizedTest @EnumSource(value = EstadoReserva.class,
            names = {"PENDIENTE", "EN_ATENCION", "COMPLETADA", "CANCELADA", "NO_ASISTIO"})
    void soloConfirmada_409SinCambios(EstadoReserva estado) throws Exception {
        var dto = crear();
        jdbc.update("update reserva set estado=? where id=?", estado.name(), dto.id());
        rechazar(dto, cliente, mover("11:00", null, 0, null), 409, "TRANSICION_INVALIDA");
    }

    @Test void versionAntigua_409AntesDeEstadoOPolitica() throws Exception {
        var dto = crear();
        jdbc.update("update reserva set version=1, estado='CANCELADA' where id=?", dto.id());
        rechazar(dto, cliente, mover("11:00", null, 0, null), 409, "VERSION_DESACTUALIZADA");
    }

    @ParameterizedTest @CsvSource({"07:00,true", "08:00,true", "08:01,false", "10:00,false", "10:01,false"})
    void clienteReglaDosHoras_incluyeLimite(String hora, boolean permitida) throws Exception {
        var dto = crear();
        adelantarHasta(hora);
        if (permitida) reprogramar(dto, cliente, mover("11:00", null, 0, null));
        else rechazar(dto, cliente, mover("11:00", null, 0, null), 422, "FUERA_DE_POLITICA");
    }

    @ParameterizedTest @CsvSource({"07:00,false", "08:00,false", "08:01,true", "09:30,true"})
    void adminConMotivo_excepcionalSoloFueraDePlazoCliente(String hora, boolean excepcional) throws Exception {
        var dto = crear();
        adelantarHasta(hora);
        reprogramar(dto, admin(), mover("11:00", null, 0, "Cambio solicitado"));
        assertThat(jdbc.queryForObject("select excepcional from auditoria_reserva where accion='REPROGRAMAR'",
                Boolean.class)).isEqualTo(excepcional);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "abcd", "a b c d", "\u00a0a\u2003b c d"})
    void adminMotivoInsuficiente_422(String motivo) throws Exception {
        var dto = crear();
        adelantarHasta("09:30");
        rechazar(dto, admin(), mover("11:00", null, 0, motivo), 422, "MOTIVO_REQUERIDO");
    }

    @Test void adminDentroDePlazo_tambienExigeMotivo() throws Exception {
        var dto = crear();
        rechazar(dto, admin(), mover("11:00", null, 0, null), 422, "MOTIVO_REQUERIDO");
    }

    @ParameterizedTest @ValueSource(strings = {"10:00", "10:01"})
    void adminAlInicioODespues_fueraPoliticaAunqueFalteMotivo(String hora) throws Exception {
        var dto = crear();
        adelantarHasta(hora);
        rechazar(dto, admin(), mover("11:00", null, 0, null), 422, "FUERA_DE_POLITICA");
    }

    @Test void barberoNuevoInactivo_422() throws Exception {
        var dto = crear();
        var nuevo = perfil("inactivo");
        jdbc.update("update barbero set activo=false where id=?", nuevo.getId());
        rechazar(dto, cliente, mover("11:00", nuevo.getId(), 0, null), 422, "RECURSO_INACTIVO");
    }

    @Test void barberoActualInactivo_permiteMoverAOtroActivo() throws Exception {
        var dto = crear();
        var nuevo = perfil("activo");
        jdbc.update("update barbero set activo=false where id=?", barbero.getId());
        reprogramar(dto, cliente, mover("11:00", nuevo.getId(), 0, null));
    }

    @ParameterizedTest @ValueSource(strings = {"11:01", "08:50", "17:50"})
    void rejillaOFueraJornada_422(String hora) throws Exception {
        var dto = crear();
        rechazar(dto, cliente, mover(hora, null, 0, null), 422, "FUERA_DE_HORARIO");
    }

    @Test void destinoPasadoYFueraHorizonte_422() throws Exception {
        var dto = crear();
        adelantarHasta("08:00");
        rechazar(dto, cliente, new ReprogramarReservaDto(TiempoNegocio.aLima(reloj.instant()), null, 0, null),
                422, "INICIO_EN_PASADO");
        rechazar(dto, cliente, new ReprogramarReservaDto(TiempoNegocio.aLima(instante("11:00")).plusDays(31),
                null, 0, null), 422, "FUERA_DE_HORIZONTE");
    }

    @Test void barberoAsignado_403HttpYServicio() throws Exception {
        var dto = crear();
        var cmd = mover("11:00", null, 0, null);
        rechazar(dto, barbero.getUsuario(), cmd, 403, "PROHIBIDO");
        assertThatThrownBy(() -> servicioReservas.reprogramar(dto.id(), cmd, actor(barbero.getUsuario())))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.PROHIBIDO));
    }

    @Test void clienteAjeno404_antesDeVersionDestinoOEstado() throws Exception {
        var dto = crear();
        var otro = datos.cliente("otro");
        rechazar(dto, otro, mover("11:00", 999L, 99, null), 404, "NO_ENCONTRADO");
        mvc.perform(conCsrf(peticion(999, mover("11:00", null, 0, null)), sesion(otro)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicioReservas.reprogramar(dto.id(), mover("11:00", null, 0, null),
                actor(perfil("ajeno").getUsuario()))).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test void barberoInexistente404_sinCambios() throws Exception {
        var dto = crear();
        rechazar(dto, cliente, mover("11:00", 999L, 0, null), 404, "NO_ENCONTRADO");
    }

    @Test void sinSesionConCsrf_401() throws Exception {
        var dto = crear();
        Cookie csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(peticion(dto.id(), mover("11:00", null, 0, null)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void sesionValidaSinCsrfOIncorrecto403_clienteYAdmin(boolean esAdmin, boolean incorrecto) throws Exception {
        var dto = crear();
        var fila = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        var peticion = peticion(dto.id(), mover("11:00", null, 0, "Cambio solicitado"))
                .cookie(sesion(esAdmin ? admin() : cliente));
        if (incorrecto) {
            Cookie csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
            peticion.cookie(csrf).header("X-XSRF-TOKEN", "incorrecto");
        }
        mvc.perform(peticion).andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("select * from reserva where id=?", dto.id())).isEqualTo(fila);
        assertThat(cantidad("auditoria_reserva")).isOne();
        assertThat(cantidad("notificacion")).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"inicio\":\"2026-10-01T11:00:00-05:00\",\"version\":-1}",
        "{\"inicio\":\"2026-10-01T11:00:00-05:00\",\"version\":0,\"barberoId\":0}",
        "{\"inicio\":\"2026-10-01T11:00:00\",\"version\":0}"})
    void cuerpoInvalido400(String cuerpo) throws Exception {
        var dto = crear();
        mvc.perform(conCsrf(post("/api/reservas/{id}/reprogramacion", dto.id())
                .contentType("application/json").content(cuerpo), sesion(cliente)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test void motivoLargo400_clienteMotivoOpcionalConservado() throws Exception {
        var dto = crear();
        rechazar(dto, cliente, mover("11:00", null, 0, "x".repeat(301)), 400, "VALIDACION");
        reprogramar(dto, cliente, mover("11:00", null, 0, "x".repeat(300)));
        assertThat(jdbc.queryForObject("select motivo from auditoria_reserva where accion='REPROGRAMAR'",
                String.class)).isEqualTo("x".repeat(300));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void falloAuditoriaOSegundoAviso_revierteTodaFilaYHistorial(boolean fallaAviso) throws Exception {
        var dto = crear();
        var nuevo = perfil("nuevo");
        if (fallaAviso) {
            doAnswer(invocacion -> invocacion.callRealMethod()).doThrow(new IllegalStateException("Fallo de aviso simulado"))
                    .when(org.springframework.test.util.AopTestUtils.<NotificacionService>getUltimateTargetObject(notificaciones))
                    .notificar(any(), any(), eq(AccionAuditoria.REPROGRAMAR), anyString());
        } else {
            doThrow(new IllegalStateException("Fallo de auditoría simulado"))
                    .when(org.springframework.test.util.AopTestUtils.<AuditoriaService>getUltimateTargetObject(auditoria))
                    .registrarCambio(any(), any(), eq(AccionAuditoria.REPROGRAMAR), any(), anyMap(), anyMap(), isNull(), eq(false));
        }
        rechazar(dto, cliente, mover("11:00", nuevo.getId(), 0, null), 500, "ERROR_INTERNO");
    }

    @RepeatedTest(20) void cruzarDosBarberos_sinInterbloqueoAmbasTerminanAntesDeCincoSegundos() throws Exception {
        var r1 = crear();
        var miguel = perfil("miguel");
        var otro = datos.cliente("otro");
        var r2 = crearHttp(otro, cmd(miguel, "11:00"));
        var respuestas = simultaneas(
                conCsrf(peticion(r1.id(), mover("12:00", miguel.getId(), 0, null)), sesion(cliente)),
                conCsrf(peticion(r2.id(), mover("13:00", barbero.getId(), 0, null)), sesion(otro)));
        assertThat(respuestas).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(200));
        assertThat(jdbc.queryForObject("select barbero_id from reserva where id=?", Long.class, r1.id())).isEqualTo(miguel.getId());
        assertThat(jdbc.queryForObject("select barbero_id from reserva where id=?", Long.class, r2.id())).isEqualTo(barbero.getId());
        assertThat(jdbc.queryForList("select version from reserva", Integer.class)).containsExactly(1, 1);
        assertThat(cantidad("auditoria_reserva")).isEqualTo(4);
        avisos(barbero.getUsuario().getId(), miguel.getUsuario().getId(),
                barbero.getUsuario().getId(), miguel.getUsuario().getId());
    }

    @RepeatedTest(20) void reprogramacionFrenteCreacion_mismaFranjaExactamenteUnExito() throws Exception {
        var dto = crear();
        var otro = datos.cliente("otro");
        var respuestas = simultaneas(
                conCsrf(peticion(dto.id(), mover("11:00", null, 0, null)), sesion(cliente)),
                conCsrf(peticion(cmd(barbero, "11:00")), sesion(otro)));
        assertThat(respuestas.stream().filter(r -> r.getStatus() == 200 || r.getStatus() == 201).count()).isOne();
        assertThat(respuestas.stream().filter(r -> r.getStatus() == 409).count()).isOne();
        var rechazo = respuestas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
        assertThat(json.readTree(rechazo.getContentAsString()).get("codigo").asString()).isEqualTo("FRANJA_NO_DISPONIBLE");
        assertThat(jdbc.queryForObject("select count(*) from reserva where inicio=?", Integer.class,
                java.sql.Timestamp.from(instante("11:00")))).isOne();
        assertThat(cantidad("auditoria_reserva")).isEqualTo(2);
    }

    @RepeatedTest(20) void reprogramacionFrenteCancelacion_unaGanaOtra409SinEstadoMixto() throws Exception {
        var dto = crear();
        var cancelar = post("/api/reservas/{id}/cancelacion", dto.id()).contentType("application/json")
                .content(json.writeValueAsString(new CancelarReservaDto(0, null)));
        var respuestas = simultaneas(conCsrf(peticion(dto.id(), mover("11:00", null, 0, null)), sesion(cliente)),
                conCsrf(cancelar, sesion(cliente)));
        assertThat(respuestas.stream().map(MockHttpServletResponse::getStatus)).containsExactlyInAnyOrder(200, 409);
        var rechazo = respuestas.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
        assertThat(json.readTree(rechazo.getContentAsString()).get("codigo").asString()).isEqualTo("VERSION_DESACTUALIZADA");
        var fila = jdbc.queryForMap("select * from reserva where id=?", dto.id());
        boolean cancelada = fila.get("estado").equals("CANCELADA");
        assertThat(((java.sql.Timestamp) fila.get("inicio")).toInstant()).isEqualTo(instante(cancelada ? "10:00" : "11:00"));
        assertThat(((java.sql.Timestamp) fila.get("fin")).toInstant()).isEqualTo(instante(cancelada ? "10:30" : "11:30"));
        assertThat(fila.get("version")).isEqualTo(1);
        assertThat(cantidad("auditoria_reserva")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select accion from auditoria_reserva where accion<>'CREAR'", String.class))
                .isEqualTo(cancelada ? "CANCELAR" : "REPROGRAMAR");
    }

    private List<MockHttpServletResponse> simultaneas(MockHttpServletRequestBuilder... peticiones) throws Exception {
        CountDownLatch preparados = new CountDownLatch(peticiones.length), salida = new CountDownLatch(1);
        var ejecutor = Executors.newFixedThreadPool(peticiones.length);
        try {
            var futuros = new ArrayList<Future<MockHttpServletResponse>>();
            for (var peticion : peticiones) futuros.add(ejecutor.submit(() -> {
                preparados.countDown();
                esperar(salida);
                return mvc.perform(peticion).andReturn().getResponse();
            }));
            assertThat(preparados.await(3, TimeUnit.SECONDS)).isTrue();
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            salida.countDown();
            var respuestas = new ArrayList<MockHttpServletResponse>();
            for (var futuro : futuros) respuestas.add(futuro.get(Math.max(1, limite - System.nanoTime()), TimeUnit.NANOSECONDS));
            assertThat(System.nanoTime()).isLessThan(limite);
            return respuestas;
        } finally {
            salida.countDown();
            ejecutor.shutdownNow();
            assertThat(ejecutor.awaitTermination(6, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"version", "estado", "reloj", "activo"})
    void despuesDeEsperaReal_revalidaSinCacheObsoleta(String cambio) throws Exception {
        var dto = crear();
        adelantarHasta("08:00");
        CountDownLatch tomado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var propietario = ejecutor.submit(() -> new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
                if (cambio.equals("activo")) {
                    barberos.bloquearPorIds(List.of(barbero.getId()));
                    jdbc.update("update barbero set activo=false where id=?", barbero.getId());
                } else {
                    reservas.bloquearPorId(dto.id()).orElseThrow();
                    if (cambio.equals("version")) jdbc.update("update reserva set version=1 where id=?", dto.id());
                    if (cambio.equals("estado")) jdbc.update("update reserva set estado='CANCELADA' where id=?", dto.id());
                }
                tomado.countDown();
                esperar(liberar);
            }));
            try {
                assertThat(tomado.await(3, TimeUnit.SECONDS)).isTrue();
                AtomicInteger pid = new AtomicInteger();
                var resultado = ejecutor.submit(() -> new TransactionTemplate(transacciones).execute(tx -> {
                    pid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    try {
                        servicioReservas.reprogramar(dto.id(), mover("11:00", null, 0, null), actor(cliente));
                        return "OK";
                    } catch (NegocioException e) { tx.setRollbackOnly(); return e.codigo().name(); }
                }));
                observarEspera(pid);
                if (cambio.equals("reloj")) reloj.adelantar(Duration.ofMinutes(1));
                liberar.countDown();
                propietario.get(5, TimeUnit.SECONDS);
                assertThat(resultado.get(5, TimeUnit.SECONDS)).isEqualTo(switch (cambio) {
                    case "version" -> "VERSION_DESACTUALIZADA";
                    case "estado" -> "TRANSICION_INVALIDA";
                    case "activo" -> "RECURSO_INACTIVO";
                    default -> "FUERA_DE_POLITICA";
                });
            } finally { liberar.countDown(); }
        }
        assertThat(cantidad("auditoria_reserva")).isOne();
        assertThat(cantidad("notificacion")).isEqualTo(2);
        assertThat(((java.sql.Timestamp) jdbc.queryForMap("select * from reserva where id=?", dto.id())
                .get("inicio")).toInstant()).isEqualTo(instante("10:00"));
    }

    private void observarEspera(AtomicInteger pid) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            if (pid.get() != 0 && Boolean.TRUE.equals(jdbc.queryForObject(
                    "select coalesce((select wait_event_type='Lock' from pg_stat_activity where pid=?),false)",
                    Boolean.class, pid.get()))) return;
            Thread.sleep(10);
        }
        throw new AssertionError("PostgreSQL no registró la espera real por el bloqueo.");
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
