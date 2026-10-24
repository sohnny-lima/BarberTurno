package pe.barberturno.common.tenancy;

import java.util.List;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Coherencia y dominio de causas ejercitados con el rol real en ambos caminos de migración. */
final class MotivoRevocacionEsquemaPruebas {
    private MotivoRevocacionEsquemaPruebas() { }

    static void comprobar(JdbcTemplate jdbc, long usuarioId) {
        comprobar(jdbc, 1L, usuarioId);
    }

    static void comprobar(JdbcTemplate jdbc, long negocioId, long usuarioId) {
        assertThat(jdbc.queryForMap("""
                select data_type, character_maximum_length, is_nullable from information_schema.columns
                where table_name='membresia' and column_name='motivo_revocacion'
                """)).containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 20).containsEntry("is_nullable", "YES");
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            record Caso(String estado, String motivo, String restriccion) { }
            for (var caso : List.of(new Caso("REVOCADA", null, "membresia_motivo_coherente"),
                    new Caso("ACTIVA", "SALIDA", "membresia_motivo_coherente"),
                    new Caso("REVOCADA", "OTRO", "membresia_motivo_valido"))) {
                var punto = conexion.setSavepoint();
                try (var sentencia = conexion.prepareStatement("update membresia set estado=?, motivo_revocacion=? where negocio_id=? and usuario_id=?")) {
                    sentencia.setString(1, caso.estado()); sentencia.setString(2, caso.motivo());
                    sentencia.setLong(3, negocioId); sentencia.setLong(4, usuarioId);
                    var error = catchThrowableOfType(PSQLException.class, sentencia::executeUpdate);
                    assertThat((Throwable) error).as(caso.toString()).isNotNull();
                    assertThat(error.getSQLState()).isEqualTo("23514");
                    assertThat(error.getServerErrorMessage().getConstraint()).isEqualTo(caso.restriccion());
                } finally { conexion.rollback(punto); conexion.releaseSavepoint(punto); }
            }
            for (String motivo : List.of("PERFIL_DESACTIVADO", "SALIDA", "DECISION_GESTOR", "PLATAFORMA")) {
                var punto = conexion.setSavepoint();
                try {
                    assertThat(jdbc.update("update membresia set estado='REVOCADA',motivo_revocacion=? where negocio_id=? and usuario_id=?", motivo, negocioId, usuarioId)).isEqualTo(1);
                    assertThat(jdbc.queryForObject("select motivo_revocacion from membresia where negocio_id=? and usuario_id=?", String.class, negocioId, usuarioId)).isEqualTo(motivo);
                    assertThat(jdbc.update("update membresia set estado='ACTIVA',motivo_revocacion=null where negocio_id=? and usuario_id=?", negocioId, usuarioId)).isEqualTo(1);
                } finally { conexion.rollback(punto); conexion.releaseSavepoint(punto); }
            }
            return null;
        });
    }
}
