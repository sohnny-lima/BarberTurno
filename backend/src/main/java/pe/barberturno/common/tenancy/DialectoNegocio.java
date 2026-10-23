package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import org.hibernate.Session;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;

/**
 * Fija RLS en la conexión de Hibernate después de abrir la transacción y antes del primer acceso.
 * Spring 7.0.9 llama a {@code JpaDialect.beginTransaction} desde {@code JpaTransactionManager.doBegin},
 * tanto para los repositorios (incluidos los heredados de solo lectura) como para
 * {@code TransactionTemplate}. Hibernate 7.4.5 ejecuta {@code Session.doWork} sobre esa misma conexión;
 * posteriormente Spring la expone a JdbcTemplate. No se interceptan consultas individuales.
 * La propagación REQUIRED reutiliza el ámbito inicial; REQUIRES_NEW abre otro ámbito.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
final class DialectoNegocio extends HibernateJpaDialect {
    private final ProveedorNegocioActual proveedor;

    DialectoNegocio(ProveedorNegocioActual proveedor) {
        this.proveedor = proveedor;
    }

    /**
     * Conserva aislamiento, timeout, flush y solo lectura del dialecto estándar y fija un GUC local.
     * Un contexto ausente se representa vacío para que {@code app_negocio_actual()} devuelva NULL.
     * @param entityManager sesión perteneciente a esta transacción
     * @param definition aislamiento, propagación y opciones resueltas por Spring
     * @return estado que el dialecto estándar necesita para limpiar la transacción
     * @throws SQLException si no puede comenzar la transacción o preparar su conexión
     * @throws jakarta.persistence.PersistenceException si Hibernate no puede fijar el contexto RLS
     * @throws IllegalStateException si el proveedor no puede resolver el ámbito de la operación
     */
    @Override
    public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
            throws SQLException {
        Object datos = super.beginTransaction(entityManager, definition);
        try {
            var negocio = proveedor.negocioActual();
            entityManager.unwrap(Session.class).doWork(conexion -> {
                try (var consulta = conexion.prepareStatement("select set_config('app.negocio_id', ?, true)")) {
                    consulta.setString(1, negocio.isPresent() ? Long.toString(negocio.getAsLong()) : "");
                    consulta.execute();
                }
            });
            return datos;
        } catch (RuntimeException fallo) {
            // No devolver al pool una transacción iniciada parcialmente si el proveedor/SQL falla.
            try {
                entityManager.getTransaction().rollback();
            } finally {
                super.cleanupTransaction(datos);
            }
            throw fallo;
        }
    }
}
