package pe.barberturno.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Crea el administrador configurado al arrancar, sin credenciales predeterminadas. */
@Component
public class AdminInicialRunner implements ApplicationRunner {
    private final AuthService auth;
    private final String correo;
    private final String password;
    private final String nombre;

    public AdminInicialRunner(AuthService auth,
            @Value("${barberturno.admin.correo:}") String correo,
            @Value("${barberturno.admin.password:}") String password,
            @Value("${barberturno.admin.nombre:}") String nombre) {
        this.auth = auth;
        this.correo = correo;
        this.password = password;
        this.nombre = nombre;
    }

    @Override
    public void run(ApplicationArguments args) {
        auth.crearAdminInicial(correo, password, nombre);
    }
}