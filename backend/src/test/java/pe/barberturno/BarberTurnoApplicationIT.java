package pe.barberturno;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class BarberTurnoApplicationIT {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private Environment entorno;

    @Autowired
    private Clock clock;

    @Test
    void cargarContexto_utilizaElPerfilTestYElRelojDelNegocio() {
        assertThat(contexto.getBean(BarberTurnoApplication.class)).isNotNull();
        assertThat(entorno.getActiveProfiles()).containsExactly("test");
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(entorno.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(entorno.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(entorno.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
    }

    @Test
    void cargarContexto_noCreaUsuariosConContrasenasGeneradas() {
        assertThat(contexto.getBeansOfType(UserDetailsService.class)).isEmpty();
    }
}
