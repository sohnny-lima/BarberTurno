package pe.barberturno.common.tenancy;

import java.util.List;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Contrato de V110 ejercitado como aplicación en instalación, actualización y test. */
final class InvitacionEsquemaPruebas {
    private InvitacionEsquemaPruebas() { }

    static void comprobar(JdbcTemplate jdbc, long usuario) {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(jdbc.queryForObject("select relrowsecurity or relforcerowsecurity from pg_class where oid='invitacion'::regclass", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='invitacion' and column_name='barbero_id'", Integer.class)).isZero();
        for (String permiso : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'invitacion',?)", Boolean.class, permiso))
                    .as(permiso).isEqualTo(List.of("SELECT", "INSERT", "UPDATE").contains(permiso));
        }
        assertThat(jdbc.queryForObject("select has_sequence_privilege(current_user,'invitacion_id_seq','USAGE, SELECT')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select has_sequence_privilege(current_user,'invitacion_id_seq','UPDATE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select has_function_privilege(current_user,'impedir_reasignacion_barbero()','EXECUTE')", Boolean.class)).isFalse();
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            var punto = conexion.setSavepoint();
            try {
                String insertar = """
                        insert into invitacion(negocio_id,correo,rol,token_hash,expira_en,invitada_por,creado_en,actualizado_en)
                        values (1,?, ?, decode(?,'hex'), CURRENT_TIMESTAMP + interval '7 days', ?, CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """;
                jdbc.update(insertar, "esquema@ejemplo.test", "BARBERO", "ab".repeat(32), usuario);
                record Caso(String correo, String rol, String hash, String estado, String restriccion) { }
                for (var caso : List.of(
                        new Caso("otro@ejemplo.test", "BARBERO", "ab".repeat(32), "23505", "invitacion_token_uk"),
                        new Caso("esquema@ejemplo.test", "BARBERO", "cd".repeat(32), "23505", "invitacion_pendiente_uk"),
                        new Caso("MAYUSCULAS@ejemplo.test", "BARBERO", "cd".repeat(32), "23514", "invitacion_correo_minusculas"),
                        new Caso("otro@ejemplo.test", "CLIENTE", "cd".repeat(32), "23514", "invitacion_rol_valido"))) {
                    var casoPunto = conexion.setSavepoint();
                    try {
                        var error = catchThrowableOfType(org.springframework.dao.DataAccessException.class,
                                () -> jdbc.update(insertar, caso.correo(), caso.rol(), caso.hash(), usuario));
                        assertThat(error).isNotNull();
                        var causa = (PSQLException) error.getMostSpecificCause();
                        assertThat(causa.getSQLState()).isEqualTo(caso.estado());
                        assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo(caso.restriccion());
                    } finally { conexion.rollback(casoPunto); conexion.releaseSavepoint(casoPunto); }
                }
                jdbc.update("update invitacion set estado='REVOCADA' where correo='esquema@ejemplo.test'");
                assertThat(jdbc.update(insertar, "esquema@ejemplo.test", "PROPIETARIO", "cd".repeat(32), usuario)).isEqualTo(1);
            } finally { conexion.rollback(punto); conexion.releaseSavepoint(punto); }
            return null;
        });
    }
}
