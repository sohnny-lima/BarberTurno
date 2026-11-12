package pe.barberturno.whatsapp.twilio;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el alta del canal después de los datos de demostración (órdenes 100 y 101).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
@Order(200)
public class RegistroCanalTwilioRunner implements ApplicationRunner {
    private final RegistroCanalTwilioService registro;

    /**
     * Delega en el proxy transaccional, sin abrir transacciones en el runner.
     * @param registro servicio idempotente exclusivo de este perfil
     */
    public RegistroCanalTwilioRunner(RegistroCanalTwilioService registro) { this.registro = registro; }

    /**
     * Registra el Sandbox aunque falten credenciales; los callbacks y el envío siguen cerrados sin ellas.
     * @param args argumentos del proceso, no usados para credenciales
     * @throws IllegalStateException si el negocio no está preparado o tiene un canal incompatible
     */
    @Override
    public void run(ApplicationArguments args) { registro.registrar(); }
}
