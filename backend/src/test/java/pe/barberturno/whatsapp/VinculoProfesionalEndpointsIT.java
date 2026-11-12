package pe.barberturno.whatsapp;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.barberturno.common.tenancy.PeticionNegocioPruebaBase;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración HTTP de los endpoints de vínculo profesional bajo /api/n/{slug}/whatsapp/vinculo-profesional (CT-16, V2-DA-106).
 * Valida que POST y DELETE exijan CSRF válido devolviendo 403 ante omisión o incorrección;
 * que con CSRF y sesión profesional activa POST devuelva 201 con {codigo, caducaEn};
 * que GET proyecte {vinculado, vinculadoEn} sin exponer wa_id;
 * que DELETE devuelva 204 y desvincule;
 * y que clientes o cuentas de otro negocio reciban 404 NO_ENCONTRADO en todas las operaciones.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class VinculoProfesionalEndpointsIT extends PeticionNegocioPruebaBase {

    private Usuario barberoCuentaA;
    private Usuario barberoCuentaB;
    private Usuario clienteCuentaA;

    @BeforeEach
    void prepararCuentas() {
        // Barbero con membresía y perfil activo en negocio A
        barberoCuentaA = usuarios.saveAndFlush(new Usuario("Barbero A", "barbero-end-a@ejemplo.test", "999111001",
                "hash-ficticio", Rol.BARBERO, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(a, barberoCuentaA.getId(), RolNegocio.BARBERO, reloj.instant()));
        en("a", () -> barberos.saveAndFlush(new Barbero(barberoCuentaA, "Barbero A Visible", reloj.instant())));

        // Barbero con membresía y perfil activo en negocio B
        barberoCuentaB = usuarios.saveAndFlush(new Usuario("Barbero B", "barbero-end-b@ejemplo.test", "999111002",
                "hash-ficticio", Rol.BARBERO, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(b, barberoCuentaB.getId(), RolNegocio.BARBERO, reloj.instant()));
        en("b", () -> barberos.saveAndFlush(new Barbero(barberoCuentaB, "Barbero B Visible", reloj.instant())));

        // Cliente en negocio A (sin perfil de barbero)
        clienteCuentaA = usuarios.saveAndFlush(new Usuario("Cliente A", "cliente-end-a@ejemplo.test", "999111003",
                "hash-ficticio", Rol.CLIENTE, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(a, clienteCuentaA.getId(), RolNegocio.CLIENTE, reloj.instant()));
    }

    @Test
    void postYDelete_sinCsrfOConCsrfIncorrecto_devuelve403() throws Exception {
        Cookie sesionBarbero = sesion(barberoCuentaA);

        // POST sin CSRF -> 403
        mvc.perform(post("/api/n/a/whatsapp/vinculo-profesional")
                .cookie(sesionBarbero))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        // POST con CSRF incorrecto -> 403
        mvc.perform(post("/api/n/a/whatsapp/vinculo-profesional")
                .cookie(sesionBarbero)
                .cookie(new Cookie("XSRF-TOKEN", "token-invalido"))
                .header("X-XSRF-TOKEN", "token-distinto"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        // DELETE sin CSRF -> 403
        mvc.perform(delete("/api/n/a/whatsapp/vinculo-profesional")
                .cookie(sesionBarbero))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        // DELETE con CSRF incorrecto -> 403
        mvc.perform(delete("/api/n/a/whatsapp/vinculo-profesional")
                .cookie(sesionBarbero)
                .cookie(new Cookie("XSRF-TOKEN", "token-invalido"))
                .header("X-XSRF-TOKEN", "token-distinto"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void flujoCompleto_conCsrfCorrecto_post201_getSinWaId_yDelete204() throws Exception {
        Cookie sesionBarbero = sesion(barberoCuentaA);

        // 1. POST con CSRF correcto -> 201 y JSON {codigo, caducaEn}
        var resultadoPost = mvc.perform(csrf(post("/api/n/a/whatsapp/vinculo-profesional"), sesionBarbero))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").isString())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("^[A-Z0-9]{4}-[A-Z0-9]{4}$")))
                .andExpect(jsonPath("$.caducaEn").isNotEmpty())
                .andReturn();

        // 2. GET con sesión -> 200 y JSON {vinculado, vinculadoEn}, sin wa_id ni waId
        var resultadoGet = mvc.perform(get("/api/n/a/whatsapp/vinculo-profesional")
                .cookie(sesionBarbero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vinculado").isBoolean())
                .andExpect(jsonPath("$.wa_id").doesNotExist())
                .andExpect(jsonPath("$.waId").doesNotExist())
                .andReturn();

        String bodyGet = resultadoGet.getResponse().getContentAsString();
        assertThat(bodyGet).doesNotContain("wa_id").doesNotContain("waId");

        // 3. DELETE con CSRF correcto -> 204
        mvc.perform(csrf(delete("/api/n/a/whatsapp/vinculo-profesional"), sesionBarbero))
                .andExpect(status().isNoContent());

        // GET posterior sigue respondiendo sin wa_id y con vinculado=false
        mvc.perform(get("/api/n/a/whatsapp/vinculo-profesional")
                .cookie(sesionBarbero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vinculado").value(false))
                .andExpect(jsonPath("$.vinculadoEn").doesNotExist());
    }

    @Test
    void clienteOCuentaDeOtroNegocio_devuelve404() throws Exception {
        Cookie sesionClienteA = sesion(clienteCuentaA);
        Cookie sesionBarberoB = sesion(barberoCuentaB);

        // Cliente en negocio A intentando acceder a endpoints de vínculo profesional en A -> 404
        mvc.perform(csrf(post("/api/n/a/whatsapp/vinculo-profesional"), sesionClienteA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        mvc.perform(get("/api/n/a/whatsapp/vinculo-profesional").cookie(sesionClienteA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        mvc.perform(csrf(delete("/api/n/a/whatsapp/vinculo-profesional"), sesionClienteA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        // Barbero de negocio B intentando acceder a endpoints de vínculo profesional en negocio A -> 404
        mvc.perform(csrf(post("/api/n/a/whatsapp/vinculo-profesional"), sesionBarberoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        mvc.perform(get("/api/n/a/whatsapp/vinculo-profesional").cookie(sesionBarberoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        mvc.perform(csrf(delete("/api/n/a/whatsapp/vinculo-profesional"), sesionBarberoB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
