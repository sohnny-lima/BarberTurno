package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.users.Rol;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FichaClienteIT extends IdentidadPruebaBase {
    @Test void mismaCuenta_dosFichasConDatosLocalesDistintos_soloSeVenLasDelContexto() throws Exception {
        long b = negocioB();
        var persona = persona("compartida", Rol.CLIENTE);
        var a = ficha(1L, persona);
        var otra = ficha(b, persona);
        a.actualizarDatos("Cliente en A", "51999000001", reloj.instant());
        clientes.saveAndFlush(a);
        otra.actualizarDatos("Cliente en B", "51999000002", reloj.instant());
        enNegocio(b, () -> clientes.saveAndFlush(otra));
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(clientes.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(a.getId());
            assertThat(c.getNombre()).isEqualTo("Cliente en A");
            assertThat(c.getTelefono()).isEqualTo("51999000001");
        });
        assertThat(enNegocio(1L, () -> jdbc.queryForObject("select count(*) from cliente where id=?", Integer.class, otra.getId()))).isZero();
        var fichasB = enNegocio(b, clientes::findAll);
        assertThat(fichasB).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(otra.getId());
            assertThat(c.getNombre()).isEqualTo("Cliente en B");
            assertThat(c.getTelefono()).isEqualTo("51999000002");
        });
    }
    @Test void rolCliente_dependeDeFichaActivaYSeRevalidaConElMismoJwt_membresiaTienePrioridad() throws Exception {
        long b = negocioB();
        var persona = persona("rol-legado-admin", Rol.ADMIN);
        var cookie = sesion(persona);
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").isEmpty());
        var local = ficha(1L, persona);
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").value("CLIENTE"));
        proveedor.fijar(b);
        try {
            mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").isEmpty());
            mvc.perform(get("/api/reservas/mias").cookie(cookie)).andExpect(status().isForbidden());
        } finally { proveedor.reiniciar(); }
        pertenencia(1L, persona, RolNegocio.BARBERO);
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").value("BARBERO"));
        var pertenencia = membresias.findByNegocioIdAndUsuarioId(1L, persona.getId()).orElseThrow();
        pertenencia.revocar(reloj.instant()); membresias.save(pertenencia);
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").value("CLIENTE"));
        local.desactivar(reloj.instant()); clientes.saveAndFlush(local);
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").isEmpty());
        var legado = persona("cliente-sin-ficha", Rol.CLIENTE);
        assertThat(auth.autenticar(legado.getId(), 0).orElseThrow().rol()).isNull();
    }
}
