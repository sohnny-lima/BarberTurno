package pe.barberturno.reservations;

import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import pe.barberturno.auth.JwtService;
import pe.barberturno.catalog.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class ReservaPruebaBase {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository usuarios;
    @Autowired BarberoRepository barberos;
    @Autowired ServicioRepository servicios;
    @Autowired JornadaRepository jornadas;
    @Autowired ReservaRepository reservas;
    @Autowired ReservaService servicioReservas;
    @Autowired RelojAjustable reloj;
    DatosPrueba datos;
    Usuario cliente;
    Barbero barbero;
    Servicio servicio;

    @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        reloj.reiniciar();
        SqlPruebas.limpiar();
        datos = new DatosPrueba(usuarios, barberos, servicios);
        cliente = datos.cliente("cliente");
        barbero = perfil("uno");
        servicio = datos.servicio();
    }

    @AfterEach void limpiar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        SqlPruebas.limpiar();
    }

    Barbero perfil(String nombre) {
        var perfil = datos.barbero(nombre);
        for (short dia = 1; dia <= 7; dia++) {
            jornadas.saveAndFlush(new Jornada(perfil, dia, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        }
        return perfil;
    }

    UsuarioAutenticado actor(Usuario usuario) {
        return new UsuarioAutenticado(usuario.getId(), usuario.getRol(), Optional.empty(), false);
    }

    Instant instante(String hora) {
        return LocalDate.of(2026, 10, 1).atTime(LocalTime.parse(hora)).atZone(TiempoNegocio.ZONA).toInstant();
    }

    CrearReservaDto cmd(Barbero perfil, String hora) {
        return new CrearReservaDto(servicio.getId(), perfil.getId(), TiempoNegocio.aLima(instante(hora)), null);
    }

    MockHttpServletRequestBuilder peticion(CrearReservaDto cmd) {
        return post("/api/reservas").contentType("application/json").content(json.writeValueAsString(cmd));
    }

    Cookie sesion(Usuario usuario) { return new Cookie("BT_SESION", jwt.emitir(usuario)); }

    MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder peticion, Cookie sesion) throws Exception {
        Cookie csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return peticion.cookie(sesion, csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }

    ReservaDto crearHttp(Usuario usuario, CrearReservaDto cmd) throws Exception {
        return json.rebuild().disable(tools.jackson.databind.cfg.DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build().readValue(mvc.perform(conCsrf(peticion(cmd), sesion(usuario)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), ReservaDto.class);
    }

    int cantidad(String tabla) { return jdbc.queryForObject("select count(*) from " + tabla, Integer.class); }
}
