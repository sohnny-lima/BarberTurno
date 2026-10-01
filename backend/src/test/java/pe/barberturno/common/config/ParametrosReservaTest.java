package pe.barberturno.common.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.assertj.core.api.Assertions.*;

class ParametrosReservaTest {
    @Test void enlazarYaml_conservaLosSeisValoresDelPlan() throws Exception {
        var fuente = new YamlPropertySourceLoader().load("aplicacion", new ClassPathResource("application.yml")).getFirst();
        try (var validador = new LocalValidatorFactoryBean()) {
            validador.afterPropertiesSet();
            var parametros = new Binder(ConfigurationPropertySources.from(fuente))
                    .bind("barberturno.reservas", Bindable.of(ParametrosReserva.class), new ValidationBindHandler(validador)).get();
            assertThat(parametros).isEqualTo(new ParametrosReserva(Duration.ofHours(2), 30, 10, 3, false, 15));
        }
    }
    @ParameterizedTest @CsvSource({"anticipacion-cambio-cliente,-1s", "horizonte-dias,0", "rejilla-min,0", "max-activas-por-cliente,0", "tolerancia-inicio-min,-1", "confirmacion-manual,invalidamente"})
    void enlazar_configuracionInvalidaFalla(String clave, String valor) {
        Map<String, Object> valores = valoresValidos();
        valores.put("barberturno.reservas." + clave, valor);
        assertThatExceptionOfType(BindException.class).isThrownBy(() -> enlazar(valores));
    }
    @Test void enlazar_anticipacionAusenteFalla() {
        var valores = valoresValidos();
        valores.remove("barberturno.reservas.anticipacion-cambio-cliente");
        assertThatExceptionOfType(BindException.class).isThrownBy(() -> enlazar(valores));
    }
    @Test void enlazar_ceroEnDuracionesYConfirmacionManualSonValidos() {
        var valores = valoresValidos();
        valores.put("barberturno.reservas.anticipacion-cambio-cliente", "0s");
        valores.put("barberturno.reservas.tolerancia-inicio-min", "0");
        valores.put("barberturno.reservas.confirmacion-manual", "true");
        assertThat(enlazar(valores)).isEqualTo(new ParametrosReserva(Duration.ZERO, 30, 10, 3, true, 0));
    }
    private ParametrosReserva enlazar(Map<String, Object> valores) {
        try (var validador = new LocalValidatorFactoryBean()) {
            validador.afterPropertiesSet();
            return new Binder(new MapConfigurationPropertySource(valores))
                    .bind("barberturno.reservas", Bindable.of(ParametrosReserva.class), new ValidationBindHandler(validador)).get();
        }
    }
    private Map<String, Object> valoresValidos() {
        return new HashMap<>(Map.of("barberturno.reservas.anticipacion-cambio-cliente", "2h",
                "barberturno.reservas.horizonte-dias", "30", "barberturno.reservas.rejilla-min", "10",
                "barberturno.reservas.max-activas-por-cliente", "3", "barberturno.reservas.confirmacion-manual", "false",
                "barberturno.reservas.tolerancia-inicio-min", "15"));
    }
}
