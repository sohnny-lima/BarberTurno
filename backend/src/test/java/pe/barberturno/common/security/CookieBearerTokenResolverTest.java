package pe.barberturno.common.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba los límites exactos de las rutas de recuperación, incluso con contextPath. */
class CookieBearerTokenResolverTest {
    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    @ParameterizedTest @ValueSource(strings = {"login", "registro", "logout"})
    void post_recuperacionConContextPath_ignoraInclusoCookiesAmbiguas(String accion) {
        var peticion = peticion("POST", "/barberturno/api/auth/" + accion);
        peticion.setCookies(new Cookie("BT_SESION", "anterior"), new Cookie("BT_SESION", "otra"));
        assertThat(resolver.resolve(peticion)).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"login", "registro", "logout", "password", "login/otra"})
    void get_oPostFueraDeRecuperacion_noOmiteCookie(String accion) {
        var peticion = peticion("GET", "/barberturno/api/auth/" + accion);
        assertThat(resolver.resolve(peticion)).isEqualTo("anterior");
        if (accion.equals("password") || accion.equals("login/otra")) {
            peticion.setMethod("POST");
            assertThat(resolver.resolve(peticion)).isEqualTo("anterior");
        }
    }

    private MockHttpServletRequest peticion(String metodo, String ruta) {
        var peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setContextPath("/barberturno");
        peticion.setCookies(new Cookie("BT_SESION", "anterior"));
        return peticion;
    }
}