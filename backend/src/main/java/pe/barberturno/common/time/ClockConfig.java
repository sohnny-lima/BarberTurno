package pe.barberturno.common.time;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Proporciona el reloj del negocio; permite fijarlo exclusivamente en demo y test.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * Spring crea esta configuración al arrancar el contexto para registrar el reloj del negocio.
     */
    public ClockConfig() {
    }

    /**
     * Selecciona el reloj de Lima; la propiedad de sustentación nunca altera producción.
     * @param entorno perfiles y propiedad barberturno.reloj-fijo del proceso
     * @return reloj fijo en demo/test si se configura; reloj del sistema en los demás casos
     * @throws IllegalStateException si prod define barberturno.reloj-fijo
     * @throws java.time.format.DateTimeParseException si el instante no es ISO-8601 con desfase
     */
    @Bean
    public Clock clock(Environment entorno) {
        String fijo = entorno.getProperty("barberturno.reloj-fijo");
        if (fijo != null && entorno.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("barberturno.reloj-fijo no está permitido en prod.");
        }
        if (fijo != null && !fijo.isBlank() && entorno.acceptsProfiles(Profiles.of("demo", "test"))) {
            return Clock.fixed(OffsetDateTime.parse(fijo).toInstant(), TiempoNegocio.ZONA);
        }
        return Clock.system(TiempoNegocio.ZONA);
    }
}
