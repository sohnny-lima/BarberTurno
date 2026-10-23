package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.users.Rol;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FichaClienteIT extends IdentidadPruebaBase {
    @org.springframework.beans.factory.annotation.Autowired pe.barberturno.customers.ClienteService fichas;

    @ParameterizedTest @ValueSource(strings = {"PROPIETARIO", "ENCARGADO"})
    void personal_editaSoloSuFicha_usuarioCompletoYOtraFichaPermanecenIdenticos(String rol) throws Exception {
        long b = negocioB();
        var actor = persona("gestor", Rol.ADMIN);
        pertenencia(1L, actor, RolNegocio.valueOf(rol));
        var persona = persona("compartida", Rol.CLIENTE);
        var a = ficha(1L, persona);
        var otra = ficha(b, persona);
        var usuarioAntes = huella(persona);
        var fichaAntesB = enNegocio(b, () -> jdbc.queryForMap("select * from cliente where id=?", otra.getId()));
        mvc.perform(get("/api/clientes/" + a.getId()).cookie(sesion(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(a.getId()));
        mvc.perform(csrf(editar(a.getId(), "Nombre solo A", "51999000003"), sesion(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Nombre solo A"))
                .andExpect(jsonPath("$.telefono").value("51999000003"));
        assertThat(huella(persona)).isEqualTo(usuarioAntes);
        var despuesB = enNegocio(b, () -> jdbc.queryForMap("select * from cliente where id=?", otra.getId()));
        assertThat(despuesB).isEqualTo(fichaAntesB);
        for (long ajeno : java.util.List.of(otra.getId(), 999999L)) {
            mvc.perform(get("/api/clientes/" + ajeno).cookie(sesion(actor))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
            mvc.perform(csrf(editar(ajeno, "No permitido", "999000001"), sesion(actor))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
        assertThat(huella(persona)).isEqualTo(usuarioAntes);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void patch_sesionValidaSinCsrfOConCsrfIncorrecto403_sinCambios(boolean incorrecto) throws Exception {
        var actor = propietario("titular");
        var cliente = persona("local", Rol.CLIENTE);
        var local = ficha(1L, cliente);
        var antes = sql.queryForMap("select * from cliente where id=?", local.getId());
        var solicitud = editar(local.getId(), "Cambio rechazado", "999000002").cookie(sesion(actor));
        if (incorrecto) solicitud.cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "valido"))
                .header("X-XSRF-TOKEN", "incorrecto");
        mvc.perform(solicitud).andExpect(status().isForbidden());
        assertThat(sql.queryForMap("select * from cliente where id=?", local.getId())).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"CLIENTE", "BARBERO", "PLATAFORMA"})
    void rolSinGestion_getYPatch403_aunqueLaSesionYCsrfSeanValidos(String rol) throws Exception {
        var persona = persona("actor", Rol.CLIENTE);
        var local = ficha(1L, persona);
        if (rol.equals("BARBERO")) pertenencia(1L, persona, RolNegocio.BARBERO);
        if (rol.equals("PLATAFORMA")) {
            persona.habilitarAdministracionPlataforma(); usuarios.saveAndFlush(persona);
            local.desactivar(reloj.instant()); clientes.saveAndFlush(local);
        }
        var antes = huella(persona);
        mvc.perform(get("/api/auth/sesion").cookie(sesion(persona))).andExpect(status().isOk());
        mvc.perform(get("/api/clientes/" + local.getId()).cookie(sesion(persona))).andExpect(status().isForbidden());
        mvc.perform(csrf(editar(local.getId(), "Cambio rechazado", "999000002"), sesion(persona)))
                .andExpect(status().isForbidden());
        assertThat(huella(persona)).isEqualTo(antes);
        var principal = auth.autenticar(persona.getId(), 0).orElseThrow();
        assertThatThrownBy(() -> fichas.editar(local.getId(), new pe.barberturno.customers.dto.EditarClienteDto("Nuevo", null), principal))
                .isInstanceOfSatisfying(pe.barberturno.common.error.NegocioException.class,
                        e -> assertThat(e.codigo()).isEqualTo(pe.barberturno.common.error.ErrorCodigo.PROHIBIDO));
    }

    @Test void registro_nuevaCuentaConFicha_correoExistenteEnB409SinFichaEnA() throws Exception {
        long b = negocioB();
        var existente = persona("existente", Rol.CLIENTE);
        ficha(b, existente);
        var antes = huella(existente);
        mvc.perform(csrf(registro(existente.getCorreo()), null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
        assertThat(clientes.findByUsuarioId(existente.getId())).isEmpty();
        assertThat(huella(existente)).isEqualTo(antes);
        var respuesta = mvc.perform(csrf(registro("nueva@ejemplo.test"), null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("CLIENTE")).andReturn().getResponse();
        var nueva = usuarios.findByCorreo("nueva@ejemplo.test").orElseThrow();
        assertThat(clientes.findByUsuarioId(nueva.getId())).get().satisfies(c -> {
            assertThat(c.getNombre()).isEqualTo(nueva.getNombre());
            assertThat(c.getTelefono()).isEqualTo(nueva.getTelefono());
            assertThat(c.getPrivacidadAceptadaEn()).isEqualTo(nueva.getPrivacidadAceptadaEn());
        });
        mvc.perform(get("/api/auth/sesion").cookie(respuesta.getCookie("BT_SESION")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("CLIENTE"));
        assertThat(enNegocio(b, () -> jdbc.queryForObject("select count(*) from cliente where usuario_id=?", Integer.class, existente.getId()))).isEqualTo(1);
    }

    @Test void perfilPropio_actualizaSoloLaCuenta_sinCambiarNingunaFicha() throws Exception {
        long b = negocioB();
        var persona = persona("perfil-propio", Rol.CLIENTE);
        var a = ficha(1L, persona);
        var otra = ficha(b, persona);
        var antesA = sql.queryForMap("select * from cliente where id=?", a.getId());
        var antesB = enNegocio(b, () -> jdbc.queryForMap("select * from cliente where id=?", otra.getId()));
        mvc.perform(csrf(put("/api/perfil").contentType("application/json")
                .content("{\"nombre\":\"Identidad propia nueva\",\"telefono\":\"999000004\"}"), sesion(persona)))
                .andExpect(status().isOk());
        assertThat(usuarios.findById(persona.getId()).orElseThrow().getNombre()).isEqualTo("Identidad propia nueva");
        assertThat(sql.queryForMap("select * from cliente where id=?", a.getId())).isEqualTo(antesA);
        var despuesB = enNegocio(b, () -> jdbc.queryForMap("select * from cliente where id=?", otra.getId()));
        assertThat(despuesB).isEqualTo(antesB);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder editar(long id, String nombre, String telefono) {
        return patch("/api/clientes/" + id).contentType("application/json")
                .content("{\"nombre\":\"" + nombre + "\",\"telefono\":\"" + telefono + "\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registro(String correo) {
        return post("/api/auth/registro").contentType("application/json").content("""
                {"nombre":"Cuenta nueva ficticia","correo":"%s","telefono":"999000005",
                 "password":"ClaveFicticia123","aceptaPrivacidad":true}
                """.formatted(correo));
    }

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
