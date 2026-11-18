package pe.barberturno.conversation.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Enlace de {@link IaPropiedades} con los valores por defecto y validación del plazo del turno (g095-3).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class IaPropiedadesTest {
    private static IaPropiedades enlazar(Map<String, String> valores) {
        return new Binder(new MapConfigurationPropertySource(valores)).bindOrCreate("barberturno.ia", IaPropiedades.class);
    }

    @Test
    void porDefecto_elPlazoDelTurnoSuperaElDeUnaLlamada_ySinClaveNoEstaConfigurada() {
        var p = enlazar(Map.of());
        assertThat(p.plazoTurno()).isGreaterThan(p.tiempoLlamada());
        assertThat(p.tiempoLlamada()).isEqualTo(Duration.ofSeconds(15));
        assertThat(p.configurada()).isFalse();
    }

    @Test
    void plazoDelTurnoIgualOMenorQueUnaLlamada_seRechazaAlArrancar() {
        assertThatThrownBy(() -> enlazar(Map.of("barberturno.ia.plazo-turno", "15s")))
                .isInstanceOf(BindException.class).rootCause()
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("plazo-turno");
        assertThatThrownBy(() -> enlazar(Map.of("barberturno.ia.plazo-turno", "10s", "barberturno.ia.tiempo-llamada", "20s")))
                .isInstanceOf(BindException.class);
    }
}
