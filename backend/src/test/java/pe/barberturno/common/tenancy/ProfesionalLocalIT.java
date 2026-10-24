package pe.barberturno.common.tenancy;

import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.scheduling.dto.CrearBarberoDto;
import pe.barberturno.users.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Condiciones 4 y 5 de V2-04 con perfiles A/B de la misma cuenta, RLS y sesiones reales. */
class ProfesionalLocalIT extends IdentidadPruebaBase {
    @Autowired JsonMapper json;

    @Test void listado_ordenaPorNombreLocalAunqueLaCuentaTengaOtroNombre() throws Exception {
        var z = persona("zeta", Rol.BARBERO); pertenencia(1L, z, RolNegocio.BARBERO);
        var a = persona("alfa", Rol.BARBERO); pertenencia(1L, a, RolNegocio.BARBERO);
        var primero = new Barbero(z, "Cortes", reloj.instant()); primero.editarPerfil("Alfa local", null, "Cortes", reloj.instant());
        var segundo = new Barbero(a, "Cortes", reloj.instant()); segundo.editarPerfil("Zeta local", null, "Cortes", reloj.instant());
        barberos.saveAndFlush(primero); barberos.saveAndFlush(segundo);
        mvc.perform(get("/api/barberos")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(primero.getId())).andExpect(jsonPath("$[0].nombre").value("Alfa local"))
                .andExpect(jsonPath("$[1].id").value(segundo.getId())).andExpect(jsonPath("$[1].nombre").value("Zeta local"));
    }

