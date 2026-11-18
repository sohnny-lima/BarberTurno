package pe.barberturno.common.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** V2-18 · CP-9: la asociación sale de la configuración, es pública y no se confunde con una ruta Angular. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "barberturno.android.paquete=pe.barberturno.prueba.twa",
        "barberturno.android.huellas=" + AsociacionAndroidIT.HUELLA_A + "," + AsociacionAndroidIT.HUELLA_B})
class AsociacionAndroidIT {
    static final String HUELLA_A =
            "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99";
    static final String HUELLA_B =
            "01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF";

    @Autowired MockMvc mvc;

    @Test
    void assetlinks_sinSesion_devuelveLaDeclaracionConfigurada() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/json")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].relation", contains("delegate_permission/common.handle_all_urls")))
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("pe.barberturno.prueba.twa"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints", contains(HUELLA_A, HUELLA_B)));
    }

    @Test
    void assetlinks_conAcceptHtml_noSeReenviaAAngular() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json").accept("text/html,application/json"))
                .andExpect(status().isOk()).andExpect(forwardedUrl(null))
                .andExpect(jsonPath("$[0].target.package_name").value("pe.barberturno.prueba.twa"));
    }

    @Test
    void assetlinks_conCookieInvalida_siguePublico() throws Exception {
        var cookie = new jakarta.servlet.http.Cookie("BT_SESION", "sesion-invalida-de-la-prueba");
        mvc.perform(get("/.well-known/assetlinks.json").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(head("/.well-known/assetlinks.json")).andExpect(status().isOk());
    }

    @Test
    void manifest_seSirveComoManifestDeAplicacion() throws Exception {
        mvc.perform(get("/manifest.webmanifest"))
                .andExpect(status().isOk()).andExpect(forwardedUrl(null))
                .andExpect(header().string("Content-Type", startsWith("application/manifest+json")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BarberTurno (prueba)")));
    }
}
