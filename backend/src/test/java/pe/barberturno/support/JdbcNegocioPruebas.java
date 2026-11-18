package pe.barberturno.support;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC explícitamente transaccional para fixtures y aserciones de v1; no sustituye el bean de producción. */
@Component
public final class JdbcNegocioPruebas {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final Environment entorno;

    public JdbcNegocioPruebas(JdbcTemplate jdbc, PlatformTransactionManager gestor, Environment entorno) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(gestor);
        this.entorno = entorno;
    }

    public <T> T queryForObject(String sql, Class<T> tipo, Object... parametros) {
        return enTransaccion(() -> jdbc.queryForObject(sql, tipo, parametros));
    }

    public <T> List<T> queryForList(String sql, Class<T> tipo, Object... parametros) {
        return enTransaccion(() -> jdbc.queryForList(sql, tipo, parametros));
    }

    public List<Map<String, Object>> queryForList(String sql, Object... parametros) {
        return enTransaccion(() -> jdbc.queryForList(sql, parametros));
    }

    public Map<String, Object> queryForMap(String sql, Object... parametros) {
        return enTransaccion(() -> jdbc.queryForMap(sql, parametros));
    }

    public int update(String sql, Object... parametros) {
        return enTransaccion(() -> jdbc.update(sql, parametros));
    }

    public void execute(String sql) {
        transaccion.executeWithoutResult(tx -> jdbc.execute(sql));
    }

    public <T> T enTransaccion(Supplier<T> accion) {
        return transaccion.execute(tx -> accion.get());
    }

    public Environment entorno() {
        return entorno;
    }
}
