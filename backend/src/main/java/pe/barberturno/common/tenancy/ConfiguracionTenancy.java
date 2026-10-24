package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Conecta el ámbito de cada petición con todas las transacciones JPA de Spring.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionTenancy {
    /** Crea la configuración detectada por el escaneo de componentes. */
    public ConfiguracionTenancy() {
    }

    /**
     * Impide habilitar compatibilidad local fuera de dev, test o demo, especialmente en prod.
     * @param entorno perfiles y propiedades del arranque
     * @return validación anterior a cualquier conexión
     */
    @Bean
    static BeanFactoryPostProcessor impedirProduccion(Environment entorno) {
        return fabrica -> {
            if (entorno.containsProperty("barberturno.tenancy.alias-legado.slug")
                    && (entorno.acceptsProfiles(Profiles.of("prod"))
                    || !entorno.acceptsProfiles(Profiles.of("dev", "test", "demo")))) {
                throw new IllegalStateException("El alias legado solo admite dev/test/demo sin prod.");
            }
        };
    }

    /**
     * Instala el gestor JPA con contexto, conservando el dialecto estándar de Hibernate.
     * @param fabrica fábrica JPA autoconfigurada y validada contra Flyway
     * @param proveedor ámbito resuelto al comenzar cada transacción física
     * @return gestor único usado por servicios, repositorios y TransactionTemplate
     */
    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory fabrica, ProveedorNegocioActual proveedor) {
        return new GestorTransaccionesNegocio(fabrica, proveedor);
    }
}
