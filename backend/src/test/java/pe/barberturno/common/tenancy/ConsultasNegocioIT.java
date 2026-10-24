package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import pe.barberturno.platform.*;
import pe.barberturno.support.SqlPruebas;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConsultasNegocioIT extends PeticionNegocioPruebaBase {
    @FunctionalInterface interface Accion { void ejecutar() throws Exception; }

    void filtro(String tabla, Accion accion) throws Exception {
        SqlPruebas.limpiar();
        accion.ejecutar();
        var sentencias = SqlPruebas.sentencias().stream().filter(s -> s.contains("from " + tabla + " ")
                || tabla.equals("notificacion") && s.startsWith("update notificacion ")).toList();
        assertThat(sentencias).as(tabla).isNotEmpty().allSatisfy(s ->
                assertThat(s.replaceAll("\\s+", " ")).containsPattern("where .*negocio_id=\\?"));
    }

    @Test void todasLasConsultasG_tienenPredicadoExplicitoConRls() throws Exception {
        var datos = en("a", () -> DatosNegocioPrueba.crear(jdbc, "sql-a"));
        en("b", () -> DatosNegocioPrueba.crear(jdbc, "sql-b"));
        membresias.save(new Membresia(a, datos.admin(), RolNegocio.PROPIETARIO, reloj.instant()));
        var admin = sesion(usuarios.findById(datos.admin()).orElseThrow());
        en("a", () -> {
            DatosNegocioPrueba.reservar(jdbc, datos.ficha(), datos.admin(), datos.barbero(), datos.servicio(), "CONFIRMADA", 60);
            return jdbc.update("update notificacion set usuario_id=?", datos.admin());
        });
        filtro("servicio", () -> mvc.perform(get("/api/n/a/servicios")).andExpect(status().isOk()));
        filtro("servicio", () -> en("a", () -> servicios.buscarPorNombre(a, "Corte homónimo")));
        filtro("barbero", () -> mvc.perform(get("/api/n/a/barberos")).andExpect(status().isOk()));
        filtro("barbero", () -> en("a", () -> barberos.countByActivoTrue(a)));
        // La disponibilidad ejecuta findByActivoTrueOrderByIdAsc con negocio explícito.
        filtro("barbero", () -> mvc.perform(get("/api/n/a/disponibilidad")
                .param("servicioId", Long.toString(datos.servicio())).param("fecha", "2026-10-01"))
                .andExpect(status().isOk()));
        filtro("reserva", () -> mvc.perform(get("/api/n/a/reservas").cookie(admin)
                .param("desde", "2026-10-01").param("hasta", "2026-10-01").param("tamano", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2)));
        filtro("reserva", () -> mvc.perform(get("/api/n/a/reportes/resumen").cookie(admin)
                .param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)));
        filtro("notificacion", () -> mvc.perform(get("/api/n/a/notificaciones").cookie(admin).param("tamano", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2)));
        filtro("notificacion", () -> mvc.perform(get("/api/n/a/notificaciones/conteo").cookie(admin)).andExpect(status().isOk()));
        filtro("notificacion", () -> mvc.perform(csrf(post("/api/n/a/notificaciones/lectura"), admin)).andExpect(status().isNoContent()));
        filtro("cliente", () -> mvc.perform(get("/api/n/a/clientes/" + datos.ficha()).cookie(admin)).andExpect(status().isOk()));
        filtro("cliente", () -> mvc.perform(csrf(patch("/api/n/a/clientes/" + datos.ficha()).contentType("application/json")
                .content("{\"nombre\":\"Ficha editada\",\"telefono\":\"999000001\"}"), admin)).andExpect(status().isOk()));
        filtro("cliente", () -> mvc.perform(csrf(post("/api/n/a/clientes/alta"), admin)).andExpect(status().isCreated()));
        SqlPruebas.limpiar();
        mvc.perform(get("/api/n/a/usuarios").cookie(admin).param("tamano", "1")).andExpect(status().isOk());
        assertThat(SqlPruebas.sentencias().stream().filter(s -> s.contains("from cliente") && s.contains("from membresia")))
                .isNotEmpty().allSatisfy(s -> assertThat(s).containsPattern("c\\d+_\\d+\\.negocio_id=\\?"));
        assertThat(this.<java.util.List<?>>en("a", () -> servicios.listar(b, true))).isEmpty();
        assertThat(this.<java.util.List<?>>en("b", () -> servicios.listar(a, true))).isEmpty();
    }

    @Test void cuentaValidaSinPapel_403EnProtegidas_yCatalogoPublicoConservado() throws Exception {
        var cookie = sesion(persona("sin-rol"));
        for (String ruta : new String[]{"reservas/9999", "reservas/9999/auditoria", "notificaciones", "notificaciones/conteo", "usuarios"}) {
            mvc.perform(get("/api/n/b/" + ruta).cookie(cookie)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        }
        mvc.perform(get("/api/n/b/servicios").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/n/b/sesion").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").isEmpty());
    }
}
