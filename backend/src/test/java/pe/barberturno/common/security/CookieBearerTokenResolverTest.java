package pe.barberturno.common.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba los límites exactos de las rutas de recuperación, incluso con contextPath. */
class CookieBearerTokenResolverTest {
    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver();

    @ParameterizedTest @ValueSource(strings = {"registro", "aceptacion"})
    void invitacion_omiteCookieSoloEnElRegistroPublico(String accion) {
        var peticion = peticion("POST", "/barberturno/api/invitaciones/" + "A".repeat(43) + "/" + accion);
        assertThat(resolver.resolve(peticion)).isEqualTo(accion.equals("registro") ? null : "anterior");
        peticion.setMethod("GET");
        assertThat(resolver.resolve(peticion)).isEqualTo("anterior");
    }

    @ParameterizedTest @ValueSource(strings = {"login", "registro", "logout"})
    void post_recuperacionConContextPath_ignoraInclusoCookiesAmbiguas(String accion) {
        var peticion = peticion("POST", accion.equals("registro")
                ? "/barberturno/api/registro" : "/barberturno/api/auth/" + accion);
        peticion.setCookies(new Cookie("BT_SESION", "anterior"), new Cookie("BT_SESION", "otra"));
        assertThat(resolver.resolve(peticion)).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"login", "registro", "logout", "password", "login/otra"})
    void get_oPostFueraDeRecuperacion_noOmiteCookie(String accion) {
        var peticion = peticion("GET", "/barberturno/api/auth/" + accion);
        assertThat(resolver.resolve(peticion)).isEqualTo("anterior");
        if (accion.equals("password") || accion.equals("login/otra") || accion.equals("registro")) {
            peticion.setMethod("POST");
            assertThat(resolver.resolve(peticion)).isEqualTo("anterior");
        }
    }

    /** V2-DA-48: fuera de /api/ no se necesita identidad; la cookie no se lee ni se valida. */
    @ParameterizedTest @ValueSource(strings = {"/barberturno/", "/barberturno/n/a/agenda", "/barberturno/index.html",
            "/barberturno/actuator/health", "/barberturno/apiario", "/barberturno/api"})
    void fueraDeApi_noLeeLaCookie(String ruta) {
        var peticion = peticion("GET", ruta);
        peticion.setCookies(new Cookie("BT_SESION", "anterior"), new Cookie("BT_SESION", "otra"));
        assertThat(resolver.resolve(peticion)).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"/barberturno/api/perfil", "/barberturno/api/n/a/sesion"})
    void dentroDeApi_sigueLeyendoLaCookie(String ruta) {
        assertThat(resolver.resolve(peticion("GET", ruta))).isEqualTo("anterior");
    }

    private MockHttpServletRequest peticion(String metodo, String ruta) {
        var peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setContextPath("/barberturno");
        peticion.setCookies(new Cookie("BT_SESION", "anterior"));
        return peticion;
    }
}
