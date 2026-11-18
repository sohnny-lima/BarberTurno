package pe.barberturno.whatsapp;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WhatsAppPropiedadesTest {
    @Test void secretosAusentes_seNormalizanAVacioYSeDeclaranSinConfigurar() {
        var propiedades = new WhatsAppPropiedades(null, "  ", null);
        assertThat(propiedades.verifyToken()).isEmpty();
        assertThat(propiedades.verificacionConfigurada()).isFalse();
        assertThat(propiedades.firmaConfigurada()).isFalse();
        assertThat(propiedades.procesador().lote()).isEqualTo(20);
        assertThat(propiedades.procesador().maxIntentos()).isEqualTo(5);
        assertThat(propiedades.procesador().reclamo()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test void procesadorFueraDeRango_seRechazaAlArrancar() {
        assertThatThrownBy(() -> new WhatsAppPropiedades.Procesador(true, Duration.ZERO, 20, 5,
                Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WhatsAppPropiedades.Procesador(true, Duration.ofSeconds(1), 20, 5,
                Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WhatsAppPropiedades.Procesador(true, Duration.ofSeconds(1), 0, 5,
                Duration.ofSeconds(5), Duration.ofMinutes(5), Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
