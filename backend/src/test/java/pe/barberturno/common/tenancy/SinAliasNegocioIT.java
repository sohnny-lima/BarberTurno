package pe.barberturno.common.tenancy;

import java.util.LinkedHashMap;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfiguration;
import pe.barberturno.platform.RolNegocio;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Arranque sin la propiedad de alias; contexto propio cerrado por la base con DirtiesContext. */
@ContextConfiguration(initializers = SinAliasNegocioIT.SinAlias.class)
class SinAliasNegocioIT extends PeticionNegocioPruebaBase {
    static class SinAlias implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext contexto) {
            var fuentes = contexto.getEnvironment().getPropertySources();
            String clave = "barberturno.tenancy.alias-legado.slug";
            for (var fuente : StreamSupport.stream(fuentes.spliterator(), false).toList()) {
                if (fuente instanceof EnumerablePropertySource<?> enumerada && fuente.getProperty(clave) != null) {
                    var valores = new LinkedHashMap<String, Object>();
                    for (String nombre : enumerada.getPropertyNames()) {
                        if (!nombre.equals(clave)) valores.put(nombre, enumerada.getProperty(nombre));
                    }
                    fuentes.replace(fuente.getName(), new MapPropertySource(fuente.getName(), valores));
                }
            }
            assertThat(contexto.getEnvironment().containsProperty(clave)).isFalse();
        }
    }

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
}
