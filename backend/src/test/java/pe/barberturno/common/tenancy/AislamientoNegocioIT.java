package pe.barberturno.common.tenancy;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.barberturno.auth.JwtService;
import pe.barberturno.catalog.ServicioService;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.notifications.NotificacionService;
import pe.barberturno.platform.*;
import pe.barberturno.reporting.ReporteService;
import pe.barberturno.reservations.ReservaConsultaService;
import pe.barberturno.scheduling.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AislamientoNegocioIT extends TenancyPruebaBase {
    @Autowired ServicioService catalogo;
    @Autowired BarberoService barberos;
    @Autowired DisponibilidadService disponibilidad;
    @Autowired ReservaConsultaService reservas;
    @Autowired NotificacionService avisos;
    @Autowired ReporteService reportes;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;

    @Test
    void dosNegocios_homonimosNoSeVenEnLasSieteTablasNiEnServiciosV1() throws Exception {
        long b = negocioB();
        var datosB = crearDatos(b, "b");
        var datosA = crearDatos(1L, "a");
        // El mismo destinatario global permite detectar una lectura masiva sin ámbito de negocio.
        enNegocio(b, () -> jdbc.update("update notificacion set usuario_id=?", datosA.cliente()));
        enNegocio(1L, () -> {
            for (String tabla : EsquemaV2IT.TABLAS) {
                assertThat(jdbc.queryForList("select distinct negocio_id from " + tabla, Long.class)).as(tabla).containsExactly(1L);
                assertThat(jdbc.queryForObject("select count(*) from " + tabla + " where negocio_id=?", Integer.class, b)).isZero();
            }
            return null;
        });
        proveedor.fijar(1L);
        var admin = new UsuarioAutenticado(datosA.admin(), Rol.ADMIN, Optional.of(datosA.barbero()), false);
        var cliente = new UsuarioAutenticado(datosA.cliente(), Rol.CLIENTE, Optional.empty(), false);
        var fecha = LocalDate.of(2026, 10, 1);
        assertThat(catalogo.listar(true)).extracting("id").containsExactly(datosA.servicio());
        assertThat(barberos.listar(true, true)).extracting("id").containsExactly(datosA.barbero());
        var franjas = disponibilidad.consultarFranjas(datosA.servicio(), Optional.empty(), fecha, Optional.empty()).franjas();
        assertThat(franjas).isNotEmpty().allSatisfy(f -> assertThat(f.barberoIds()).containsExactly(datosA.barbero()));
        assertThatThrownBy(() -> disponibilidad.consultarFranjas(datosB.servicio(), Optional.empty(), fecha, Optional.empty()))
                .isInstanceOf(NegocioException.class).extracting("codigo").isEqualTo(ErrorCodigo.NO_ENCONTRADO);
        assertThat(reservas.listar(admin, fecha, fecha, null, null, null, null, 0, 20).contenido())
                .extracting("id").containsExactly(datosA.reserva());
        assertThatThrownBy(() -> reservas.detalle(admin, datosB.reserva())).isInstanceOf(NegocioException.class)
                .extracting("codigo").isEqualTo(ErrorCodigo.NO_ENCONTRADO);
        assertThat(avisos.conteo(cliente).noLeidas()).isEqualTo(1);
        avisos.marcarTodasLeidas(cliente);
        assertThat(avisos.conteo(cliente).noLeidas()).isZero();
        assertThat(reportes.resumen(admin, fecha, fecha, null, null).total()).isEqualTo(1);
        assertThat(enNegocio(b, () -> jdbc.queryForObject("select count(*) from notificacion where not leida", Integer.class))).isEqualTo(1);
        var sesion = new jakarta.servlet.http.Cookie("BT_SESION", jwt.emitir(usuarios.findById(datosA.admin()).orElseThrow()));
        mvc.perform(get("/api/reservas/" + datosB.reserva()).cookie(sesion)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void nombreDuplicado_mantieneEl409PublicoEnElNegocio() throws Exception {
        crearDatos(1L, "a");
        var admin = usuarios.findByCorreo("admin-a@ejemplo.test").orElseThrow();
        var sesion = new jakarta.servlet.http.Cookie("BT_SESION", jwt.emitir(admin));
        var csrf = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/servicios").cookie(sesion, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content("""
                        {"nombre":"CORTE HOMÓNIMO","descripcion":"Prueba","duracionMin":30,"precio":20}
                        """)).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"));
    }

    private DatosNegocioPrueba crearDatos(long negocioId, String sufijo) {
        return enNegocio(negocioId, () -> {
            var datos = DatosNegocioPrueba.crear(jdbc, sufijo);
            membresias.save(new Membresia(negocioId, datos.admin(), RolNegocio.PROPIETARIO, reloj.instant()));
            return datos;
        });
    }
}