    @Test void editarPerfil_soloCambiaAYTodasLasVistasMuestranElNombreLocal() throws Exception {
        var e = escenario();
        var cuenta = huella(e.usuario()); var perfilB = perfil(e.b(), e.perfilB());
        var historia = historia(1);
        editarLocal(e);
        assertThat(huella(e.usuario())).isEqualTo(cuenta);
        assertThat(perfil(e.b(), e.perfilB())).isEqualTo(perfilB);
        assertThat(historia(1)).isEqualTo(historia);
        mvc.perform(get("/api/barberos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(e.a().barbero()))
                .andExpect(jsonPath("$[0].nombre").value("Profesional local A"));
        mvc.perform(get("/api/barberos").cookie(sesion(e.gestor()))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].telefono").value("999000009"));
        mvc.perform(get("/api/reservas/" + e.a().reserva()).cookie(sesion(e.gestor())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.barbero.nombre").value("Profesional local A"));
        mvc.perform(get("/api/reservas").param("desde", "2026-10-01").param("hasta", "2026-10-01").cookie(sesion(e.gestor())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].barbero.nombre").value("Profesional local A"));
        // Disponibilidad conserva su contrato de ids; el catálogo que los rotula presenta el nombre local.
        mvc.perform(get("/api/disponibilidad").param("servicioId", "" + e.a().servicio()).param("barberoId", "" + e.a().barbero()).param("fecha", "2026-10-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.franjas[0].barberoIds[0]").value(e.a().barbero()));
        mvc.perform(reporte("/api/reportes/resumen", e, "2026-10-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.porBarbero[0].nombre").value("Profesional local A"));
        for (String fecha : List.of("2026-10-01", "2026-10-02")) {
            byte[] archivo = mvc.perform(reporte("/api/reportes/resumen.xlsx", e, fecha)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (var libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
                var textos = new ArrayList<String>(); var formato = new DataFormatter();
                for (var fila : libro.getSheetAt(0)) for (var celda : fila) textos.add(formato.formatCellValue(celda));
                assertThat(textos).contains("Profesional local A").doesNotContain(e.usuario().getNombre());
            }
        }
        assertThat(huella(e.usuario())).isEqualTo(cuenta);
        assertThat(perfil(e.b(), e.perfilB())).isEqualTo(perfilB);
    }

    @Test void crearYReprogramar_losAvisosUsanElNombreLocalSinCambiarDestinatarios() throws Exception {
        var e = escenario(); editarLocal(e);
        var cuenta = huella(e.usuario());
        var cliente = usuarios.findById(e.a().cliente()).orElseThrow();
        var creada = mvc.perform(csrf(post("/api/reservas").contentType("application/json").content(
                "{\"servicioId\":" + e.a().servicio() + ",\"barberoId\":" + e.a().barbero() + ",\"inicio\":\"2026-10-01T11:00:00-05:00\"}"), sesion(cliente)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.barbero.nombre").value("Profesional local A")).andReturn();
        long id = json.readTree(creada.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(csrf(post("/api/reservas/" + id + "/reprogramacion").contentType("application/json")
                .content("{\"inicio\":\"2026-10-01T12:00:00-05:00\",\"version\":0}"), sesion(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.barbero.nombre").value("Profesional local A"));
        assertThat(sql.queryForList("select mensaje from notificacion where reserva_id=?", String.class, id)).hasSize(4)
                .allSatisfy(m -> assertThat(m).contains("Profesional local A").doesNotContain(e.usuario().getNombre()));
        assertThat(sql.queryForList("select usuario_id from notificacion where reserva_id=?", Long.class, id))
                .containsExactlyInAnyOrder(cliente.getId(), cliente.getId(), e.usuario().getId(), e.usuario().getId());
        assertThat(huella(e.usuario())).isEqualTo(cuenta);
    }

    @Test void bajaYReactivacion_soloPerfilYMembresiaA_conservaCuentaOtrosNegociosEHistorial() throws Exception {
        var e = escenario();
        var cuenta = huella(e.usuario()); var perfilB = perfil(e.b(), e.perfilB());
        var miembroB = sql.queryForMap("select * from membresia where negocio_id=? and usuario_id=?", e.b(), e.usuario().getId());
        var historiaA = historia(1); var historiaB = historia(e.b());
        var cookie = sesion(e.usuario());
        mvc.perform(get("/api/reservas").param("desde", "2026-10-01").param("hasta", "2026-10-01").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(csrf(estadoPerfil(e.a().barbero(), false), sesion(e.gestor()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasFuturasVigentes").value(1));
        assertThat(huella(e.usuario())).isEqualTo(cuenta);
        assertThat(membresias.findByNegocioIdAndUsuarioId(1L, e.usuario().getId()).orElseThrow().isActiva()).isFalse();
        mvc.perform(get("/api/reservas").param("desde", "2026-10-01").param("hasta", "2026-10-01").cookie(cookie)).andExpect(status().isForbidden());
        proveedor.fijar(e.b());
        try { mvc.perform(get("/api/reservas").param("desde", "2026-10-01").param("hasta", "2026-10-01").cookie(cookie)).andExpect(status().isOk()); }
        finally { proveedor.reiniciar(); }
        var membresiaRevocada = sql.queryForMap("select * from membresia where negocio_id=1 and usuario_id=?", e.usuario().getId());
        mvc.perform(csrf(estadoPerfil(e.a().barbero(), false), sesion(e.gestor()))).andExpect(status().isOk());
        assertThat(sql.queryForMap("select * from membresia where negocio_id=1 and usuario_id=?", e.usuario().getId())).isEqualTo(membresiaRevocada);
        editarLocal(e); // La baja debe permitir gestionar y reactivar su perfil local inactivo.
        mvc.perform(csrf(estadoPerfil(e.a().barbero(), true), sesion(e.gestor()))).andExpect(status().isOk());
        assertThat(membresias.findByNegocioIdAndUsuarioId(1L, e.usuario().getId()).orElseThrow().isActiva()).isTrue();
        mvc.perform(get("/api/reservas").param("desde", "2026-10-01").param("hasta", "2026-10-01").cookie(cookie)).andExpect(status().isOk());
        assertThat(huella(e.usuario())).isEqualTo(cuenta);
        assertThat(perfil(e.b(), e.perfilB())).isEqualTo(perfilB);
        assertThat(sql.queryForMap("select * from membresia where negocio_id=? and usuario_id=?", e.b(), e.usuario().getId())).isEqualTo(miembroB);
        assertThat(historia(1)).isEqualTo(historiaA); assertThat(historia(e.b())).isEqualTo(historiaB);
    }

    @ParameterizedTest @EnumSource(value=RolNegocio.class, names={"PROPIETARIO", "ENCARGADO"})
    void bajaDeGestorConPerfil_conservaGestionYMembresiaCompleta(RolNegocio rol) throws Exception {
        var gestor = persona("gestor-con-perfil", Rol.ADMIN); pertenencia(1L, gestor, rol);
        var perfil = barberos.saveAndFlush(new Barbero(gestor, "Cortes", reloj.instant()));
        var cuenta = huella(gestor); var pertenencia = sql.queryForMap("select * from membresia where negocio_id=1 and usuario_id=?", gestor.getId());
        for (boolean activo : List.of(false, true)) {
            mvc.perform(csrf(estadoPerfil(perfil.getId(), activo), sesion(gestor))).andExpect(status().isOk());
            assertThat(huella(gestor)).isEqualTo(cuenta);
            assertThat(sql.queryForMap("select * from membresia where negocio_id=1 and usuario_id=?", gestor.getId())).isEqualTo(pertenencia);
            mvc.perform(get("/api/usuarios").cookie(sesion(gestor))).andExpect(status().isOk());
        }
    }

    @Test void reactivarPerfil_noReactivaUnaCuentaGlobalInactiva() throws Exception {
        var e = escenario();
        mvc.perform(csrf(estadoPerfil(e.a().barbero(), false), sesion(e.gestor()))).andExpect(status().isOk());
        var cuenta = usuarios.findById(e.usuario().getId()).orElseThrow(); cuenta.desactivar(reloj.instant()); usuarios.saveAndFlush(cuenta);
        var antes = huella(cuenta);
        mvc.perform(csrf(estadoPerfil(e.a().barbero(), true), sesion(e.gestor()))).andExpect(status().isOk());
        assertThat(huella(cuenta)).isEqualTo(antes);
        assertThat(usuarios.findById(cuenta.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test void identificadoresDeB_edicionYEstado404SinCambiarCuentaNiPerfil() throws Exception {
        var e = escenario(); var cuenta = huella(e.usuario()); var antes = perfil(e.b(), e.perfilB());
        mvc.perform(csrf(edicion(e.perfilB()), sesion(e.gestor()))).andExpect(status().isNotFound());
        mvc.perform(csrf(estadoPerfil(e.perfilB(), false), sesion(e.gestor()))).andExpect(status().isNotFound());
        assertThat(huella(e.usuario())).isEqualTo(cuenta); assertThat(perfil(e.b(), e.perfilB())).isEqualTo(antes);
    }

    @ParameterizedTest @CsvSource({"PUT,ausente", "PUT,incorrecto", "PUT,rol", "PATCH,ausente", "PATCH,incorrecto", "PATCH,rol"})
    void escrituraLocal_sesionValidaSinCsrfIncorrectoORolNoAutorizado403(String metodo, String variante) throws Exception {
        var e = escenario(); var cuenta = huella(e.usuario()); var antes = perfil(1, e.a().barbero());
        var solicitud = metodo.equals("PUT") ? edicion(e.a().barbero()) : estadoPerfil(e.a().barbero(), false);
        if (variante.equals("rol")) solicitud = csrf(solicitud, sesion(e.usuario()));
        else {
            solicitud.cookie(sesion(e.gestor()));
            if (variante.equals("incorrecto")) solicitud.cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "valido")).header("X-XSRF-TOKEN", "incorrecto");
        }
        mvc.perform(solicitud).andExpect(status().isForbidden());
        assertThat(huella(e.usuario())).isEqualTo(cuenta); assertThat(perfil(1, e.a().barbero())).isEqualTo(antes);
    }

    @Test void altaConCuentaNueva_copiaLosDatosLocales() {
        var creada = profesionales.crear(new CrearBarberoDto(null, "Profesional nuevo", "nuevo-local@ejemplo.test", "999000008", "Cortes", "Temporal123"));
        var cuenta = usuarios.findByCorreo("nuevo-local@ejemplo.test").orElseThrow();
        assertThat(creada.nombre()).isEqualTo(cuenta.getNombre()); assertThat(creada.telefono()).isEqualTo(cuenta.getTelefono());
        assertThat(barberos.findById(creada.id()).orElseThrow().getNombreVisible()).isEqualTo(cuenta.getNombre());
        assertThat(barberos.findById(creada.id()).orElseThrow().getTelefonoContacto()).isEqualTo(cuenta.getTelefono());
    }

    private Escenario escenario() throws Exception {
        long b = negocioB();
        var a = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "local-a"));
        enNegocio(b, () -> DatosNegocioPrueba.crear(jdbc, "local-b"));
        var usuario = usuarios.findById(a.admin()).orElseThrow();
        pertenencia(1, usuario, RolNegocio.BARBERO); pertenencia(b, usuario, RolNegocio.BARBERO);
        var perfilB = enNegocio(b, () -> {
            var perfil = new Barbero(usuario, "Barba B", reloj.instant());
            perfil.editarPerfil("Profesional local B", "999000007", "Barba B", reloj.instant());
            return barberos.saveAndFlush(perfil);
        });
        return new Escenario(b, a, usuario, propietario("titular-local"), perfilB.getId());
    }
    private void editarLocal(Escenario e) throws Exception {
        mvc.perform(csrf(edicion(e.a().barbero()), sesion(e.gestor()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Profesional local A")).andExpect(jsonPath("$.telefono").value("999000009"))
                .andExpect(jsonPath("$.especialidad").value("Cortes A"));
    }
    private static MockHttpServletRequestBuilder edicion(long id) { return put("/api/barberos/" + id).contentType("application/json").content("{\"nombre\":\"Profesional local A\",\"telefono\":\"999000009\",\"especialidad\":\"Cortes A\"}"); }
    private static MockHttpServletRequestBuilder estadoPerfil(long id, boolean activo) { return patch("/api/barberos/" + id + "/estado").contentType("application/json").content("{\"activo\":" + activo + "}"); }
    private MockHttpServletRequestBuilder reporte(String ruta, Escenario e, String fecha) { return get(ruta).param("desde", fecha).param("hasta", fecha).param("barberoId", "" + e.a().barbero()).cookie(sesion(e.gestor())); }
    private Map<String,Object> perfil(long negocio, long id) { return enNegocio(negocio, () -> jdbc.queryForMap("select * from barbero where id=?", id)); }
    private Map<String,Object> historia(long negocio) {
        return enNegocio(negocio, () -> Map.of("reservas", jdbc.queryForList("select * from reserva order by id"), "auditoria", jdbc.queryForList("select * from auditoria_reserva order by id"), "avisos", jdbc.queryForList("select * from notificacion order by id")));
    }
    private record Escenario(long b, DatosNegocioPrueba a, Usuario usuario, Usuario gestor, long perfilB) { }
}
