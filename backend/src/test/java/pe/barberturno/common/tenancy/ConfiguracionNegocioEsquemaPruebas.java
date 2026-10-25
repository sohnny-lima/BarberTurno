package pe.barberturno.common.tenancy;

import java.sql.Connection;
import java.util.List;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Permisos de V112 compartidos por las pruebas del esquema nuevo y de la actualización. */
final class ConfiguracionNegocioEsquemaPruebas {
    private ConfiguracionNegocioEsquemaPruebas() { }

    static void comprobarPermisos(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'negocio','UPDATE')", Boolean.class)).isFalse();
        var permitidas = List.of("nombre", "estado", "anticipacion_cambio_cliente_min", "horizonte_dias",
                "limite_reservas_activas", "limite_barberos_activos", "confirmacion_manual", "tolerancia_inicio_min", "actualizado_en");
        for (String columna : jdbc.queryForList("select column_name from information_schema.columns where table_name='negocio' order by ordinal_position", String.class)) {
            assertThat(jdbc.queryForObject("select has_column_privilege(current_user,'negocio',?,'UPDATE')", Boolean.class, columna))
                    .as("UPDATE de %s", columna).isEqualTo(permitidas.contains(columna));
        }
    }

    static void rechazarColumnasReservadas(Connection conexion) throws Exception {
        var jdbc = MigracionPruebaBase.jdbc(conexion);
        var antes = jdbc.queryForMap("select * from negocio where id=1");
        for (String columna : List.of("id", "slug", "zona_horaria", "creado_en")) {
            var punto = conexion.setSavepoint();
            var error = catchThrowableOfType(DataAccessException.class,
                    () -> jdbc.update("update negocio set " + columna + "="
                            + (columna.equals("id") ? "DEFAULT" : columna) + " where id=1"));
            assertThat(error).isNotNull();
            assertThat(((PSQLException) error.getMostSpecificCause()).getSQLState()).isEqualTo("42501");
            conexion.rollback(punto);
            conexion.releaseSavepoint(punto);
        }
        assertThat(jdbc.queryForMap("select * from negocio where id=1")).isEqualTo(antes);
    }
}
