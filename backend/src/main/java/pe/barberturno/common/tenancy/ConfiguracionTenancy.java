package pe.barberturno.common.tenancy;

import java.util.OptionalLong;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Conecta el ámbito transitorio de V2-01 con todas las transacciones JPA de Spring.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionTenancy {
    /** Crea la configuración detectada por el escaneo de componentes. */
    public ConfiguracionTenancy() {
    }

    /**
     * Impide usar la fundación transitoria como un despliegue de producción.
     * @param entorno perfiles y propiedades del arranque
     * @return validación anterior a cualquier conexión
     */
    @Bean
    static BeanFactoryPostProcessor impedirProduccion(Environment entorno) {
        return fabrica -> {
            if (entorno.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("V2-01 no admite el perfil prod; falta el resolutor de negocio.");
            }
        };
    }

    /**
     * Mantiene un único negocio hasta que V2-05 introduzca la resolución por petición.
     * @param entorno configuración local de dev o test
     * @return proveedor inmutable del identificador obligatorio
     * @throws IllegalStateException si falta el identificador o no es positivo
     */
    @Bean
    ProveedorNegocioActual proveedorNegocioActual(Environment entorno) {
        Long id = entorno.getProperty("barberturno.tenancy.negocio-unico-id", Long.class);
        if (id == null || id <= 0) {
            throw new IllegalStateException("Se requiere barberturno.tenancy.negocio-unico-id positivo.");
        }
        return () -> OptionalLong.of(id);
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
