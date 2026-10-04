package pe.barberturno.reservations;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.dto.*;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = {"barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=", "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class UsuarioAdminIT extends ReservaPruebaBase {
    @Autowired PasswordEncoder passwords;
    @Autowired UsuarioAdminService gestion;
    Usuario admin;
    @BeforeEach void administrador() { admin = admin("primero"); }
    Usuario admin(String nombre) { return usuarios.saveAndFlush(new Usuario("ADMIN " + nombre, nombre + "@ejemplo.test", null, passwords.encode("ClaveFicticia123"), Rol.ADMIN, null, reloj.instant())); }
    String estado(long id) { return "/api/usuarios/" + id + "/estado"; }
    String reset(long id) { return "/api/usuarios/" + id + "/restablecer-password"; }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cambio(long id, boolean activo) { return patch(estado(id)).contentType("application/json").content(json.writeValueAsString(Map.of("activo", activo))); }
    @Test void buscaNombreCorreoSinMayusculas_filtraRolPaginaYSinDatosInternos() throws Exception {
        mvc.perform(get("/api/usuarios").param("q", "CLIENTE FICT").param("rol", "CLIENTE").param("tamano", "1").cookie(sesion(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].id").value(cliente.getId())).andExpect(jsonPath("$.contenido[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.contenido[0].tokenVersion").doesNotExist()).andExpect(jsonPath("$.contenido[0].intentosFallidos").doesNotExist()).andExpect(jsonPath("$.contenido[0].bloqueadoHasta").doesNotExist());
        mvc.perform(get("/api/usuarios").param("q", "PRIMERO@EJEMPLO").cookie(sesion(admin))).andExpect(jsonPath("$.contenido[0].id").value(admin.getId()));
        mvc.perform(get("/api/usuarios").param("q", "%").cookie(sesion(admin))).andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(get("/api/usuarios").param("rol", "BARBERO").cookie(sesion(admin))).andExpect(jsonPath("$.contenido[0].barberoId").value(barbero.getId()));
        mvc.perform(get("/api/usuarios").param("pagina", "1").param("tamano", "1").cookie(sesion(admin))).andExpect(jsonPath("$.pagina").value(1)).andExpect(jsonPath("$.totalElementos").value(3));
    }
    @ParameterizedTest @ValueSource(strings = {"pagina=-1", "tamano=0", "tamano=101", "rol=OTRO"}) void consultaInvalida400(String parametro) throws Exception {
        var par=parametro.split("="); mvc.perform(get("/api/usuarios").param(par[0],par[1]).cookie(sesion(admin))).andExpect(status().isBadRequest());
    }
    @Test void cp17_temporalRevocaSesionAnterior_exigeCambioYDesbloqueaAcceso() throws Exception {
        var anterior=sesion(cliente); cliente.registrarIntentoFallido(reloj.instant().plusSeconds(900), reloj.instant()); usuarios.saveAndFlush(cliente);
        var r=mvc.perform(conCsrf(post(reset(cliente.getId())), sesion(admin))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        var temporal=json.readTree(r.getResponse().getContentAsString()).get("passwordTemporal").asString();
        assertThat(temporal).hasSize(12).matches(".*[A-Za-z].*").matches(".*[0-9].*");
        var actualizado=usuarios.findById(cliente.getId()).orElseThrow(); assertThat(passwords.matches(temporal,actualizado.getPasswordHash())).isTrue(); assertThat(actualizado.isDebeCambiarPassword()).isTrue(); assertThat(actualizado.getTokenVersion()).isEqualTo(cliente.getTokenVersion()+1); assertThat(actualizado.getBloqueadoHasta()).isNull();
        mvc.perform(get("/api/auth/sesion").cookie(anterior)).andExpect(status().isUnauthorized());
        var login=mvc.perform(conCsrf(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("correo",cliente.getCorreo(),"password",temporal))),sesion(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarPassword").value(true)).andReturn();
        mvc.perform(get("/api/notificaciones").cookie(login.getResponse().getCookie("BT_SESION"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
        assertThat(new pe.barberturno.users.dto.PasswordTemporalDto(temporal).toString()).doesNotContain(temporal);
    }
    @Test void desactivarRevoca_reactivarNoResucitaSesion_idempotente() throws Exception {
        var anterior=sesion(cliente); for(int i=0;i<2;i++) mvc.perform(conCsrf(cambio(cliente.getId(),false),sesion(admin))).andExpect(status().isOk());
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getTokenVersion()).isEqualTo(1);
        mvc.perform(get("/api/auth/sesion").cookie(anterior)).andExpect(status().isUnauthorized());
        mvc.perform(conCsrf(cambio(cliente.getId(),true),sesion(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/sesion").cookie(anterior)).andExpect(status().isUnauthorized());
    }
    @Test void ultimoAdmin_yPropiaCuenta_noSeDesactivan409() throws Exception {
        mvc.perform(conCsrf(cambio(admin.getId(),false),sesion(admin))).andExpect(status().isConflict()); assertThat(usuarios.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(1);
        admin("segundo"); mvc.perform(conCsrf(cambio(admin.getId(),false),sesion(admin))).andExpect(status().isConflict()); assertThat(usuarios.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(2);
    }
    @RepeatedTest(10) void dosAdmin_desactivacionCruzada_exactamenteUn200_yQuedaUnoActivo() throws Exception {
        var otro=admin("segundo"); var csrf=mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN"); var cookieUno=sesion(admin); var cookieDos=sesion(otro); var salida=new CyclicBarrier(2);
        try(var hilos=Executors.newFixedThreadPool(2)) {
            var uno=hilos.submit(()->{salida.await(10,TimeUnit.SECONDS);return mvc.perform(cambio(otro.getId(),false).cookie(cookieUno,csrf).header("X-XSRF-TOKEN",csrf.getValue())).andReturn().getResponse().getStatus();});
            var dos=hilos.submit(()->{salida.await(10,TimeUnit.SECONDS);return mvc.perform(cambio(admin.getId(),false).cookie(cookieDos,csrf).header("X-XSRF-TOKEN",csrf.getValue())).andReturn().getResponse().getStatus();});
            var resultados=List.of(uno.get(20,TimeUnit.SECONDS),dos.get(20,TimeUnit.SECONDS)); assertThat(resultados.stream().filter(c->c==200)).hasSize(1); assertThat(resultados).allMatch(c->c==200||c==401||c==403||c==409);
        }
        assertThat(usuarios.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"reset","estado"}) void escrituras_sesionValidaSinCsrfOCsrfIncorrecto403(String operacion) throws Exception {
        for(boolean incorrecto:List.of(false,true)) {var p=operacion.equals("reset")?post(reset(cliente.getId())):cambio(cliente.getId(),false);p.cookie(sesion(admin));if(incorrecto)p.cookie(new Cookie("XSRF-TOKEN","valido")).header("X-XSRF-TOKEN","incorrecto");mvc.perform(p).andExpect(status().isForbidden());}
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getTokenVersion()).isZero();
    }
    @ParameterizedTest @EnumSource(value=Rol.class,names={"CLIENTE","BARBERO"}) void rolNoAutorizado403_enTodasLasRutas(Rol rol) throws Exception {
        var u=rol==Rol.CLIENTE?cliente:barbero.getUsuario(); mvc.perform(get("/api/usuarios").cookie(sesion(u))).andExpect(status().isForbidden());mvc.perform(conCsrf(post(reset(cliente.getId())),sesion(u))).andExpect(status().isForbidden());mvc.perform(conCsrf(cambio(cliente.getId(),false),sesion(u))).andExpect(status().isForbidden());
        assertThatThrownBy(()->gestion.restablecer(cliente.getId(),actor(u))).isInstanceOf(NegocioException.class);assertThatThrownBy(()->gestion.cambiarEstado(cliente.getId(),false,actor(u))).isInstanceOf(NegocioException.class);
    }
    @Test void inexistente404_estadoOmitido400() throws Exception {
        mvc.perform(conCsrf(post(reset(999999L)),sesion(admin))).andExpect(status().isNotFound());mvc.perform(conCsrf(cambio(999999L,true),sesion(admin))).andExpect(status().isNotFound());mvc.perform(conCsrf(patch(estado(cliente.getId())).contentType("application/json").content("{}"),sesion(admin))).andExpect(status().isBadRequest());
    }
}
