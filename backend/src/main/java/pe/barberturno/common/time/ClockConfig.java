package pe.barberturno.common.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Proporciona el reloj inyectable de la zona horaria del negocio.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * Crea el reloj que deberán usar los servicios para obtener el instante actual.
     * @return reloj del sistema con la zona America/Lima
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Lima"));
    }
}
