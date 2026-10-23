package pe.barberturno.common.tenancy;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.barberturno.platform.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.scheduling.dto.CrearBarberoDto;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MembresiaIT extends IdentidadPruebaBase {
    @org.springframework.beans.factory.annotation.Autowired pe.barberturno.common.security.UsuarioJwtConverter converter;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Test void mismaIdentidad_propietarioEnAYBarberoEnB_revalidaCadaPeticionYReservaAjena404() throws Exception {
        long b = negocioB();
        var persona = propietario("compartida");
        pertenencia(b, persona, RolNegocio.BARBERO);
        var datosB = enNegocio(b, () -> DatosNegocioPrueba.crear(jdbc, "membresias"));
        var propio = enNegocio(b, () -> {
            var perfil = barberos.saveAndFlush(new Barbero(persona, "Corte", reloj.instant()));
            return DatosNegocioPrueba.reservar(jdbc, datosB.cliente(), persona.getId(), perfil.getId(),
                    datosB.servicio(), "CONFIRMADA", 60);
        });
        Cookie cookie = sesion(persona); // Mismo JWT, incluido su claim ADMIN heredado.
        mvc.perform(get("/api/usuarios").cookie(cookie)).andExpect(status().isOk());
        assertThat(auth.autenticar(persona.getId(), 0).orElseThrow().rolNegocio()).contains(RolNegocio.PROPIETARIO);
        proveedor.fijar(b);
        try {
            mvc.perform(get("/api/usuarios").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/api/reservas").cookie(cookie).param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(propio))
                    .andExpect(jsonPath("$.contenido[0].permisos.cancelar").value(false))
                    .andExpect(jsonPath("$.contenido[0].permisos.reprogramar").value(false));
            mvc.perform(get("/api/reservas/" + propio).cookie(cookie)).andExpect(status().isOk());
            mvc.perform(get("/api/reservas/" + datosB.reserva()).cookie(cookie)).andExpect(status().isNotFound());
            assertThat(auth.autenticar(persona.getId(), 0).orElseThrow().rolNegocio()).contains(RolNegocio.BARBERO);
        } finally { proveedor.reiniciar(); }
    }

    @Test void plataformaSinMembresia_noDaPermisosDeNegocio_conMembresiaSoloLosDeEsta() throws Exception {
        var plataforma = persona("plataforma", Rol.ADMIN);
        plataforma.habilitarAdministracionPlataforma();
        usuarios.saveAndFlush(plataforma);
        var cookie = sesion(plataforma);
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").isEmpty());
        for (String ruta : List.of("/api/usuarios", "/api/reservas", "/api/reportes/resumen")) {
            mvc.perform(get(ruta).cookie(cookie)).andExpect(status().isForbidden());
        }
        mvc.perform(csrf(post("/api/servicios").contentType("application/json").content("{}"), cookie))
                .andExpect(status().isForbidden());
        var principal = auth.autenticar(plataforma.getId(), 0).orElseThrow();
        assertThat(principal.rol()).isNull();
        assertThat(principal.rolPlataforma()).isEqualTo(RolPlataforma.ADMIN_PLATAFORMA);
        assertThat(converter.convert(decoder.decode(cookie.getValue())).getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN_PLATAFORMA");
        pertenencia(1L, plataforma, RolNegocio.BARBERO);
        mvc.perform(get("/api/usuarios").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").value("BARBERO"));
    }

    @Test void revocarMembresia_peticionSiguienteSinCambiarTokenPierdeAcceso() throws Exception {
        var usuario = propietario("revocable");
        var cookie = sesion(usuario);
        mvc.perform(get("/api/usuarios").cookie(cookie)).andExpect(status().isOk());
        var anterior = huella(usuario);
        var pertenencia = membresias.findByNegocioIdAndUsuarioId(1L, usuario.getId()).orElseThrow();
        pertenencia.revocar(reloj.instant());
        membresias.save(pertenencia);
        mvc.perform(get("/api/usuarios").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/sesion").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.rol").isEmpty());
        assertThat(huella(usuario)).isEqualTo(anterior);
    }

    @Test void contextoA_serviciosNoDevuelvenRolesNiPerfilesDeMembresiasB() throws Exception {
        long b = negocioB();
        var usuario = persona("solo-b", Rol.BARBERO);
        pertenencia(b, usuario, RolNegocio.PROPIETARIO);
        enNegocio(b, () -> barberos.saveAndFlush(new Barbero(usuario, "Corte", reloj.instant())));
        assertThat(auth.autenticar(usuario.getId(), 0).orElseThrow()).satisfies(p -> {
            assertThat(p.rolNegocio()).isEmpty(); assertThat(p.rol()).isNull(); assertThat(p.barberoId()).isEmpty();
        });
        assertThat(auth.sesion(usuario).rol()).isNull();
        assertThat(auth.sesion(usuario.getId()).barberoId()).isNull();
        var admin = propietario("admin-a");
        var listado = gestion.listar("solo-b", null, 0, 20, auth.autenticar(admin.getId(), 0).orElseThrow());
        assertThat(listado.contenido()).singleElement().satisfies(u -> {
            assertThat(u.rol()).isNull(); assertThat(u.barberoId()).isNull();
        });
        assertThat(profesionales.listar(true, true)).isEmpty();
        mvc.perform(csrf(post("/api/barberos").contentType("application/json")
                .content("{\"usuarioId\":" + usuario.getId() + ",\"especialidad\":\"Corte\"}"), sesion(admin))).andExpect(status().isNotFound());
    }

    @Test void altaNueva_creaMembresiaBarbero_yVinculoExigeGestorActivoDelNegocio() throws Exception {
        var creada = profesionales.crear(new CrearBarberoDto(null, "Profesional ficticio", "nuevo@ejemplo.test",
                null, "Corte", "Temporal123"));
        var nuevo = usuarios.findByCorreo("nuevo@ejemplo.test").orElseThrow();
        assertThat(membresias.findByNegocioIdAndUsuarioId(1L, nuevo.getId())).get()
                .extracting(Membresia::getRol).isEqualTo(RolNegocio.BARBERO);
        assertThat(creada.id()).isPositive();
        var encargado = persona("encargado-vinculable", Rol.CLIENTE);
        pertenencia(1L, encargado, RolNegocio.ENCARGADO);
        assertThat(profesionales.crear(new CrearBarberoDto(encargado.getId(), null, null, null, "Corte", null)).id()).isPositive();
    }

    @Test void arranque_completaCuentaExistenteSinCambiarCredencialesYNoDuplicaPertenencias() {
        var existente = persona("inicial", Rol.ADMIN);
        var previa = pertenencia(1L, existente, RolNegocio.BARBERO);
        previa.revocar(reloj.instant()); membresias.save(previa);
        var anterior = huella(existente);
        auth.crearAdminInicial(existente.getCorreo(), "ClaveInicial123", "Inicial");
        auth.crearAdminInicial(existente.getCorreo(), "ClaveInicial123", "Inicial");
        var actual = usuarios.findById(existente.getId()).orElseThrow();
        assertThat(actual.getPasswordHash()).isEqualTo(anterior.get("password_hash"));
        assertThat(actual.getRolPlataforma()).isEqualTo(RolPlataforma.ADMIN_PLATAFORMA);
        assertThat(membresias.contarPropietariosActivos(1L)).isEqualTo(1);
        assertThat(membresias.findByNegocioIdAndUsuarioId(1L, existente.getId()).orElseThrow().getId()).isEqualTo(previa.getId());
    }

    @Test void encargado_listaSoloBarberosYClientes_yObjetivosGestores404() throws Exception {
        var propietario = propietario("titular");
        var encargado = persona("encargado", Rol.BARBERO);
        pertenencia(1L, encargado, RolNegocio.ENCARGADO);
        var cliente = persona("cliente", Rol.CLIENTE);
        var barbero = persona("profesional", Rol.ADMIN);
        pertenencia(1L, barbero, RolNegocio.BARBERO);
        var revocado = propietario("titular-revocado");
        var previa = membresias.findByNegocioIdAndUsuarioId(1L, revocado.getId()).orElseThrow();
        previa.revocar(reloj.instant()); membresias.save(previa);
        var cookie = sesion(encargado);
        mvc.perform(get("/api/usuarios").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        for (var objetivo : List.of(propietario, encargado, revocado)) {
            mvc.perform(csrf(post("/api/usuarios/" + objetivo.getId() + "/restablecer-password"), cookie))
                    .andExpect(status().isNotFound());
            mvc.perform(csrf(estado(objetivo.getId(), false), cookie)).andExpect(status().isNotFound());
        }
        for (var objetivo : List.of(cliente, barbero)) {
            mvc.perform(csrf(post("/api/usuarios/" + objetivo.getId() + "/restablecer-password"), cookie))
                    .andExpect(status().isOk());
            mvc.perform(csrf(estado(objetivo.getId(), false), cookie)).andExpect(status().isOk());
        }
    }
}
