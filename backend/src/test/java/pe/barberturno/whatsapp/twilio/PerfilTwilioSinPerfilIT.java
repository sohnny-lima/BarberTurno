package pe.barberturno.whatsapp.twilio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración del comportamiento del sistema sin el perfil {@code twilio} (CT-7, V2-DA-97).
 * Valida que los beans del adaptador no existan en el contexto y que las rutas
 * {@code POST /api/twilio/webhook} y {@code POST /api/twilio/estado} respondan 404 Not Found.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=",
        "barberturno.admin.password=",
        "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PerfilTwilioSinPerfilIT {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mvc;

    @Test
    void beansTwilio_noExistenEnContextoSinPerfil() {
        assertThat(context.getBeanNamesForType(WebhookTwilioController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ClienteTwilioWhatsApp.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RegistroCanalTwilioService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TwilioPropiedades.class)).isEmpty();
        assertThat(context.getBeanNamesForType(FirmaTwilio.class)).isEmpty();
        assertThat(context.getBeanNamesForType(TraductorEntradaTwilio.class)).isEmpty();
        assertThat(context.getBeanNamesForType(CorrespondenciaMenusTwilio.class)).isEmpty();
    }

    @Test
    void rutasTwilio_devuelven404SinElPerfil() throws Exception {
        mvc.perform(post("/api/twilio/webhook")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .content("Body=hola"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/twilio/estado")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                        .content("MessageStatus=delivered"))
                .andExpect(status().isNotFound());
    }
}
