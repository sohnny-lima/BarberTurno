package pe.barberturno.common.tenancy;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.web.util.ServletRequestPathUtils;
import pe.barberturno.common.security.RespuestaSeguridad;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResolutorNegocioTest {
    final ContextoNegocio contexto = new ContextoNegocio();
    final DirectorioNegocios directorio = mock(DirectorioNegocios.class);
    final MockEnvironment entorno = new MockEnvironment();

    ResolutorNegocio filtro() {
        return new ResolutorNegocio(contexto, directorio, new RespuestaSeguridad(new JsonMapper()), entorno);
    }

    @Test void prefijo_recalculaLosTresGettersYRequestPath_yLimpiaInclusoConError() {
        var request = new MockHttpServletRequest("GET", "/api/n/a/servicios");
        request.setServletPath("/api/n/a/servicios");
        var previo = ServletRequestPathUtils.parseAndCache(request);
        when(directorio.buscar("a")).thenReturn(OptionalLong.of(2));
        assertThatThrownBy(() -> filtro().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var efectiva = (jakarta.servlet.http.HttpServletRequest) req;
            assertThat(efectiva.getRequestURI()).isEqualTo("/api/servicios");
            assertThat(efectiva.getServletPath()).isEqualTo("/api/servicios");
            assertThat(efectiva.getPathInfo()).isNull();
            assertThat(ServletRequestPathUtils.getParsedRequestPath(efectiva).value()).isEqualTo("/api/servicios");
            assertThat(contexto.negocioActual()).hasValue(2);
            throw new IllegalStateException("Fallo simulado");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Fallo simulado");
        assertThat(contexto.negocioActual()).isEmpty();
        assertThat(contexto.delimitado()).isFalse();
        assertThat(ServletRequestPathUtils.getParsedRequestPath(request)).isSameAs(previo);
    }

    @Test void sinAlias_localFallaPeroGlobalContinuaSinContexto() throws Exception {
        when(directorio.buscar(null)).thenReturn(OptionalLong.empty());
        var respuesta = new MockHttpServletResponse();
        filtro().doFilter(new MockHttpServletRequest("GET", "/api/servicios"), respuesta,
                (req, res) -> fail("No debe continuar sin negocio"));
        assertThat(respuesta.getStatus()).isEqualTo(404);
        filtro().doFilter(new MockHttpServletRequest("GET", "/api/perfil"), new MockHttpServletResponse(),
                (req, res) -> { assertThat(contexto.negocioActual()).isEmpty(); assertThat(contexto.delimitado()).isTrue(); });
        assertThat(contexto.delimitado()).isFalse();
    }
}
