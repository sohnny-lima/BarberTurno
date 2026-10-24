package pe.barberturno.common.tenancy;

import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.support.SqlPruebas;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccesoCruzadoIT extends PeticionNegocioPruebaBase {
    @Autowired EntityManagerFactory fabrica;
    @Autowired ApplicationContext aplicacion;

    Map<String, List<Map<String, Object>>> huellaB(DatosNegocioPrueba datos) {
        return en("b", () -> {
            var resultado = new LinkedHashMap<String, List<Map<String, Object>>>();
            for (String tabla : EsquemaV2IT.TABLAS) resultado.put(tabla, jdbc.queryForList("select * from " + tabla + " order by id"));
            resultado.put("cuentas", jdbc.queryForList("select * from usuario where id in (?,?) order by id", datos.cliente(), datos.admin()));
            resultado.put("membresia", jdbc.queryForList("select * from membresia where negocio_id=? order by id", b));
            return resultado;
        });
    }

    @Test void idsDeB_enLecturasYEscriturasDeA_404SinModificarB() throws Exception {
        var datosA = en("a", () -> DatosNegocioPrueba.crear(jdbc, "cruzado-a"));
        var datosB = en("b", () -> DatosNegocioPrueba.crear(jdbc, "cruzado-b"));
        var actor = personal("gestor-a", "a", RolNegocio.PROPIETARIO);
        var cookie = sesion(actor);
        long bloqueoB = en("b", () -> jdbc.queryForObject("select id from bloqueo", Long.class));
        long avisoB = en("b", () -> jdbc.queryForObject("select id from notificacion", Long.class));
        var anterior = huellaB(datosB);
        String base = "/api/n/a";
        for (var solicitud : List.of(
                get(base + "/barberos/" + datosB.barbero() + "/jornadas"),
                get(base + "/barberos/" + datosB.barbero() + "/bloqueos").param("desde", "2026-10-01").param("hasta", "2026-10-01"),
                get(base + "/reservas/" + datosB.reserva()),
                get(base + "/reservas/" + datosB.reserva() + "/auditoria"),
                get(base + "/clientes/" + datosB.ficha()),
                get(base + "/disponibilidad").param("servicioId", Long.toString(datosB.servicio())).param("fecha", "2026-10-01"),
                get(base + "/disponibilidad").param("servicioId", Long.toString(datosA.servicio())).param("barberoId", Long.toString(datosB.barbero())).param("fecha", "2026-10-01"),
                get(base + "/disponibilidad").param("servicioId", Long.toString(datosA.servicio())).param("fecha", "2026-10-01").param("excluirReservaId", Long.toString(datosB.reserva())))) {
            mvc.perform(solicitud.cookie(cookie)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
        String intervalo = "{\"inicio\":\"2026-10-01T16:00:00-05:00\",\"fin\":\"2026-10-01T16:30:00-05:00\",\"motivo\":\"Intento ficticio\"}";
        List<MockHttpServletRequestBuilder> escrituras = List.of(
                put(base + "/servicios/" + datosB.servicio()).content("{\"nombre\":\"Ajeno\",\"descripcion\":\"Ficticio\",\"duracionMin\":30,\"precio\":20}"),
                patch(base + "/servicios/" + datosB.servicio() + "/estado").content("{\"activo\":false}"),
                put(base + "/barberos/" + datosB.barbero()).content("{\"nombre\":\"Ajeno\",\"telefono\":\"999000001\",\"especialidad\":\"Corte\"}"),
                patch(base + "/barberos/" + datosB.barbero() + "/estado").content("{\"activo\":false}"),
                put(base + "/barberos/" + datosB.barbero() + "/jornadas").content("[{\"diaSemana\":4,\"horaInicio\":\"09:00\",\"horaFin\":\"18:00\"}]"),
                post(base + "/barberos/" + datosB.barbero() + "/bloqueos").content(intervalo),
                post(base + "/bloqueos/lote").content("{\"barberoIds\":[" + datosB.barbero() + "]," + intervalo.substring(1)),
                delete(base + "/bloqueos/" + bloqueoB),
                post(base + "/reservas/" + datosB.reserva() + "/cancelacion").content("{\"version\":0,\"motivo\":\"Intento ficticio\"}"),
                post(base + "/reservas/" + datosB.reserva() + "/reprogramacion").content("{\"version\":0,\"inicio\":\"2026-10-01T11:00:00-05:00\",\"motivo\":\"Intento ficticio\"}"),
                post(base + "/reservas/" + datosB.reserva() + "/transiciones").content("{\"version\":0,\"estado\":\"EN_ATENCION\"}"),
                patch(base + "/clientes/" + datosB.ficha()).content("{\"nombre\":\"Ajeno\",\"telefono\":\"999000001\"}"),
                post(base + "/notificaciones/" + avisoB + "/lectura"),
                post(base + "/usuarios/" + datosB.cliente() + "/restablecer-password"),
                patch(base + "/usuarios/" + datosB.cliente() + "/estado").content("{\"activo\":false}"));
        for (var solicitud : escrituras) {
            mvc.perform(csrf(solicitud.contentType("application/json"), cookie)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
        for (long[] ids : List.of(new long[]{datosB.servicio(), datosA.barbero(), datosA.cliente()},
                new long[]{datosA.servicio(), datosB.barbero(), datosA.cliente()},
                new long[]{datosA.servicio(), datosA.barbero(), datosB.cliente()})) {
            mvc.perform(csrf(post(base + "/reservas").contentType("application/json").content("""
                    {"servicioId":%d,"barberoId":%d,"clienteId":%d,"inicio":"2026-10-01T11:00:00-05:00"}
                    """.formatted(ids[0], ids[1], ids[2])), cookie)).andExpect(status().isNotFound());
        }
        mvc.perform(get(base + "/usuarios").cookie(cookie).param("q", "cliente-cruzado-b@ejemplo.test"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0));
        assertThat(huellaB(datosB)).isEqualTo(anterior);
    }

    @Test void relacionesCruzadas_rechazadasPorFkCompuestaConElRolReal() {
        var x = en("a", () -> DatosNegocioPrueba.crear(jdbc, "fk-a"));
        var y = en("b", () -> DatosNegocioPrueba.crear(jdbc, "fk-b"));
        var anterior = huellaB(y);
        for (String relacion : List.of("servicio", "barbero", "cliente")) {
            assertThatThrownBy(() -> en("a", () -> DatosNegocioPrueba.reservar(jdbc,
                    relacion.equals("cliente") ? y.ficha() : x.ficha(), x.admin(),
                    relacion.equals("barbero") ? y.barbero() : x.barbero(),
                    relacion.equals("servicio") ? y.servicio() : x.servicio(), "CANCELADA", 60)))
                    .isInstanceOf(DataAccessException.class).satisfies(error -> {
                        var causa = (org.postgresql.util.PSQLException)((DataAccessException)error).getMostSpecificCause();
                        assertThat(causa.getSQLState()).isEqualTo("23503");
                        assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo("reserva_" + relacion + "_fk");
                    });
        }
        assertThat(huellaB(y)).isEqualTo(anterior);
    }

    @Test void sinCachesDeAplicacionSegundoNivelOConsultas_ySlugSeConsultaCadaVez() {
        assertThat(aplicacion.getBeansOfType(CacheManager.class)).isEmpty();
        var opciones = fabrica.unwrap(SessionFactoryImplementor.class).getSessionFactoryOptions();
        assertThat(opciones.isSecondLevelCacheEnabled()).isFalse();
        assertThat(opciones.isQueryCacheEnabled()).isFalse();
        SqlPruebas.limpiar();
        assertThat(directorio.buscar("a")).hasValue(a);
        assertThat(directorio.buscar("a")).hasValue(a);
        assertThat(SqlPruebas.sentencias().stream().filter(s -> s.startsWith("select id from negocio where slug="))).hasSize(2);
    }
}
