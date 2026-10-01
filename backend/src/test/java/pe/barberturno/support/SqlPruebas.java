package pe.barberturno.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Captura solo sentencias SQL sin parámetros ni valores para comprobar los bloqueos. */
public final class SqlPruebas implements StatementInspector {

    private static final List<String> SENTENCIAS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SENTENCIAS.add(sql);
        return sql;
    }

    public static List<String> sentencias() {
        return List.copyOf(SENTENCIAS);
    }

    public static void limpiar() {
        SENTENCIAS.clear();
    }
}