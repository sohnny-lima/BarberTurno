package pe.barberturno.reservations;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import pe.barberturno.support.RelojAjustable;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false", "barberturno.reservas.confirmacion-manual=true"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ReservaConfirmacionManualIT extends ReservaPruebaBase {
    @Test void confirmacionManual_pendienteOcupaFranjaYPuedeCancelarSinReprogramar() throws Exception {
        var dto = crearHttp(cliente, cmd(barbero, "10:00"));
        assertThat(dto.estado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(dto.permisos().cancelar()).isTrue();
        assertThat(dto.permisos().reprogramar()).isFalse();
        assertThat(dto.permisos().transiciones()).isEmpty();
        assertThat(jdbc.queryForObject("select estado_nuevo from auditoria_reserva", String.class)).isEqualTo("PENDIENTE");
        assertThatThrownBy(() -> servicioReservas.crear(cmd(barbero, "10:00"), actor(datos.cliente("otro"))))
                .isInstanceOfSatisfying(pe.barberturno.common.error.NegocioException.class,
                        e -> assertThat(e.codigo()).isEqualTo(pe.barberturno.common.error.ErrorCodigo.FRANJA_NO_DISPONIBLE));
    }

    @Test void cancelarCreadaConConfirmacionManual_200PendienteACancelada() throws Exception {
        var dto = crearHttp(cliente, cmd(barbero, "10:00"));
        assertThat(dto.estado()).isEqualTo(pe.barberturno.reservations.EstadoReserva.PENDIENTE);
        var resultado = mvc.perform(conCsrf(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/reservas/{id}/cancelacion", dto.id())
                        .contentType("application/json").content("{\"version\":0}"), sesion(cliente)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.estado").value("CANCELADA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.permisos.cancelar").value(false));
        assertThat(jdbc.queryForObject("select estado_anterior from auditoria_reserva where accion='CANCELAR'",
                String.class)).isEqualTo("PENDIENTE");
        assertThat(cantidad("reserva")).isOne();
        assertThat(cantidad("notificacion")).isEqualTo(4);
    }

    @Test void confirmarCreadaPorApiManual_200AuditoriaYAvisoCliente() throws Exception {
        var pendiente = crearHttp(cliente, cmd(barbero, "10:00"));
        mvc.perform(conCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/reservas/{id}/transiciones", pendiente.id()).contentType("application/json")
                .content("{\"estado\":\"CONFIRMADA\",\"version\":0}"), sesion(barbero.getUsuario())))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("select estado_anterior from auditoria_reserva where accion='CONFIRMAR'", String.class))
                .isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForList("select usuario_id from notificacion where tipo='CONFIRMAR'", Long.class))
                .containsExactly(cliente.getId());
        assertThat(cantidad("auditoria_reserva")).isEqualTo(2);
        assertThat(cantidad("notificacion")).isEqualTo(3);
    }
}
