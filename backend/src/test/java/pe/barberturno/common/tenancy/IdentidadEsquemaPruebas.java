package pe.barberturno.common.tenancy;

import java.util.List;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Las mismas restricciones se ejercitan como aplicación en test, instalación y actualización. */
final class IdentidadEsquemaPruebas {
    private IdentidadEsquemaPruebas() { }

    static void comprobar(JdbcTemplate jdbc, long usuarioId) {
        assertThat(jdbc.queryForObject("select relrowsecurity or relforcerowsecurity from pg_class where oid='membresia'::regclass", Boolean.class)).isFalse();
        for (String permiso : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user, 'membresia', ?)", Boolean.class, permiso))
                    .as(permiso).isEqualTo(List.of("SELECT", "INSERT", "UPDATE", "DELETE").contains(permiso));
        }
        assertThat(jdbc.queryForObject("select has_sequence_privilege(current_user, 'membresia_id_seq', 'USAGE, SELECT')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select has_sequence_privilege(current_user, 'membresia_id_seq', 'UPDATE')", Boolean.class)).isFalse();
        jdbc.update("INSERT INTO membresia (negocio_id, usuario_id, rol, creado_en, actualizado_en) VALUES (1, ?, 'BARBERO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", usuarioId);
        record Caso(long negocio, long usuario, String rol, String estado, String sqlState, String restriccion) { }
        var casos = List.of(new Caso(1, usuarioId, "BARBERO", "ACTIVA", "23505", "membresia_unica_uk"),
                new Caso(1, -1, "BARBERO", "ACTIVA", "23503", "membresia_usuario_fk"),
                new Caso(-1, usuarioId, "BARBERO", "ACTIVA", "23503", "membresia_negocio_fk"),
                new Caso(1, usuarioId, "CLIENTE", "ACTIVA", "23514", "membresia_rol_valido"),
                new Caso(1, usuarioId, "BARBERO", "OTRO", "23514", "membresia_estado_valido"));
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            for (var caso : casos) {
                var punto = conexion.setSavepoint();
                try (var consulta = conexion.prepareStatement("INSERT INTO membresia (negocio_id, usuario_id, rol, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                    consulta.setLong(1, caso.negocio()); consulta.setLong(2, caso.usuario());
                    consulta.setString(3, caso.rol()); consulta.setString(4, caso.estado());
                    PSQLException error = catchThrowableOfType(PSQLException.class, consulta::executeUpdate);
                    assertThat((Throwable) error).as(caso.restriccion()).isNotNull();
                    assertThat(error.getSQLState()).isEqualTo(caso.sqlState());
                    assertThat(error.getServerErrorMessage().getConstraint()).isEqualTo(caso.restriccion());
                } finally { conexion.rollback(punto); conexion.releaseSavepoint(punto); }
            }
            var punto = conexion.setSavepoint();
            try (var consulta = conexion.prepareStatement("UPDATE usuario SET rol_plataforma='OTRO' WHERE id=?")) {
                consulta.setLong(1, usuarioId);
                PSQLException error = catchThrowableOfType(PSQLException.class, consulta::executeUpdate);
                assertThat((Throwable) error).isNotNull();
                assertThat(error.getSQLState()).isEqualTo("23514");
                assertThat(error.getServerErrorMessage().getConstraint()).isEqualTo("usuario_rol_plataforma_valido");
            } finally { conexion.rollback(punto); conexion.releaseSavepoint(punto); }
            return null;
        });
    }
}
