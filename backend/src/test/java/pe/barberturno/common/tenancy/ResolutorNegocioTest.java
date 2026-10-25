package pe.barberturno.common.tenancy;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"/api/n/a/%70erfil", "/api/%70erfil", "/api/%6E/a/servicios",
            "/api/n/%61/servicios", "/api/n/a/%61uth/password", "/api/servicios%", "/api/servicios%zz"})
    void porcentaje_rechazaAntesDeConsultarElDirectorioOSeguirLaCadena(String ruta) throws Exception {
        var request = new MockHttpServletRequest("PUT", ruta);
        var previo = ServletRequestPathUtils.parseAndCache(new MockHttpServletRequest("GET", "/api/perfil"));
        ServletRequestPathUtils.setParsedRequestPath(previo, request);
        var response = new MockHttpServletResponse();
        filtro().doFilter(request, response, (req, res) -> fail("Una ruta codificada no continúa"));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(new JsonMapper().readTree(response.getContentAsString()).get("codigo").asString()).isEqualTo("VALIDACION");
        verifyNoInteractions(directorio);
        assertThat(contexto.negocioActual()).isEmpty();
        assertThat(contexto.delimitado()).isFalse();
        assertThat(ServletRequestPathUtils.getParsedRequestPath(request)).isSameAs(previo);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/perfil", "/api/servicios"})
    void globalYAlias_gettersCoincidenConRutaMvc_yPorcentajeEnConsultaNoSeRechaza(String ruta) throws Exception {
        entorno.setProperty("barberturno.tenancy.alias-legado.slug", "a");
        when(directorio.buscar("a")).thenReturn(OptionalLong.of(2));
        when(directorio.buscarActivo("a")).thenReturn(OptionalLong.of(2));
        var request = new MockHttpServletRequest("GET", "/barberturno" + ruta);
        request.setContextPath("/barberturno");
        request.setQueryString("busqueda=%70erfil");
        filtro().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var efectiva = (jakarta.servlet.http.HttpServletRequest) req;
            assertThat(efectiva.getRequestURI()).isEqualTo("/barberturno" + ruta);
            assertThat(efectiva.getServletPath()).isEqualTo(ruta);
            assertThat(efectiva.getPathInfo()).isNull();
            assertThat(ServletRequestPathUtils.getParsedRequestPath(efectiva).pathWithinApplication().value()).isEqualTo(ruta);
            if (ruta.equals("/api/perfil")) assertThat(contexto.negocioActual()).isEmpty();
            else assertThat(contexto.negocioActual()).hasValue(2);
        });
        assertThat(contexto.delimitado()).isFalse();
    }

    @Test void prefijo_localizaLocation_yConservaLasDemasCabeceras() throws Exception {
        when(directorio.buscar("a")).thenReturn(OptionalLong.of(2));
        var respuesta = new MockHttpServletResponse();
        filtro().doFilter(new MockHttpServletRequest("POST", "/api/n/a/reservas"), respuesta, (req, res) -> {
            var salida = (jakarta.servlet.http.HttpServletResponse) res;
            salida.setHeader("Location", "/api/reservas/1");
            salida.addHeader("Set-Cookie", "ficticia=sin-secreto");
        });
        assertThat(respuesta.getHeader("Location")).isEqualTo("/api/n/a/reservas/1");
        assertThat(respuesta.getHeader("Set-Cookie")).isEqualTo("ficticia=sin-secreto");
        assertThat(contexto.delimitado()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"", "/barberturno"})
    void prefijo_recalculaLosTresGettersYRequestPath_yLimpiaInclusoConError(String contextPath) {
        var request = new MockHttpServletRequest("GET", contextPath + "/api/n/a/servicios");
        request.setContextPath(contextPath);
        request.setServletPath("/api/n/a/servicios");
        var previo = ServletRequestPathUtils.parseAndCache(request);
        when(directorio.buscarActivo("a")).thenReturn(OptionalLong.of(2));
        assertThatThrownBy(() -> filtro().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var efectiva = (jakarta.servlet.http.HttpServletRequest) req;
            assertThat(efectiva.getRequestURI()).isEqualTo(contextPath + "/api/servicios");
            assertThat(efectiva.getServletPath()).isEqualTo("/api/servicios");
            assertThat(efectiva.getPathInfo()).isNull();
            assertThat(ServletRequestPathUtils.getParsedRequestPath(efectiva).pathWithinApplication().value()).isEqualTo("/api/servicios");
            assertThat(contexto.negocioActual()).hasValue(2);
            throw new IllegalStateException("Fallo simulado");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Fallo simulado");
        assertThat(contexto.negocioActual()).isEmpty();
        assertThat(contexto.delimitado()).isFalse();
        assertThat(ServletRequestPathUtils.getParsedRequestPath(request)).isSameAs(previo);
    }

    @Test void sinAlias_localFallaPeroGlobalContinuaSinContexto() throws Exception {
        when(directorio.buscarActivo(null)).thenReturn(OptionalLong.empty());
        var respuesta = new MockHttpServletResponse();
        filtro().doFilter(new MockHttpServletRequest("GET", "/api/servicios"), respuesta,
                (req, res) -> fail("No debe continuar sin negocio"));
        assertThat(respuesta.getStatus()).isEqualTo(404);
        filtro().doFilter(new MockHttpServletRequest("GET", "/api/perfil"), new MockHttpServletResponse(),
                (req, res) -> { assertThat(contexto.negocioActual()).isEmpty(); assertThat(contexto.delimitado()).isTrue(); });
        assertThat(contexto.delimitado()).isFalse();
    }

    @Test void plataformaGlobal_sinAliasNiConsultaDeNegocio_yBajoPrefijo404() throws Exception {
        filtro().doFilter(new MockHttpServletRequest("GET", "/api/plataforma/negocios"), new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(contexto.delimitado()).isTrue();
                    assertThat(contexto.negocioActual()).isEmpty();
                    assertThat(((jakarta.servlet.http.HttpServletRequest) req).getServletPath()).isEqualTo("/api/plataforma/negocios");
                });
        var respuesta = new MockHttpServletResponse();
        filtro().doFilter(new MockHttpServletRequest("POST", "/api/n/a/plataforma/negocios"), respuesta,
                (req, res) -> fail("Plataforma no entra por un negocio"));
        assertThat(respuesta.getStatus()).isEqualTo(404);
        verifyNoInteractions(directorio);
        assertThat(contexto.delimitado()).isFalse();
    }
}
