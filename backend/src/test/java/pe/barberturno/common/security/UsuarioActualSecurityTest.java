package pe.barberturno.common.security;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.barberturno.common.error.NegocioException;
import pe.barberturno.users.Rol;
import static org.assertj.core.api.Assertions.*;

class UsuarioActualSecurityTest {
    private final UsuarioActualSecurity actual = new UsuarioActualSecurity();

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test void principal_autenticado_devuelveIdentidadYPerfilOpcional() {
        var usuario = new UsuarioAutenticado(7, Rol.ADMIN, Optional.of(3L), true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of()));
        assertThat(actual.principal()).isEqualTo(usuario);
        assertThat(actual.id()).isEqualTo(7);
        assertThat(actual.rol()).isEqualTo(Rol.ADMIN);
        assertThat(actual.barberoId()).contains(3L);
    }

    @Test void principal_sinSesion_rechaza() {
        assertThatThrownBy(actual::principal).isInstanceOf(NegocioException.class);
    }

    @Test void principal_simuladoONoAutenticado_rechaza() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("simulado", null, List.of()));
        assertThatThrownBy(actual::id).isInstanceOf(NegocioException.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        new UsuarioAutenticado(7, Rol.CLIENTE, Optional.empty(), false), null));
        assertThatThrownBy(actual::id).isInstanceOf(NegocioException.class);
    }
}
