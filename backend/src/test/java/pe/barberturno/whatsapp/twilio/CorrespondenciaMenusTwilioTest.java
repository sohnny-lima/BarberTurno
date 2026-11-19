package pe.barberturno.whatsapp.twilio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.barberturno.support.RelojAjustable;
import static org.assertj.core.api.Assertions.assertThat;

/** Regresión del cerrojo temporal de selecciones con tiempo virtual, sin esperas reales. */
class CorrespondenciaMenusTwilioTest {
    private final RelojAjustable reloj = new RelojAjustable();
    private final AtomicLong nanos = new AtomicLong();
    private CorrespondenciaMenusTwilio menus;

    @BeforeEach
    void preparar() {
        reloj.fijar(Instant.parse("2026-11-18T15:00:00Z"));
        var propiedades = new TwilioPropiedades("", "", "+14155238886", "", "barberturno",
                Duration.ofSeconds(3), "https://api.twilio.com", true, Duration.ofSeconds(10),
                Duration.ofMillis(1500), List.of());
        menus = new CorrespondenciaMenusTwilio(reloj, propiedades, nanos::get);
        publicar("s:1");
    }

    private void publicar(String id) {
        menus.guardar("14155238886", "51999000111", List.of(
                new CorrespondenciaMenusTwilio.Seleccion("button_reply", id, "Elegir " + id)), "1. Elegir " + id);
    }

    private CorrespondenciaMenusTwilio.Decision elegir(String sid) {
        return menus.decidir("14155238886", "51999000111", "1", sid, false, nanos.get());
    }

    @Test
    void reaccionMinima_bloqueaHastaElLimite_yNoReinterpretaUnSidBloqueado() {
        nanos.set(Duration.ofMillis(1499).toNanos());
        var bloqueada = elegir("SM_TEMPRANO");
        assertThat(bloqueada.seleccion()).isEmpty();
        assertThat(bloqueada.aviso()).get().asString().contains("Recibí varios mensajes seguidos", "1. Elegir s:1");
        nanos.set(Duration.ofMillis(1500).toNanos());
        assertThat(elegir("SM_TEMPRANO")).isEqualTo(bloqueada);
        assertThat(elegir("SM_POSTERIOR").seleccion()).get()
                .extracting(CorrespondenciaMenusTwilio.Seleccion::id).isEqualTo("s:1");
    }

    @Test
    void dosUnos_conMenuYaPublicado_esperanReaccionYConservanIdempotencia() {
        nanos.set(Duration.ofMillis(1500).toNanos());
        var primera = elegir("SM_PRIMERO");
        assertThat(primera.seleccion()).isPresent();
        nanos.set(Duration.ofMillis(1600).toNanos());
        publicar("m:RESERVAR");
        nanos.set(Duration.ofMillis(2600).toNanos());
        var segunda = elegir("SM_SEGUNDO");
        assertThat(segunda.seleccion()).isEmpty();
        assertThat(segunda.aviso()).get().asString().contains("1. Elegir m:RESERVAR");
        assertThat(elegir("SM_PRIMERO")).isEqualTo(primera);
        nanos.set(Duration.ofMillis(3100).toNanos());
        assertThat(elegir("SM_NUEVO").seleccion()).get()
                .extracting(CorrespondenciaMenusTwilio.Seleccion::id).isEqualTo("m:RESERVAR");
    }

    @Test
    void respuestaPerdida_liberaPendienteALos20Segundos_sinCaducarElMenuNiRepetirUnSid() {
        nanos.set(Duration.ofSeconds(2).toNanos());
        menus.decidir("14155238886", "51999000111", "persona", "SM_DERIVAR", false, nanos.get());
        reloj.adelantar(Duration.ofMillis(19999));
        var antes = elegir("SM_ANTES");
        assertThat(antes.seleccion()).isEmpty();
        reloj.adelantar(Duration.ofMillis(1));
        assertThat(elegir("SM_ANTES")).isEqualTo(antes);
        assertThat(elegir("SM_RECUPERADO").seleccion()).isPresent();
    }

    @Test
    void derivacionConTextoEntregado_liberaPendienteSinNecesitarOtroMenu() {
        nanos.set(Duration.ofSeconds(2).toNanos());
        menus.decidir("14155238886", "51999000111", "persona", "SM_PERSONA", false, nanos.get());
        assertThat(elegir("SM_MIENTRAS_SE_ENVIA").seleccion()).isEmpty();
        menus.entregadoSinOpciones("14155238886", "51999000111");
        assertThat(elegir("SM_TRAS_EL_TEXTO").seleccion()).isPresent();
    }
}
