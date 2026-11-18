package pe.barberturno.common.tenancy;

import java.util.Map;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

/**
 * Impide que Hibernate consulte sin la transacción Spring que instala el contexto de negocio.
 * La inspección de sentencias de sesión no intercepta la validación del esquema ni el JDBC
 * directo de Flyway, del dialecto transaccional o de la limpieza de pruebas.
 * La instancia compartida no conserva SQL ni estado de peticiones.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
public class GuardaTransaccionHibernate implements StatementInspector, HibernatePropertiesCustomizer {
    /** Crea la guarda sin estado que comparten las sesiones de la fábrica JPA. */
    public GuardaTransaccionHibernate() {
    }

    /**
     * Registra la inspección antes de construir la fábrica de sesiones autoconfigurada.
     * Conserva un inspector configurado previamente, después de comprobar la transacción.
     * @param propiedades opciones de Hibernate de la fábrica JPA
     * @throws IllegalArgumentException si el inspector previo no es una clase válida
     */
    @Override
    public void customize(Map<String, Object> propiedades) {
        Object configurado = propiedades.get(JdbcSettings.STATEMENT_INSPECTOR);
        if (configurado == null) {
            propiedades.put(JdbcSettings.STATEMENT_INSPECTOR, this);
            return;
        }
        StatementInspector anterior = resolverInspector(configurado);
        propiedades.put(JdbcSettings.STATEMENT_INSPECTOR, (StatementInspector) sql -> {
            inspect(sql);
            return anterior.inspect(sql);
        });
    }

    private static StatementInspector resolverInspector(Object configurado) {
        if (configurado instanceof StatementInspector inspector) {
            return inspector;
        }
        Class<?> tipo = configurado instanceof Class<?> clase ? clase
                : ClassUtils.resolveClassName(configurado.toString(), ClassUtils.getDefaultClassLoader());
        return BeanUtils.instantiateClass(tipo.asSubclass(StatementInspector.class));
    }

    /**
     * Exige una transacción física de Spring antes de preparar cualquier SQL de sesión.
     * @param sql sentencia generada por Hibernate, que no se registra ni modifica
     * @return la misma sentencia para continuar su preparación
     * @throws IllegalStateException si falta una transacción Spring activa en el hilo
     */
    @Override
    public String inspect(String sql) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Hibernate requiere una transacción de Spring activa para acceder a datos.");
        }
        return sql;
    }
}
