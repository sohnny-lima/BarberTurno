package pe.barberturno.whatsapp.twilio;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Vector público de RequestValidatorTest del SDK oficial twilio-java; no contiene credenciales reales. */
class FirmaTwilioTest {
    @Test
    void comprobar_vectorOficialDelSdkJava_coincide() {
        var parametros = Map.of("Digits", "1234", "CallSid", "CA1234567890ABCDE", "To", "+18005551212",
                "Caller", "+14158675309", "From", "+14158675309");
        assertThat(FirmaTwilio.comprobar("12345", "https://mycompany.com/myapp.php?foo=1&bar=2",
                parametros, "RSOYDt4T1cUTdK1PDd93/VVr8B8=")).isTrue();
    }
}
