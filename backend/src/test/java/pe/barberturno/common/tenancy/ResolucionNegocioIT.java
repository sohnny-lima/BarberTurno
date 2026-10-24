package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import pe.barberturno.platform.RolNegocio;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResolucionNegocioIT extends PeticionNegocioPruebaBase {
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
