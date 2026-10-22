package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Instala el dialecto de negocio después de la autodetección de Spring.
 * {@code JpaTransactionManager.afterPropertiesSet} relee el dialecto de la fábrica JPA;
 * por eso fijarlo solo desde un customizer de Boot anterior a la inicialización no basta.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
final class GestorTransaccionesNegocio extends JpaTransactionManager {
    private final ProveedorNegocioActual proveedor;

    GestorTransaccionesNegocio(EntityManagerFactory fabrica, ProveedorNegocioActual proveedor) {
        setEntityManagerFactory(fabrica);
        this.proveedor = proveedor;
    }

    /**
     * Conserva la detección de DataSource y después fija el dialecto que abre cada ámbito RLS.
     * @throws IllegalArgumentException si falta la fábrica de persistencia
     */
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        setJpaDialect(new DialectoNegocio(proveedor));
    }
}
