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
}
