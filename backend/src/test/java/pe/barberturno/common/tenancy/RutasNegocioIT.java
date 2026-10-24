package pe.barberturno.common.tenancy;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.barberturno.platform.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RutasNegocioIT extends PeticionNegocioPruebaBase {
    static String registro(String correo) {
        return """
                {"nombre":"Cliente ficticio","correo":"%s","telefono":"999000001",
                 "password":"ClaveFicticia123","aceptaPrivacidad":true}
                """.formatted(correo);
    }

    @Test void registro_cuentaYFichaAtomicasEnLaRuta_conCookieRevocada_ySinMembresia() throws Exception {
        var respuesta = mvc.perform(csrf(post("/api/n/a/registro").contentType("application/json")
                .content(registro("registro-a@ejemplo.test")), new Cookie("BT_SESION", "revocada")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andReturn().getResponse();
        var cuenta = usuarios.findByCorreo("registro-a@ejemplo.test").orElseThrow();
        assertThat(en("a", () -> clientes.findByUsuarioId(cuenta.getId()))).isPresent();
        assertThat(en("b", () -> clientes.findByUsuarioId(cuenta.getId()))).isEmpty();
        assertThat(membresias.listarPropiasActivas(cuenta.getId())).isEmpty();
        assertThat(respuesta.getCookie("BT_SESION")).isNotNull();
        assertThat(respuesta.getCookie("XSRF-TOKEN")).isNotNull();
        mvc.perform(get("/api/n/a/sesion").cookie(respuesta.getCookie("BT_SESION")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("CLIENTE"));
        mvc.perform(csrf(post("/api/n/b/registro").contentType("application/json")
                .content(registro("registro-a@ejemplo.test")), null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
        assertThat(en("b", () -> clientes.findByUsuarioId(cuenta.getId()))).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"registro", "clientes/alta"})
    void escriturasNuevas_sesionValidaSinCsrfOIncorrecto_403(String ruta) throws Exception {
        var cuenta = persona("csrf");
        Cookie sesion = sesion(cuenta);
        String cuerpo = ruta.equals("registro") ? registro("otro@ejemplo.test") : "{}";
        mvc.perform(post("/api/n/a/" + ruta).cookie(sesion).contentType("application/json").content(cuerpo))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(csrf(post("/api/n/a/" + ruta).contentType("application/json").content(cuerpo), sesion)
                .with(r -> { r.removeHeader("X-XSRF-TOKEN"); r.addHeader("X-XSRF-TOKEN", "incorrecto"); return r; }))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(this.<Long>en("a", clientes::count)).isZero();
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test void alta_cuentaSinRolObtieneFicha_sinConcederPersonal_yDuplicada409InclusoInactiva() throws Exception {
        var cuenta = persona("alta");
        var sesion = sesion(cuenta);
        mvc.perform(get("/api/n/a/sesion").cookie(sesion)).andExpect(jsonPath("$.rol").isEmpty());
        mvc.perform(csrf(post("/api/n/a/clientes/alta").contentType("application/json")
                .content("{\"usuarioId\":99999,\"negocioId\":" + b + "}"), sesion))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.activo").value(true));
        mvc.perform(get("/api/n/a/sesion").cookie(sesion)).andExpect(jsonPath("$.rol").value("CLIENTE"));
        assertThat(membresias.listarPropiasActivas(cuenta.getId())).isEmpty();
        assertThat(this.<Long>en("b", clientes::count)).isZero();
        mvc.perform(csrf(post("/api/n/a/clientes/alta"), sesion)).andExpect(status().isConflict());
        en("a", () -> { var ficha = clientes.findByUsuarioId(cuenta.getId()).orElseThrow(); ficha.desactivar(reloj.instant()); return clientes.saveAndFlush(ficha); });
        mvc.perform(csrf(post("/api/n/a/clientes/alta"), sesion)).andExpect(status().isConflict());
        mvc.perform(csrf(post("/api/n/b/clientes/alta"), sesion)).andExpect(status().isCreated());
        mvc.perform(csrf(post("/api/n/b/clientes/alta"), null)).andExpect(status().isUnauthorized());
    }

    @Test void misNegocios_soloPropiosActivos_prioridadPersonal_ySinContextoGlobal() throws Exception {
        var cuenta = personal("propio", "a", RolNegocio.ENCARGADO);
        var ajena = personal("ajena", "b", RolNegocio.PROPIETARIO);
        en("a", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(cuenta, reloj.instant())));
        en("b", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(cuenta, reloj.instant())));
        mvc.perform(get("/api/mis-negocios").cookie(sesion(cuenta)).param("usuarioId", ajena.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("a")).andExpect(jsonPath("$[0].rol").value("ENCARGADO"))
                .andExpect(jsonPath("$[1].slug").value("b")).andExpect(jsonPath("$[1].rol").value("CLIENTE"));
        en("b", () -> jdbc.update("update cliente set activo=false where usuario_id=?", cuenta.getId()));
        var miembro = membresias.findByNegocioIdAndUsuarioId(a, cuenta.getId()).orElseThrow();
        miembro.revocar(reloj.instant()); membresias.save(miembro);
        mvc.perform(get("/api/mis-negocios").cookie(sesion(cuenta)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].rol").value("CLIENTE"));
        en("a", () -> jdbc.update("update cliente set activo=false where usuario_id=?", cuenta.getId()));
        mvc.perform(get("/api/mis-negocios").cookie(sesion(cuenta))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/mis-negocios")).andExpect(status().isUnauthorized());
        assertThat(contexto.delimitado()).isFalse();
    }
}
