package pe.barberturno.common.tenancy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.barberturno.platform.*;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IdentidadCompartidaIT extends IdentidadPruebaBase {
    private static final String MENSAJE = "La cuenta pertenece también a otro negocio; el cambio lo hace la plataforma";

    @ParameterizedTest @ValueSource(strings = {"password", "estado", "nombre", "telefono", "perfil"})
    void fichaEnOtroNegocio_aunInactivaProtegeTodasLasColumnasDeLaCuenta(String operacion) throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var usuario = persona("compartida-por-ficha", Rol.BARBERO);
        pertenencia(1L, usuario, RolNegocio.BARBERO);
        var perfil = barberos.saveAndFlush(new Barbero(usuario, "Corte", reloj.instant()));
        var fichaB = ficha(b, usuario);
        fichaB.desactivar(reloj.instant());
        enNegocio(b, () -> clientes.saveAndFlush(fichaB));
        var anterior = huella(usuario);
        var solicitud = switch (operacion) {
            case "password" -> post("/api/usuarios/" + usuario.getId() + "/restablecer-password");
            case "estado" -> estado(usuario.getId(), false);
            case "perfil" -> patch("/api/barberos/" + perfil.getId() + "/estado").contentType("application/json")
                    .content("{\"activo\":false}");
            default -> put("/api/barberos/" + perfil.getId()).contentType("application/json")
                    .content("{\"nombre\":\"" + (operacion.equals("nombre") ? "Nuevo" : usuario.getNombre())
                            + "\",\"telefono\":\"" + (operacion.equals("telefono") ? "999000002" : usuario.getTelefono())
                            + "\",\"especialidad\":\"Barba\"}");
        };
        if (operacion.equals("perfil")) {
            mvc.perform(csrf(solicitud, sesion(actor))).andExpect(status().isOk());
            assertThat(barberos.findById(perfil.getId()).orElseThrow().isActivo()).isFalse();
        } else {
            mvc.perform(csrf(solicitud, sesion(actor))).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("IDENTIDAD_COMPARTIDA"))
                    .andExpect(jsonPath("$.passwordTemporal").doesNotExist());
        }
        assertThat(huella(usuario)).isEqualTo(anterior);
    }

    @ParameterizedTest @ValueSource(strings = {"password", "estado", "nombre", "telefono"})
    void identidadCompartida_rechaza409SinModificarNingunaColumnaGlobal(String operacion) throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var usuario = persona("compartida", Rol.BARBERO);
        pertenencia(1L, usuario, RolNegocio.BARBERO);
        pertenencia(b, usuario, RolNegocio.PROPIETARIO);
        var perfil = barberos.saveAndFlush(new Barbero(usuario, "Corte", reloj.instant()));
        var anterior = huella(usuario);
        var solicitud = switch (operacion) {
            case "password" -> post("/api/usuarios/" + usuario.getId() + "/restablecer-password");
            case "estado" -> estado(usuario.getId(), false);
            default -> put("/api/barberos/" + perfil.getId()).contentType("application/json")
                    .content("{\"nombre\":\"" + (operacion.equals("nombre") ? "Nombre nuevo" : usuario.getNombre())
                            + "\",\"telefono\":\"" + (operacion.equals("telefono") ? "999000002" : usuario.getTelefono())
                            + "\",\"especialidad\":\"Barba\"}");
        };
        mvc.perform(csrf(solicitud, sesion(actor))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("IDENTIDAD_COMPARTIDA"))
                .andExpect(jsonPath("$.detail").value(MENSAJE));
        assertThat(huella(usuario)).isEqualTo(anterior);
        assertThat(barberos.findById(perfil.getId()).orElseThrow().getEspecialidad()).isEqualTo("Corte");
    }

    @Test void perfilCompartido_desactivacionYReactivacionSoloLocales_especialidadEditable() throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var usuario = persona("compartida", Rol.BARBERO);
        pertenencia(1L, usuario, RolNegocio.BARBERO);
        pertenencia(b, usuario, RolNegocio.BARBERO);
        var perfil = barberos.saveAndFlush(new Barbero(usuario, "Corte", reloj.instant()));
        var anterior = huella(usuario);
        var cookie = sesion(usuario);
        for (boolean activo : List.of(false, true)) {
            mvc.perform(csrf(patch("/api/barberos/" + perfil.getId() + "/estado").contentType("application/json")
                    .content("{\"activo\":" + activo + "}"), sesion(actor))).andExpect(status().isOk());
            assertThat(barberos.findById(perfil.getId()).orElseThrow().isActivo()).isEqualTo(activo);
            assertThat(huella(usuario)).isEqualTo(anterior);
            mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(status().isOk());
        }
        mvc.perform(csrf(put("/api/barberos/" + perfil.getId()).contentType("application/json")
                .content("{\"nombre\":\"" + usuario.getNombre() + "\",\"telefono\":\"" + usuario.getTelefono()
                        + "\",\"especialidad\":\"Barba\"}"), sesion(actor))).andExpect(status().isOk());
        assertThat(huella(usuario)).isEqualTo(anterior);
        assertThat(barberos.findById(perfil.getId()).orElseThrow().getEspecialidad()).isEqualTo("Barba");
    }

    @Test void personaNoCompartida_conservaRestablecimientoEdicionEstadoYRevocacionV1() throws Exception {
        var actor = propietario("titular");
        var usuario = persona("local", Rol.BARBERO);
        pertenencia(1L, usuario, RolNegocio.BARBERO);
        var perfil = barberos.saveAndFlush(new Barbero(usuario, "Corte", reloj.instant()));
        var cookie = sesion(actor);
        mvc.perform(csrf(post("/api/usuarios/" + usuario.getId() + "/restablecer-password"), cookie)).andExpect(status().isOk());
        mvc.perform(csrf(estado(usuario.getId(), false), cookie)).andExpect(status().isOk());
        mvc.perform(csrf(estado(usuario.getId(), true), cookie)).andExpect(status().isOk());
        mvc.perform(csrf(put("/api/barberos/" + perfil.getId()).contentType("application/json")
                .content("{\"nombre\":\"Nuevo nombre\",\"telefono\":\"999000002\",\"especialidad\":\"Barba\"}"), cookie))
                .andExpect(status().isOk());
        assertThat(usuarios.findById(usuario.getId()).orElseThrow().getNombre()).isEqualTo("Nuevo nombre");
        mvc.perform(csrf(patch("/api/barberos/" + perfil.getId() + "/estado").contentType("application/json")
                .content("{\"activo\":false}"), cookie)).andExpect(status().isOk());
        assertThat(usuarios.findById(usuario.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test void pertenenciaRevocadaEnOtroNegocio_noHaceCompartidaALaPersona() throws Exception {
        long b = negocioB();
        var actor = propietario("titular");
        var usuario = persona("local", Rol.BARBERO);
        pertenencia(1L, usuario, RolNegocio.BARBERO);
        var otra = pertenencia(b, usuario, RolNegocio.BARBERO);
        otra.revocar(reloj.instant());
        membresias.save(otra);
        mvc.perform(csrf(estado(usuario.getId(), false), sesion(actor))).andExpect(status().isOk());
    }
}
