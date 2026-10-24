package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import pe.barberturno.platform.RolNegocio;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResolucionNegocioIT extends PeticionNegocioPruebaBase {
    @Test void cuerpoConsultaYCabecera_noSeleccionanElNegocioDeUnaEscritura() throws Exception {
        servicio("a"); servicio("b");
        var cookie = sesion(personal("gestor-cuerpo", "a", RolNegocio.PROPIETARIO));
        mvc.perform(csrf(post("/api/n/a/servicios").param("negocioId", Long.toString(b)).header("X-Negocio-Id", b)
                .contentType("application/json").content("""
                {"nombre":"Nuevo en A","descripcion":"Ficticio","duracionMin":30,"precio":20,"negocioId":%d}
                """.formatted(b)), cookie)).andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(this.<Long>en("a", servicios::count)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(this.<Long>en("b", servicios::count)).isEqualTo(1);
    }

    @Test void membresiaRevocada_mismaSesionPierdeElRolEnLaPeticionSiguiente() throws Exception {
        var cuenta = personal("revocada", "a", RolNegocio.PROPIETARIO);
        var cookie = sesion(cuenta);
        mvc.perform(get("/api/n/a/usuarios").cookie(cookie)).andExpect(status().isOk());
        var miembro = membresias.findByNegocioIdAndUsuarioId(a, cuenta.getId()).orElseThrow();
        miembro.revocar(reloj.instant()); membresias.save(miembro);
        mvc.perform(get("/api/n/a/sesion").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").isEmpty());
        mvc.perform(get("/api/n/a/usuarios").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(csrf(post("/api/n/a/servicios").contentType("application/json").content("{}"), cookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(get("/api/n/no-existe/usuarios").cookie(cookie)).andExpect(status().isNotFound());
    }

    @Test void da22YDa26_conPrefijo_rechazaYBorraJwtRevocado_yRegistroLoginLogoutLoSustituyen() throws Exception {
        var inicial = mvc.perform(get("/api/n/a/sesion")).andExpect(status().isUnauthorized()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        var registro = mvc.perform(post("/api/n/a/registro").cookie(inicial).header("X-XSRF-TOKEN", inicial.getValue())
                .contentType("application/json").content(RutasNegocioIT.registro("revocable@ejemplo.test")))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var cookie = registro.getCookie("BT_SESION");
        var csrfNuevo = registro.getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(csrfNuevo.getValue()).isNotEqualTo(inicial.getValue());
        mvc.perform(get("/api/n/a/servicios").cookie(cookie, csrfNuevo)).andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"));
        var cuenta = usuarios.findByCorreo("revocable@ejemplo.test").orElseThrow();
        cuenta.cambiarPassword("hash-ficticio-no-utilizable", false, reloj.instant()); usuarios.saveAndFlush(cuenta);
        var rechazo = mvc.perform(get("/api/n/a/servicios").cookie(cookie)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO")).andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(rechazo.getCookie("BT_SESION").getMaxAge()).isZero();
        mvc.perform(get("/api/n/a/servicios")).andExpect(status().isOk());
        mvc.perform(csrf(post("/api/n/b/registro").contentType("application/json")
                .content(RutasNegocioIT.registro("recuperada@ejemplo.test")), cookie))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CLIENTE"));
        var login = mvc.perform(csrf(post("/api/auth/login").contentType("application/json")
                .content("{\"correo\":\"recuperada@ejemplo.test\",\"password\":\"ClaveFicticia123\"}"), cookie))
                .andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(get("/api/n/b/sesion").cookie(login.getCookie("BT_SESION"))).andExpect(jsonPath("$.rol").value("CLIENTE"));
        mvc.perform(csrf(post("/api/auth/logout"), cookie)).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("BT_SESION", 0));
    }
    @Test void prefijo_resuelveCatalogoPublicoYNoAceptaNegocioIdDeConsultaOCabecera() throws Exception {
        servicio("a"); servicio("b");
        mvc.perform(get("/api/n/a/servicios").param("negocioId", Long.toString(b)).header("X-Negocio-Id", b))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Corte a"));
        mvc.perform(get("/api/n/b/servicios")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Corte b"));
    }

    @Test void inexistenteOInvalido_404_yIdentidadBajoNegocio_404() throws Exception {
        for (String ruta : new String[]{"/api/n/no-existe/servicios", "/api/n/A/servicios",
                "/api/n/a/auth/sesion", "/api/n/a/auth/registro", "/api/n/a/perfil", "/api/n/a/mis-negocios"}) {
            mvc.perform(get(ruta)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
    }

    @Test void mismoCaminoDeCsrfYAutorizacion_403ConSesionValida() throws Exception {
        var gestor = sesion(personal("propietario", "a", RolNegocio.PROPIETARIO));
        mvc.perform(post("/api/n/a/servicios").cookie(gestor).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(csrf(post("/api/n/a/servicios"), gestor).with(r -> { r.removeHeader("X-XSRF-TOKEN"); r.addHeader("X-XSRF-TOKEN", "incorrecto"); return r; })
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
}
