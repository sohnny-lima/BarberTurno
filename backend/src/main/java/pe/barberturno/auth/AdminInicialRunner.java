package pe.barberturno.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/**
 * Inicializa la cuenta ADMIN configurada antes de la demo, sin claves predeterminadas (arquitectura §7.1).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@Order(0)
public class AdminInicialRunner implements ApplicationRunner {
    private final AuthService auth;
    private final String correo;
    private final String password;
    private final String nombre;

    /**
     * Recibe servicio y configuración externa de la cuenta inicial, sin incluir sus valores en logs.
     * @param auth servicio no nulo de identidad y revalidación
     * @param correo correo inicial configurado; vacío indica configuración incompleta
     * @param password contraseña inicial configurada; vacía omite la cuenta, nunca se registra
     * @param nombre nombre inicial configurado; vacío omite la cuenta
     */
    public AdminInicialRunner(AuthService auth,
            @Value("${barberturno.admin.correo:}") String correo,
            @Value("${barberturno.admin.password:}") String password,
            @Value("${barberturno.admin.nombre:}") String nombre) {
        this.auth = auth;
        this.correo = correo;
        this.password = password;
        this.nombre = nombre;
    }

    /**
     * Solicita la creación idempotente del administrador al arrancar; omite la cuenta si faltan valores
     * configurados.
     * @param args argumentos no nulos de arranque proporcionados por Spring
     */
    @Override
    public void run(ApplicationArguments args) {
        auth.crearAdminInicial(correo, password, nombre);
    }
}
