package pe.barberturno.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Solicita la importación idempotente del prototipo únicamente durante la sustentación.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@Profile("demo & !prod")
@Order(100)
public class DatosDemoRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DatosDemoRunner.class);
    private final DatosDemoService datos;
    private final String password;

    /**
     * Recibe la carga transaccional y la contraseña externa, sin claves predeterminadas.
     * @param datos servicio que importa el escenario completo
     * @param password valor externo BT_DEMO_PASSWORD; vacío omite toda la carga
     */
    public DatosDemoRunner(DatosDemoService datos, @Value("${BT_DEMO_PASSWORD:}") String password) {
        this.datos = datos;
        this.password = password;
    }

    /**
     * Omite la carga sin contraseña y registra solo un aviso sin valores de configuración.
     * @param args argumentos del arranque proporcionados por Spring
     * @throws IllegalStateException si la contraseña o el catálogo impiden importar el escenario
     */
    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            LOG.warn("Falta BT_DEMO_PASSWORD: se omiten los datos y los usuarios de demostración.");
            return;
        }
        datos.cargar(password);
    }
}
