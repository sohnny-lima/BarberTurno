package pe.barberturno.reservations;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import pe.barberturno.support.RelojAjustable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regresión T-46 con JWT real, CSRF y los fixtures compartidos de reservas. */
@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErroresMvcIT extends ReservaPruebaBase {

    @Test
    void reservar_conSesionClienteCsrfYTexto_devuelve415ValidacionSinEscrituras() throws Exception {
        mvc.perform(conCsrf(post("/api/reservas").contentType(MediaType.TEXT_PLAIN)
                .content("Contenido ficticio"), sesion(cliente)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.detail").value("El tipo de contenido de la solicitud no está admitido."))
                .andExpect(jsonPath("$.instance").value("/api/reservas"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores").isEmpty());
        assertThat(cantidad("reserva")).isZero();
        assertThat(cantidad("auditoria_reserva")).isZero();
        assertThat(cantidad("notificacion")).isZero();
    }

    @Test
    void health_conAcceptHtml_devuelve406SinCuerpoNiTipo() throws Exception {
        mvc.perform(get("/actuator/health").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Content-Type"));
    }

    @Test
    void metodoNoPermitido_conSesionYCsrf_seguridadRechazaAntesDeMvc() throws Exception {
        mvc.perform(conCsrf(put("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), sesion(cliente)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(cantidad("reserva")).isZero();
    }
}
