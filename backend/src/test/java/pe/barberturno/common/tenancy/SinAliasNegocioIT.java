package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import pe.barberturno.support.RutasNegocioPruebas;
import pe.barberturno.platform.RolNegocio;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CG-5: configuración normal sin alias; rechazos antes de autenticación, CSRF y MVC. */
class SinAliasNegocioIT extends PeticionNegocioPruebaBase {

    @Test void sinPropiedad_rutasLegadas404_globalesSinRol_yPrefijoSigueOperando() throws Exception {
        var cuenta = personal("sin-alias", "a", RolNegocio.PROPIETARIO);
        var cookie = sesion(cuenta);
        assertThat(entorno.containsProperty("barberturno.tenancy.alias-legado.slug")).isFalse();
        mvc.perform(get("/api/servicios").cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/reservas").cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/registro").cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").isEmpty());
        mvc.perform(get("/api/perfil").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/mis-negocios").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("a"));
        mvc.perform(get("/api/n/a/sesion").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMIN"));
        mvc.perform(get("/api/n/a/usuarios").cookie(cookie)).andExpect(status().isOk());
    }

    @Test void todasLasFamiliasLegadas_conYSinSesion_404SinEjecutarControladores() throws Exception {
        var cookie = sesion(personal("sin-alias-matriz", "a", RolNegocio.PROPIETARIO));
        for (String ruta : RutasNegocioPruebas.legadas()) {
            for (HttpMethod metodo : new HttpMethod[]{HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST,
                    HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
                for (boolean autenticada : new boolean[]{false, true}) {
                    var solicitud = request(metodo, ruta).contentType("application/json").content("{}");
                    if (autenticada) solicitud.cookie(cookie);
                    var resultado = mvc.perform(solicitud).andExpect(status().isNotFound())
                            .andExpect(content().contentType("application/problem+json")).andReturn();
                    assertThat(resultado.getHandler()).as("%s %s, sesión=%s", metodo, ruta, autenticada).isNull();
                    assertThat(contexto.delimitado()).isFalse();
                }
            }
        }
    }
}
