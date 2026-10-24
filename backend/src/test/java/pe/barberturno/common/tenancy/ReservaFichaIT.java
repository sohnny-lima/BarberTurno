package pe.barberturno.common.tenancy;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.*;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservaFichaIT extends IdentidadPruebaBase {
    @Autowired ReservaService reservas;
    @Autowired JornadaRepository jornadas;

    @Test void asistida_traduceCuentaAFichaDistinta_yDtoFiltrosAvisosYDisponibilidadUsanLaCuenta() throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        pertenencia(b, actor, pe.barberturno.platform.RolNegocio.PROPIETARIO);
        var cuenta = persona("compartida", Rol.CLIENTE);
        ficha(1L, cuenta);
        var datosB = enNegocio(b, () -> DatosNegocioPrueba.crear(jdbc, "b"));
        var local = ficha(b, cuenta);
        assertThat(local.getId()).isNotEqualTo(cuenta.getId());
        local.actualizarDatos("Nombre local B", "51999000007", reloj.instant());
        enNegocio(b, () -> clientes.saveAndFlush(local));
        proveedor.fijar(b);
        try {
            mvc.perform(csrf(post("/api/reservas").contentType("application/json").content("""
                    {"servicioId":%d,"barberoId":%d,"inicio":"2026-10-01T11:00:00-05:00","clienteId":%d}
                    """.formatted(datosB.servicio(), datosB.barbero(), cuenta.getId())), sesion(actor)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.cliente.id").value(cuenta.getId()))
                    .andExpect(jsonPath("$.cliente.nombre").value("Nombre local B"))
                    .andExpect(jsonPath("$.cliente.telefono").value("51999000007"));
            long id = sql.queryForObject("select id from reserva where cliente_id=?", Long.class, local.getId());
            assertThat(sql.queryForObject("select creada_por from reserva where id=?", Long.class, id)).isEqualTo(actor.getId());
            assertThat(sql.queryForObject("select actor_id from auditoria_reserva where reserva_id=?", Long.class, id)).isEqualTo(actor.getId());
            assertThat(sql.queryForList("select usuario_id from notificacion where reserva_id=?", Long.class, id))
                    .containsExactlyInAnyOrder(cuenta.getId(), datosB.admin());
            mvc.perform(get("/api/reservas").param("desde", "2026-10-01").param("hasta", "2026-10-01")
                    .param("clienteId", cuenta.getId().toString()).cookie(sesion(actor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(id));
            mvc.perform(get("/api/reservas/mias").cookie(sesion(cuenta))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].permisos.cancelar").value(true))
                    .andExpect(jsonPath("$.contenido[0].cliente.telefono").doesNotExist());
            mvc.perform(get("/api/disponibilidad").cookie(sesion(cuenta)).param("servicioId", Long.toString(datosB.servicio()))
                    .param("fecha", "2026-10-01").param("excluirReservaId", Long.toString(id))).andExpect(status().isOk());
        } finally { proveedor.reiniciar(); }
    }

    @Test void idsAjenos_deFichaReservaYCuenta_seRechazanComoAplicacion() throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var a = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "a"));
        var datosB = enNegocio(b, () -> DatosNegocioPrueba.crear(jdbc, "b"));
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(enNegocio(1L, () -> jdbc.queryForObject("select count(*) from reserva where id=?", Integer.class, datosB.reserva()))).isZero();
        assertThatThrownBy(() -> enNegocio(1L, () -> DatosNegocioPrueba.reservar(jdbc, datosB.ficha(), actor.getId(),
                a.barbero(), a.servicio(), "CANCELADA", 60)))
                .isInstanceOfSatisfying(org.springframework.dao.DataAccessException.class, e -> {
                    var causa = (org.postgresql.util.PSQLException) e.getMostSpecificCause();
                    assertThat(causa.getSQLState()).isEqualTo("23503");
                    assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo("reserva_cliente_fk");
                });
        mvc.perform(csrf(post("/api/reservas").contentType("application/json").content("""
                {"servicioId":%d,"barberoId":%d,"inicio":"2026-10-01T11:00:00-05:00","clienteId":%d}
                """.formatted(a.servicio(), a.barbero(), datosB.cliente())), sesion(actor)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(get("/api/reservas/" + datosB.reserva()).cookie(sesion(actor)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(csrf(post("/api/reservas/" + datosB.reserva() + "/reprogramacion").contentType("application/json")
                .content("{\"inicio\":\"2026-10-01T11:00:00-05:00\",\"version\":0,\"motivo\":\"Cambio ajeno\"}"), sesion(actor)))
                .andExpect(status().isNotFound());
        var local = clientes.findByUsuarioId(a.cliente()).orElseThrow();
        local.desactivar(reloj.instant()); clientes.saveAndFlush(local);
        assertThatThrownBy(() -> reservas.crear(cmd(a, "11:00", a.cliente()), auth.autenticar(actor.getId(), 0).orElseThrow()))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
    }

    @Test void rn04YRn20_porFicha_mismaCuentaReservaSimultaneaEnAYB_sinCambiarContrato() throws Exception {
        long b = negocioB();
        var datosA = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "a"));
        var datosB = enNegocio(b, () -> DatosNegocioPrueba.crear(jdbc, "b"));
        var cuenta = usuarios.findById(datosA.cliente()).orElseThrow();
        var fichaB = ficha(b, cuenta);
        assertThat(fichaB.getId()).isNotEqualTo(cuenta.getId());
        var actorA = auth.autenticar(cuenta.getId(), 0).orElseThrow();
        var citaA = reservas.crear(cmd(datosA, "11:00", null), actorA);
        reservas.crear(cmd(datosA, "12:00", null), actorA);
        assertThatThrownBy(() -> reservas.crear(cmd(datosA, "13:00", null), actorA))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.LIMITE_RESERVAS_ACTIVAS));
        var persona = persona("segundo-barbero-a", Rol.BARBERO);
        var segundo = barberos.saveAndFlush(new Barbero(persona, "Corte", reloj.instant()));
        jornadas.saveAndFlush(new Jornada(segundo, (short) 4, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        assertThatThrownBy(() -> reservas.crear(new CrearReservaDto(datosA.servicio(), segundo.getId(), citaA.inicio(), null), actorA))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.CLIENTE_CON_RESERVA_SOLAPADA));
        proveedor.fijar(b);
        try {
            var actorB = auth.autenticar(cuenta.getId(), 0).orElseThrow();
            var citaB = reservas.crear(cmd(datosB, "11:00", null), actorB);
            assertThat(citaB.cliente().id()).isEqualTo(cuenta.getId());
            assertThat(citaB.permisos().cancelar()).isTrue();
            reservas.crear(cmd(datosB, "13:00", null), actorB);
            mvc.perform(get("/api/reservas/mias").cookie(sesion(cuenta))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido.length()").value(2));
            mvc.perform(get("/api/reservas/" + citaA.id()).cookie(sesion(cuenta))).andExpect(status().isNotFound());
            var movida = reservas.reprogramar(citaB.id(), new ReprogramarReservaDto(
                    TiempoNegocio.aLima(Instant.parse("2026-10-01T19:00:00Z")), null, citaB.version(), null), actorB);
            assertThat(movida.cliente().id()).isEqualTo(cuenta.getId());
            assertThat(movida.permisos().cancelar()).isTrue();
        } finally { proveedor.reiniciar(); }
        assertThat(citaA.cliente().id()).isEqualTo(cuenta.getId());
    }

    static CrearReservaDto cmd(DatosNegocioPrueba datos, String hora, Long cuentaId) {
        return new CrearReservaDto(datos.servicio(), datos.barbero(),
                LocalDate.of(2026, 10, 1).atTime(LocalTime.parse(hora)).atOffset(ZoneOffset.ofHours(-5)), cuentaId);
    }
}
