package pe.barberturno.whatsapp.twilio;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Tiempo monotónico virtual para pruebas de transporte que no evalúan la velocidad humana. */
final class SoporteMenusTwilio {
    private SoporteMenusTwilio() { }

    static CorrespondenciaMenusTwilio crear(Clock reloj) {
        var propiedades = new TwilioPropiedades("", "", "+14155238886", "", "barberturno",
                Duration.ofSeconds(3), "https://api.twilio.com", true, Duration.ofSeconds(10),
                Duration.ofMillis(1500), List.of());
        var nanos = new AtomicLong();
        return new CorrespondenciaMenusTwilio(reloj, propiedades,
                () -> nanos.getAndAdd(Duration.ofSeconds(2).toNanos()));
    }
}
