package pe.barberturno.reservations;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.audit.*;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.catalog.dto.GuardarServicioDto;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.notifications.*;
import pe.barberturno.reporting.dto.ResumenReporteDto;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reproduce las cinco hojas de la corrida, con sesiones obtenidas por login y PostgreSQL real. */
@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
class CorridaManualIT extends ReservaPruebaBase {
    private static final String PASSWORD = "ClaveCorrida32";
    @Autowired PasswordEncoder encoder;
    @Autowired BloqueoRepository bloqueos;
    @Autowired AuditoriaRepository auditorias;
    @Autowired NotificacionRepository avisos;
    @Autowired PlatformTransactionManager transacciones;
    Usuario luis;
    Usuario admin;
    Servicio barba;
    Cookie anaSesion;
    Cookie carlosSesion;
    Cookie adminSesion;

    // Sustituye exclusivamente la preparación de la base, conservando su limpieza y contexto.
    @Override @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
        SqlPruebas.limpiar();
        String hash = encoder.encode(PASSWORD);
        cliente = usuarios.saveAndFlush(new Usuario("Ana", "ana@ejemplo.test", "999000001",
                hash, Rol.CLIENTE, reloj.instant(), reloj.instant()));
        luis = usuarios.saveAndFlush(new Usuario("Luis", "luis@ejemplo.test", "999000002",
                hash, Rol.CLIENTE, reloj.instant(), reloj.instant()));
        Usuario carlos = usuarios.saveAndFlush(new Usuario("Carlos", "carlos@ejemplo.test", null,
                hash, Rol.BARBERO, null, reloj.instant()));
        admin = usuarios.saveAndFlush(new Usuario("Administrador", "admin@ejemplo.test", null,
                hash, Rol.ADMIN, null, reloj.instant()));
        barbero = barberos.saveAndFlush(new Barbero(carlos, "Corte y barba", reloj.instant()));
        servicio = servicios.saveAndFlush(new Servicio("Corte", "Corte", (short) 30,
                new BigDecimal("25.00"), reloj.instant()));
        barba = servicios.saveAndFlush(new Servicio("Barba", "Barba", (short) 20,
                new BigDecimal("20.00"), reloj.instant()));
        new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
            barberos.bloquearPorIds(List.of(barbero.getId()));
            jornadas.save(new Jornada(barbero, (short) 4, LocalTime.of(9, 0), LocalTime.of(13, 0)));
            jornadas.save(new Jornada(barbero, (short) 4, LocalTime.of(14, 0), LocalTime.of(18, 0)));
            bloqueos.save(new Bloqueo(barbero, instante("16:00"), instante("17:00"),
                    "Trámite", admin, reloj.instant()));
        });
    }

    private Cookie login(Usuario usuario) throws Exception {
        return mvc.perform(conCsrf(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("correo", usuario.getCorreo(), "password", PASSWORD))),
                new Cookie("BT_SESION", ""))).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("BT_SESION");
    }

    private Reserva inicial(Usuario propietario, Servicio contratado, String hora, EstadoReserva estado) {
        return new TransactionTemplate(transacciones).execute(tx -> {
            usuarios.bloquearPorId(propietario.getId()).orElseThrow();
            barberos.bloquearPorIds(List.of(barbero.getId()));
            Reserva r = reservas.saveAndFlush(new Reserva(propietario, barbero, contratado,
                    instante(hora), estado, propietario, reloj.instant()));
            // A1/A2 y N1/N2: importación de la hoja Inicial y sus filas de Trazabilidad.
            auditorias.save(new AuditoriaReserva(r, propietario, AccionAuditoria.CREAR, null, estado,
                    null, Map.of("inicio", TiempoNegocio.aLima(r.getInicio()).toString(),
                            "fin", TiempoNegocio.aLima(r.getFin()).toString(),
                            "barberoId", barbero.getId(), "servicioId", contratado.getId(), "estado", estado.name()),
                    null, false, reloj.instant()));
            for (Usuario destinatario : List.of(propietario, barbero.getUsuario())) {
                avisos.save(new Notificacion(destinatario, r, AccionAuditoria.CREAR,
                        "Reserva inicial creada para la corrida manual.", reloj.instant()));
            }
            return r;
        });
    }

    private ResultActions cambiar(long id, String accion, Object comando, Cookie sesion) throws Exception {
        return mvc.perform(conCsrf(post("/api/reservas/{id}/{accion}", id, accion)
                .contentType("application/json").content(json.writeValueAsString(comando)), sesion));
    }

    private ReservaDto leer(ResultActions resultado) throws Exception {
        return json.rebuild().disable(tools.jackson.databind.cfg.DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build().readValue(resultado.andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString(), ReservaDto.class);
    }

    private ReservaDto detalle(long id, Cookie sesion) throws Exception {
        return leer(mvc.perform(get("/api/reservas/{id}", id).cookie(sesion)));
    }

    private void contar(int reservasEsperadas, int auditoriasEsperadas, int avisosEsperados) {
        assertThat(cantidad("reserva")).isEqualTo(reservasEsperadas);
        assertThat(cantidad("auditoria_reserva")).isEqualTo(auditoriasEsperadas);
        assertThat(cantidad("notificacion")).isEqualTo(avisosEsperados);
    }

    @Test void ochoPasos_concilianReservasAuditoriasAvisosReporteEHistorial() throws Exception {
        Reserva r101 = inicial(cliente, servicio, "10:00", EstadoReserva.CONFIRMADA);
        reloj.adelantar(Duration.ofMinutes(1));
        Reserva r102 = inicial(luis, barba, "10:30", EstadoReserva.PENDIENTE);
        anaSesion = login(cliente);
        carlosSesion = login(barbero.getUsuario());

        // 1. La franja cruza las dos reservas iniciales: ningún efecto persistido.
        mvc.perform(conCsrf(peticion(cmd(barbero, "10:10")), anaSesion))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FRANJA_NO_DISPONIBLE"));
        contar(2, 2, 4);

        // 2. R103: creación real por API a las 09:02 del reloj del escenario.
        reloj.adelantar(Duration.ofMinutes(1));
        var r103 = json.readValue(mvc.perform(conCsrf(peticion(cmd(barbero, "10:50")), anaSesion))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), ReservaDto.class);
        assertThat(r103.fin().toInstant()).isEqualTo(instante("11:20"));
        contar(3, 3, 6);

        // 3. K1 impide mover: también se conservan versión, horario y trazabilidad.
        cambiar(r103.id(), "reprogramacion", new ReprogramarReservaDto(
                TiempoNegocio.aLima(instante("16:00")), null, r103.version(), null), anaSesion)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FRANJA_NO_DISPONIBLE"));
        assertThat(detalle(r103.id(), anaSesion).inicio().toInstant()).isEqualTo(instante("10:50"));
        assertThat(detalle(r103.id(), anaSesion).version()).isEqualTo(r103.version());
        contar(3, 3, 6);

        // 4. Cancelar R101 conserva la fila y libera 10:00.
        reloj.adelantar(Duration.ofMinutes(1));
        var cancelada = leer(cambiar(r101.getId(), "cancelacion",
                new CancelarReservaDto(r101.getVersion(), null), anaSesion));
        assertThat(cancelada.estado()).isEqualTo(EstadoReserva.CANCELADA);
        contar(3, 4, 8);

        // 5. Reprogramar R103 al hueco liberado conserva las referencias contratadas.
        reloj.adelantar(Duration.ofMinutes(1));
        r103 = leer(cambiar(r103.id(), "reprogramacion", new ReprogramarReservaDto(
                TiempoNegocio.aLima(instante("10:00")), null, r103.version(), null), anaSesion));
        assertThat(r103.inicio().toInstant()).isEqualTo(instante("10:00"));
        assertThat(r103.fin().toInstant()).isEqualTo(instante("10:30"));
        assertThat(r103.precioRef()).isEqualByComparingTo("25.00");
        assertThat(r103.duracionMin()).isEqualTo((short) 30);
        contar(3, 5, 10);

        // 6. Confirmar a las 09:05, iniciar el día 1 a las 10:30 y completar a las 10:50.
        reloj.adelantar(Duration.ofMinutes(1));
        var atendida = leer(cambiar(r102.getId(), "transiciones",
                new TransicionarReservaDto(EstadoReserva.CONFIRMADA, r102.getVersion()), carlosSesion));
        reloj.adelantar(Duration.between(reloj.instant(), instante("10:30")));
        carlosSesion = login(barbero.getUsuario()); // La sesión del día 28 ya caducó (ocho horas).
        atendida = leer(cambiar(atendida.id(), "transiciones",
                new TransicionarReservaDto(EstadoReserva.EN_ATENCION, atendida.version()), carlosSesion));
        reloj.adelantar(Duration.ofMinutes(20));
        atendida = leer(cambiar(atendida.id(), "transiciones",
                new TransicionarReservaDto(EstadoReserva.COMPLETADA, atendida.version()), carlosSesion));
        assertThat(atendida.estado()).isEqualTo(EstadoReserva.COMPLETADA);
        contar(3, 8, 13);

        // 7. S99 se representa con una identidad garantizada inexistente.
        anaSesion = login(cliente);
        var inexistente = new CrearReservaDto(Long.MAX_VALUE, barbero.getId(),
                TiempoNegocio.aLima(instante("12:00")), null);
        mvc.perform(conCsrf(peticion(inexistente), anaSesion)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        contar(3, 8, 13);

        // 8. Edición del catálogo por el administrador, sin alterar precio_ref.
        adminSesion = login(admin);
        mvc.perform(conCsrf(put("/api/servicios/{id}", servicio.getId()).contentType("application/json")
                .content(json.writeValueAsString(new GuardarServicioDto("Corte", "Corte", 30,
                        new BigDecimal("30.00")))), adminSesion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precio").value(30));
        contar(3, 8, 13);
        assertThat(detalle(r101.getId(), anaSesion).precioRef()).isEqualByComparingTo("25.00");
        assertThat(detalle(r103.id(), anaSesion).precioRef()).isEqualByComparingTo("25.00");
        assertThat(detalle(r103.id(), anaSesion).estado()).isEqualTo(EstadoReserva.CONFIRMADA);

        conciliarAuditorias(r101.getId(), r102.getId(), r103.id());
        conciliarAvisos(cliente, anaSesion, 4);
        conciliarAvisos(luis, login(luis), 4);
        conciliarAvisos(barbero.getUsuario(), carlosSesion, 5); // Tres creaciones, cancelación y reprogramación.
        conciliarAvisos(admin, adminSesion, 0);
        assertThat(jdbc.queryForList("select reserva_id from notificacion where usuario_id=? order by id",
                Long.class, cliente.getId())).containsExactly(r101.getId(), r103.id(), r101.getId(), r103.id());
        assertThat(jdbc.queryForList("select tipo from notificacion where usuario_id=? order by id",
                String.class, cliente.getId())).containsExactly("CREAR", "CREAR", "CANCELAR", "REPROGRAMAR");
        assertThat(jdbc.queryForList("select reserva_id from notificacion where usuario_id=? order by id",
                Long.class, luis.getId())).containsOnly(r102.getId()).hasSize(4);
        assertThat(jdbc.queryForList("select tipo from notificacion where usuario_id=? order by id",
                String.class, luis.getId())).containsExactly("CREAR", "CONFIRMAR", "INICIAR", "COMPLETAR");
        conciliarReportes();
        mvc.perform(get("/api/reservas/mias").cookie(anaSesion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(get("/api/reservas/mias").cookie(login(luis)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        System.out.printf("Corrida T-32: R101=%d, R102=%d, R103=%d; 8 auditorías; avisos Ana=4 Luis=4 Carlos=5.%n",
                r101.getId(), r102.getId(), r103.id());
    }

    private void conciliarAuditorias(long r101, long r102, long r103) throws Exception {
        var esperado = List.of(
                Map.entry(r101, List.of("CREAR", "CANCELAR")),
                Map.entry(r102, List.of("CREAR", "CONFIRMAR", "INICIAR", "COMPLETAR")),
                Map.entry(r103, List.of("CREAR", "REPROGRAMAR")));
        for (var entrada : esperado) {
            var respuesta = mvc.perform(get("/api/reservas/{id}/auditoria", entrada.getKey())
                    .cookie(carlosSesion)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var acciones = new java.util.ArrayList<String>();
            json.readTree(respuesta).forEach(n -> acciones.add(n.get("accion").asString()));
            assertThat(acciones).containsExactlyElementsOf(entrada.getValue());
        }
        assertThat(jdbc.queryForObject("select count(*) from auditoria_reserva where reserva_id not in (?, ?, ?)",
                Integer.class, r101, r102, r103)).isZero();
        assertThat(jdbc.queryForList("select actor_id from auditoria_reserva order by id", Long.class))
                .containsExactly(cliente.getId(), luis.getId(), cliente.getId(), cliente.getId(),
                        cliente.getId(), barbero.getUsuario().getId(), barbero.getUsuario().getId(),
                        barbero.getUsuario().getId());
    }

    private void conciliarAvisos(Usuario usuario, Cookie sesion, int esperados) throws Exception {
        mvc.perform(get("/api/notificaciones").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(esperados))
                .andExpect(jsonPath("$.contenido.length()").value(esperados));
        mvc.perform(get("/api/notificaciones/conteo").cookie(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(esperados));
        assertThat(jdbc.queryForObject("select count(*) from notificacion where usuario_id = ?",
                Integer.class, usuario.getId())).isEqualTo(esperados);
    }

    private void conciliarReportes() throws Exception {
        var resultado = mvc.perform(get("/api/reportes/resumen").cookie(adminSesion)
                .param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var resumen = json.readValue(resultado, ResumenReporteDto.class);
        assertThat(resumen.total()).isEqualTo(3);
        for (EstadoReserva estado : EstadoReserva.values()) {
            long esperado = List.of(EstadoReserva.CANCELADA, EstadoReserva.COMPLETADA,
                    EstadoReserva.CONFIRMADA).contains(estado) ? 1 : 0;
            assertThat(resumen.porEstado()).containsEntry(estado, esperado);
            historial(estado.name(), null, esperado);
        }
        assertThat(resumen.porServicio()).hasSize(2);
        for (var fila : resumen.porServicio()) {
            assertThat(fila.total()).isEqualTo(fila.id() == servicio.getId() ? 2 : 1);
            historial(null, fila.id(), fila.total());
        }
        assertThat(resumen.porBarbero()).singleElement().satisfies(fila -> {
            assertThat(fila.id()).isEqualTo(barbero.getId());
            assertThat(fila.total()).isEqualTo(3);
        });
        historial(null, null, 3);
    }

    private void historial(String estado, Long servicioId, long total) throws Exception {
        MockHttpServletRequestBuilder peticion = get("/api/reservas").cookie(adminSesion)
                .param("desde", "2026-10-01").param("hasta", "2026-10-01");
        if (estado != null) peticion.param("estado", estado);
        if (servicioId != null) peticion.param("servicioId", servicioId.toString());
        mvc.perform(peticion).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(total));
    }
}
