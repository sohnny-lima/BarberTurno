package pe.barberturno.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;

/**
 * Carga el escenario multinegocio después de la demo académica, únicamente con demo y sin prod.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("demo & !prod")
@Order(101)
public class DatosDemoMultinegocioRunner implements ApplicationRunner {
    private final DatosDemoMultinegocioService datos;
    private final EjecutorEnNegocio ejecutor;
    private final String slug;
    private final String password;

    /**
     * Recibe la carga de V2-07.0 y el mismo ámbito explícito y contraseña de la demo académica.
     * @param datos carga transaccional que conserva lo existente
     * @param ejecutor fija cada negocio antes de abrir su transacción
     * @param slug barbería inicial de la demo
     * @param password BT_DEMO_PASSWORD externa; vacía omite toda la carga
     */
    public DatosDemoMultinegocioRunner(DatosDemoMultinegocioService datos, EjecutorEnNegocio ejecutor,
            @Value("${barberturno.demo.negocio-slug:}") String slug, @Value("${BT_DEMO_PASSWORD:}") String password) {
        this.datos = datos; this.ejecutor = ejecutor; this.slug = slug; this.password = password;
    }

    /**
     * Prepara las cuentas globales y completa dos ámbitos con transacciones independientes e idempotentes.
     * @param args argumentos del arranque de Spring
     * @throws IllegalStateException si faltan negocio, cuentas, cupo o una clave válida
     */
    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) return;
        datos.preparar(password);
        ejecutor.ejecutar(slug, () -> { datos.completar(true); return null; });
        ejecutor.ejecutar("sur", () -> { datos.completar(false); return null; });
    }
}
