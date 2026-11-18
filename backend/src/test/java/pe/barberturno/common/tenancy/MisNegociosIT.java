package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import pe.barberturno.customers.Cliente;
import pe.barberturno.platform.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** V2-DA-44: todos los papeles activos propios, sin duplicar negocios ni incorporar relaciones ajenas. */
class MisNegociosIT extends PeticionNegocioPruebaBase {
    @Test void conservaRolPrioritario_yExponeAmbosPapelesSoloConFichaActiva() throws Exception {
        var persona = personal("doble", "a", RolNegocio.PROPIETARIO);
        en("a", () -> clientes.saveAndFlush(new Cliente(persona, reloj.instant())));
        var fichaB = en("b", () -> clientes.saveAndFlush(new Cliente(persona, reloj.instant())));
        var cookie = sesion(persona);
        personal("ajena", "b", RolNegocio.ENCARGADO);
        mvc.perform(get("/api/mis-negocios").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("a")).andExpect(jsonPath("$[0].rol").value("PROPIETARIO"))
                .andExpect(jsonPath("$[0].papeles.length()").value(2)).andExpect(jsonPath("$[0].papeles[0]").value("PROPIETARIO")).andExpect(jsonPath("$[0].papeles[1]").value("CLIENTE"))
                .andExpect(jsonPath("$[1].rol").value("CLIENTE"))
                .andExpect(jsonPath("$[1].papeles.length()").value(1)).andExpect(jsonPath("$[1].papeles[0]").value("CLIENTE"));
        en("b", () -> { var c = clientes.findById(fichaB.getId()).orElseThrow(); c.desactivar(reloj.instant()); return clientes.saveAndFlush(c); });
        var m = membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow();
        m.revocar(MotivoRevocacion.SALIDA, reloj.instant()); membresias.save(m);
        mvc.perform(get("/api/mis-negocios").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].rol").value("CLIENTE"))
                .andExpect(jsonPath("$[0].papeles.length()").value(1)).andExpect(jsonPath("$[0].papeles[0]").value("CLIENTE"));
    }

    @Test void personalSinFicha_soloMembresia_ySinRelacionesListaVacia() throws Exception {
        var persona = personal("personal", "b", RolNegocio.BARBERO);
        mvc.perform(get("/api/mis-negocios").cookie(sesion(persona))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].rol").value("BARBERO"))
                .andExpect(jsonPath("$[0].papeles.length()").value(1)).andExpect(jsonPath("$[0].papeles[0]").value("BARBERO"));
        mvc.perform(get("/api/mis-negocios").cookie(sesion(persona("sin-relacion"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/mis-negocios")).andExpect(status().isUnauthorized());
    }
}
