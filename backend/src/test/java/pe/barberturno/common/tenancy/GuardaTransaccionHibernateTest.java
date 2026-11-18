package pe.barberturno.common.tenancy;

import java.util.HashMap;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.barberturno.support.SqlPruebas;
import static org.assertj.core.api.Assertions.*;

class GuardaTransaccionHibernateTest {
    @Test
    void soloSincronizacion_noSustituyeUnaTransaccionReal() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> new GuardaTransaccionHibernate().inspect("select 1"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("transacción de Spring activa");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"instancia", "clase", "nombre"})
    void inspectorPrevio_soloRecibeSqlTrasSuperarLaGuarda(String forma) {
        SqlPruebas.limpiar();
        Object configurado = switch (forma) {
            case "instancia" -> new SqlPruebas();
            case "clase" -> SqlPruebas.class;
            default -> SqlPruebas.class.getName();
        };
        var propiedades = new HashMap<String, Object>();
        propiedades.put(JdbcSettings.STATEMENT_INSPECTOR, configurado);
        new GuardaTransaccionHibernate().customize(propiedades);
        var inspector = (StatementInspector) propiedades.get(JdbcSettings.STATEMENT_INSPECTOR);
        assertThatThrownBy(() -> inspector.inspect("select 1")).isInstanceOf(IllegalStateException.class);
        assertThat(SqlPruebas.sentencias()).isEmpty();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThat(inspector.inspect("select 1")).isEqualTo("select 1");
            assertThat(SqlPruebas.sentencias()).containsExactly("select 1");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            SqlPruebas.limpiar();
        }
    }
}
