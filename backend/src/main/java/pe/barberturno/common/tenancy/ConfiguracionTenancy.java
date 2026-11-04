package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

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
     * Rechaza cualquier propiedad del alias retirado, en todos los perfiles y antes de abrir conexiones.
     * Incluye propiedades de entorno con nombres relajados, valores vacíos y claves distintas de slug.
     * @param entorno perfiles y propiedades del arranque
     * @return validación anterior a cualquier conexión
     */
    @Bean
    static BeanFactoryPostProcessor impedirAliasLegado(ConfigurableEnvironment entorno) {
        return fabrica -> {
            for (var fuente : entorno.getPropertySources()) {
                if (fuente instanceof EnumerablePropertySource<?> enumerable
                        && java.util.Arrays.stream(enumerable.getPropertyNames()).anyMatch(nombre ->
                            nombre.replaceAll("[-_.]", "").toLowerCase(java.util.Locale.ROOT)
                                    .startsWith("barberturnotenancyaliaslegado"))) {
                    throw new IllegalStateException("El alias legado fue retirado; elimine barberturno.tenancy.alias-legado de la configuración.");
                }
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
