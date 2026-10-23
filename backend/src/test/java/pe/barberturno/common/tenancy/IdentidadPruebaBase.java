package pe.barberturno.common.tenancy;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.auth.*;
import pe.barberturno.platform.*;
import pe.barberturno.scheduling.*;
import pe.barberturno.users.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Fixtures explícitos con el rol real; el negocio B se prepara únicamente mediante negocioB(). */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
abstract class IdentidadPruebaBase extends TenancyPruebaBase {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired BarberoRepository barberos;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired UsuarioAdminService gestion;
    @Autowired BarberoService profesionales;

    Usuario persona(String alias, Rol legado) {
        return usuarios.saveAndFlush(new Usuario("Persona " + alias, alias + "@ejemplo.test", "999000001",
                "hash-ficticio-no-utilizable", legado, reloj.instant(), reloj.instant()));
    }

    Membresia pertenencia(long negocioId, Usuario usuario, RolNegocio rol) {
        return membresias.save(new Membresia(negocioId, usuario.getId(), rol, reloj.instant()));
    }

    Usuario propietario(String alias) {
        var usuario = persona(alias, Rol.ADMIN);
        pertenencia(1L, usuario, RolNegocio.PROPIETARIO);
        return usuario;
    }

    Cookie sesion(Usuario usuario) { return new Cookie("BT_SESION", jwt.emitir(usuario)); }

    MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder solicitud, Cookie sesion) throws Exception {
        var token = mvc.perform(get("/api/auth/sesion")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return solicitud.cookie(sesion, token).header("X-XSRF-TOKEN", token.getValue());
    }

    MockHttpServletRequestBuilder estado(long id, boolean activo) {
        return patch("/api/usuarios/" + id + "/estado").contentType("application/json")
                .content("{\"activo\":" + activo + "}");
    }

    Map<String, Object> huella(Usuario usuario) {
        return sql.queryForMap("select * from usuario where id=?", usuario.getId());
    }
}
