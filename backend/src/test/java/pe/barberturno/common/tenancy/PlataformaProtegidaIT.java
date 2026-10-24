package pe.barberturno.common.tenancy;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.platform.*;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regresión del recorrido de 079: el negocio nunca obtiene credenciales ni altera la cuenta de plataforma. */
class PlataformaProtegidaIT extends IdentidadPruebaBase {
    @Test void contextoUsaRolAplicacionSinPrivilegiosQueEludanRls() {
        assertThat(sql.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(sql.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user",
                Boolean.class)).isFalse();
    }

    @ParameterizedTest @EnumSource(Rol.class)
    void plataformaSinMembresias_invisibleYEscrituras404_sinEmitirTemporalNiAlterarFila(Rol legado) throws Exception {
        var actor = propietario("titular");
        var plataforma = plataforma("plataforma-aislada", legado);
        assertThat(sql.queryForObject("select count(*) from membresia where usuario_id=?", Integer.class,
                plataforma.getId())).isZero();
        exigirAjena(plataforma, sesion(actor));
    }

    @ParameterizedTest @ValueSource(strings = {"password", "estado", "nombre", "telefono"})
    void plataformaPropietaria_global409YPerfilLocal200_yNingunaColumnaGlobalCambia(String operacion) throws Exception {
        var actor = propietario("titular");
        var plataforma = plataforma("plataforma-titular", Rol.ADMIN);
        pertenencia(1L, plataforma, RolNegocio.PROPIETARIO);
        var perfil = barberos.saveAndFlush(new Barbero(plataforma, "Corte", reloj.instant()));
        var anterior = huella(plataforma);
        mvc.perform(get("/api/usuarios").cookie(sesion(actor)).param("q", plataforma.getCorreo()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(plataforma.getId()));
        var solicitud = switch (operacion) {
            case "password" -> post("/api/usuarios/" + plataforma.getId() + "/restablecer-password");
            case "estado" -> estado(plataforma.getId(), false);
            default -> editar(perfil.getId(), operacion.equals("nombre") ? "Nombre cambiado" : plataforma.getNombre(),
                    operacion.equals("telefono") ? "999000002" : plataforma.getTelefono());
        };
        boolean global = operacion.equals("password") || operacion.equals("estado");
        if (global) {
            mvc.perform(csrf(solicitud, sesion(actor))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("IDENTIDAD_COMPARTIDA"))
                    .andExpect(jsonPath("$.passwordTemporal").doesNotExist());
        } else {
            mvc.perform(csrf(solicitud, sesion(actor))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.passwordTemporal").doesNotExist());
            var local = barberos.findById(perfil.getId()).orElseThrow();
            assertThat(local.getNombreVisible()).isEqualTo(operacion.equals("nombre") ? "Nombre cambiado" : plataforma.getNombre());
            assertThat(local.getTelefonoContacto()).isEqualTo(operacion.equals("telefono") ? "999000002" : plataforma.getTelefono());
        }
        assertThat(huella(plataforma)).isEqualTo(anterior);
        assertThat(barberos.findById(perfil.getId()).orElseThrow().getEspecialidad()).isEqualTo(global ? "Corte" : "Barba");
    }

    @ParameterizedTest @EnumSource(RolNegocio.class)
    void perfilDePlataforma_estadoYEspecialidadSoloLocales_conservanCuentaYSesion(RolNegocio rol) throws Exception {
        var actor = propietario("titular");
        var plataforma = plataforma("plataforma-profesional", Rol.BARBERO);
        pertenencia(1L, plataforma, rol);
        var perfil = barberos.saveAndFlush(new Barbero(plataforma, "Corte", reloj.instant()));
        var anterior = huella(plataforma);
        var cookie = sesion(plataforma);
        for (boolean activo : List.of(false, true)) {
            mvc.perform(csrf(estadoPerfil(perfil.getId(), activo), sesion(actor))).andExpect(status().isOk());
            assertThat(barberos.findById(perfil.getId()).orElseThrow().isActivo()).isEqualTo(activo);
            assertThat(huella(plataforma)).isEqualTo(anterior);
            mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(status().isOk());
        }
        mvc.perform(csrf(editar(perfil.getId(), plataforma.getNombre(), plataforma.getTelefono()), sesion(actor)))
                .andExpect(status().isOk());
        assertThat(barberos.findById(perfil.getId()).orElseThrow().getEspecialidad()).isEqualTo("Barba");
        assertThat(huella(plataforma)).isEqualTo(anterior);
    }

    @ParameterizedTest @EnumSource(RolNegocio.class)
    void personalSoloDeOtroNegocio_invisibleYEscrituras404_incluidoVinculo(RolNegocio rol) throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var ajeno = persona("personal-ajeno", Rol.CLIENTE);
        pertenencia(b, ajeno, rol);
        var perfil = enNegocio(b, () -> barberos.saveAndFlush(new Barbero(ajeno, "Corte", reloj.instant())));
        exigirAjena(ajeno, sesion(actor));
        mvc.perform(csrf(editar(perfil.getId(), "Cambio ajeno", ajeno.getTelefono()), sesion(actor)))
                .andExpect(status().isNotFound());
        mvc.perform(csrf(estadoPerfil(perfil.getId(), false), sesion(actor))).andExpect(status().isNotFound());
        mvc.perform(csrf(post("/api/barberos").contentType("application/json")
                .content("{\"usuarioId\":" + ajeno.getId() + ",\"especialidad\":\"Corte\"}"), sesion(actor)))
                .andExpect(status().isNotFound());
    }

    @Test void cuentaConFichaActiva_soloVisibleYGestionableEnSuNegocio() throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        pertenencia(b, actor, RolNegocio.PROPIETARIO);
        var cliente = persona("cliente-legado", Rol.CLIENTE);
        ficha(1L, cliente);
        var cookie = sesion(actor);
        mvc.perform(get("/api/usuarios").cookie(cookie).param("q", cliente.getCorreo()).param("rol", "CLIENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(cliente.getId()));
        proveedor.fijar(b);
        try { exigirAjena(cliente, cookie, "/api/n/negocio-b"); }
        finally { proveedor.reiniciar(); }
        mvc.perform(csrf(estado(cliente.getId(), false), cookie)).andExpect(status().isOk());
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test void fichaActivaEnB_noDaAmbitoEnA_yFichaLocalInactivaTampoco() throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var cliente = persona("cliente-solo-b", Rol.CLIENTE);
        ficha(b, cliente);
        exigirAjena(cliente, sesion(actor));
        var local = ficha(1L, cliente);
        local.desactivar(reloj.instant()); clientes.saveAndFlush(local);
        exigirAjena(cliente, sesion(actor));
    }

    @Test void plataformaConFichaLocal_sinMembresias_sigueProtegidaContraCambiosGlobales() throws Exception {
        var actor = propietario("titular");
        var plataforma = plataforma("plataforma-cliente", Rol.CLIENTE);
        ficha(1L, plataforma);
        var anterior = huella(plataforma);
        mvc.perform(csrf(post("/api/usuarios/" + plataforma.getId() + "/restablecer-password"), sesion(actor)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("IDENTIDAD_COMPARTIDA"))
                .andExpect(jsonPath("$.passwordTemporal").doesNotExist());
        mvc.perform(csrf(estado(plataforma.getId(), false), sesion(actor))).andExpect(status().isConflict());
        assertThat(huella(plataforma)).isEqualTo(anterior);
    }

    @Test void clienteConMembresiaRevocadaEnOtroNegocio_noEsClienteHeredadoDelUnico() throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var usuario = persona("cliente-con-historial-ajeno", Rol.CLIENTE);
        var membresia = pertenencia(b, usuario, RolNegocio.BARBERO);
        membresia.revocar(reloj.instant());
        membresias.save(membresia);
        exigirAjena(usuario, sesion(actor));
    }

    @ParameterizedTest @EnumSource(RolNegocio.class)
    void membresiaLocalRevocada_noVuelveClienteV1NiPermiteEditarPerfilAntiguo(RolNegocio rol) throws Exception {
        var actor = propietario("titular");
        var usuario = persona("revocado", Rol.CLIENTE);
        var membresia = pertenencia(1L, usuario, rol);
        var perfil = barberos.saveAndFlush(new Barbero(usuario, "Corte", reloj.instant()));
        membresia.revocar(reloj.instant());
        membresias.save(membresia);
        var anterior = huella(usuario);
        exigirAjena(usuario, sesion(actor));
        mvc.perform(csrf(editar(perfil.getId(), "Cambio rechazado", usuario.getTelefono()), sesion(actor)))
                .andExpect(status().isNotFound());
        mvc.perform(csrf(estadoPerfil(perfil.getId(), false), sesion(actor))).andExpect(status().isNotFound());
        mvc.perform(csrf(post("/api/barberos").contentType("application/json")
                .content("{\"usuarioId\":" + usuario.getId() + ",\"especialidad\":\"Corte\"}"), sesion(actor)))
                .andExpect(status().isNotFound());
        assertThat(huella(usuario)).isEqualTo(anterior);
        assertThat(barberos.findById(perfil.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(barberos.findById(perfil.getId()).orElseThrow().getEspecialidad()).isEqualTo("Corte");
    }

    private Usuario plataforma(String alias, Rol legado) {
        var usuario = persona(alias, legado);
        usuario.habilitarAdministracionPlataforma();
        return usuarios.saveAndFlush(usuario);
    }

    private void exigirAjena(Usuario usuario, Cookie cookie) throws Exception { exigirAjena(usuario, cookie, "/api"); }

    private void exigirAjena(Usuario usuario, Cookie cookie, String raiz) throws Exception {
        var anterior = huella(usuario);
        for (String rol : List.of("", "ADMIN", "BARBERO", "CLIENTE")) {
            var completa = get(raiz + "/usuarios").cookie(cookie);
            var solicitud = get(raiz + "/usuarios").cookie(cookie).param("q", usuario.getCorreo());
            if (!rol.isEmpty()) {
                solicitud.param("rol", rol);
                completa.param("rol", rol);
            }
            mvc.perform(completa).andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[?(@.id == " + usuario.getId() + ")]").isEmpty());
            mvc.perform(solicitud).andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido.length()").value(0))
                    .andExpect(jsonPath("$.totalElementos").value(0));
        }
        mvc.perform(csrf(post(raiz + "/usuarios/" + usuario.getId() + "/restablecer-password"), cookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.passwordTemporal").doesNotExist());
        assertThat(huella(usuario)).isEqualTo(anterior);
        mvc.perform(csrf(patch(raiz + "/usuarios/" + usuario.getId() + "/estado").contentType("application/json").content("{\"activo\":false}"), cookie)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        assertThat(huella(usuario)).isEqualTo(anterior);
    }

    private MockHttpServletRequestBuilder editar(long id, String nombre, String telefono) {
        return put("/api/barberos/" + id).contentType("application/json")
                .content("{\"nombre\":\"" + nombre + "\",\"telefono\":\"" + telefono + "\",\"especialidad\":\"Barba\"}");
    }

    private MockHttpServletRequestBuilder estadoPerfil(long id, boolean activo) {
        return patch("/api/barberos/" + id + "/estado").contentType("application/json")
                .content("{\"activo\":" + activo + "}");
    }
}
