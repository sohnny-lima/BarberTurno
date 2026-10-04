package pe.barberturno.reservations;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = {"barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=", "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ReservaAsistidaIT extends ReservaPruebaBase {
    Usuario admin;
    @BeforeEach void administrador() { admin = usuarios.saveAndFlush(new Usuario("ADMIN ficticio", "admin31@ejemplo.test", null, "hash-ficticio", Rol.ADMIN, null, reloj.instant())); }
    CrearReservaDto asistida(Usuario u, String hora) { return new CrearReservaDto(servicio.getId(), barbero.getId(), TiempoNegocio.aLima(instante(hora)), u.getId()); }
    @Test void cp19_ocupada409_libre201_conActorAdminYAvisos() throws Exception {
        crearHttp(datos.cliente("otro"), cmd(barbero, "10:00"));
        mvc.perform(conCsrf(peticion(asistida(cliente, "10:00")), sesion(admin))).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FRANJA_NO_DISPONIBLE"));
        var creada = crearHttp(admin, asistida(cliente, "11:00"));
        assertThat(creada.estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(creada.cliente().id()).isEqualTo(cliente.getId());
        assertThat(jdbc.queryForObject("select creada_por from reserva where id=?", Long.class, creada.id())).isEqualTo(admin.getId());
        assertThat(jdbc.queryForObject("select actor_id from auditoria_reserva where reserva_id=?", Long.class, creada.id())).isEqualTo(admin.getId());
        assertThat(jdbc.queryForList("select usuario_id from notificacion where reserva_id=?", Long.class, creada.id())).containsExactlyInAnyOrder(cliente.getId(), barbero.getUsuario().getId());
    }
    @Test void cuartaFutura_adminNoTieneLimiteRn20() throws Exception {
        for (String hora : List.of("10:00", "11:00", "12:00")) crearHttp(cliente, cmd(barbero, hora));
        crearHttp(admin, asistida(cliente, "13:00")); assertThat(cantidad("reserva")).isEqualTo(4);
    }
    @Test void clienteConClienteId403_adminSinClienteId400() throws Exception {
        mvc.perform(conCsrf(peticion(asistida(cliente, "10:00")), sesion(cliente))).andExpect(status().isForbidden());
        mvc.perform(conCsrf(peticion(cmd(barbero, "10:00")), sesion(admin))).andExpect(status().isBadRequest());
    }
    @Test void clienteInexistente404_rolAjeno404_inactivo422() throws Exception {
        mvc.perform(conCsrf(peticion(new CrearReservaDto(servicio.getId(), barbero.getId(), TiempoNegocio.aLima(instante("10:00")), 999999L)), sesion(admin))).andExpect(status().isNotFound());
        mvc.perform(conCsrf(peticion(asistida(admin, "10:00")), sesion(admin))).andExpect(status().isNotFound());
        cliente.desactivar(reloj.instant()); usuarios.saveAndFlush(cliente);
        mvc.perform(conCsrf(peticion(asistida(cliente, "10:00")), sesion(admin))).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("RECURSO_INACTIVO"));
    }
    @Test void solapeClienteSeMantiene409() throws Exception {
        crearHttp(cliente, cmd(perfil("dos"), "10:00"));
        mvc.perform(conCsrf(peticion(asistida(cliente, "10:00")), sesion(admin))).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CLIENTE_CON_RESERVA_SOLAPADA"));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true}) void adminConSesion_csrfAusenteOIncorrecto403(boolean incorrecto) throws Exception {
        var peticion = peticion(asistida(cliente, "10:00")).cookie(sesion(admin));
        if (incorrecto) peticion.cookie(new Cookie("XSRF-TOKEN", "valido")).header("X-XSRF-TOKEN", "incorrecto");
        mvc.perform(peticion).andExpect(status().isForbidden()); assertThat(cantidad("reserva")).isZero();
    }
    @Test void barberoNoPuedeReservar403() throws Exception { mvc.perform(conCsrf(peticion(asistida(cliente, "10:00")), sesion(barbero.getUsuario()))).andExpect(status().isForbidden()); }
}
