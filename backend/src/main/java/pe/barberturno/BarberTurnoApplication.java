package pe.barberturno;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la API de BarberTurno.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@SpringBootApplication
public class BarberTurnoApplication {

    /**
     * Inicia la aplicación con la configuración del perfil seleccionado.
     * @param args argumentos de arranque
     */
    public static void main(String[] args) {
        SpringApplication.run(BarberTurnoApplication.class, args);
    }
}
