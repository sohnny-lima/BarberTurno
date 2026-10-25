package pe.barberturno.common.tenancy;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Contrato SQL y permisos del registro global, compartidos por instalación y actualización. */
final class AuditoriaPlataformaEsquemaPruebas {
    private AuditoriaPlataformaEsquemaPruebas() { }

    static void comprobar(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(jdbc.queryForObject("select relrowsecurity or relforcerowsecurity from pg_class where oid='auditoria_plataforma'::regclass", Boolean.class)).isFalse();
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name='auditoria_plataforma' order by ordinal_position", String.class))
                .containsExactly("id", "actor_id", "accion", "negocio_id", "objetivo_id", "creado_en");
        for (String permiso : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'auditoria_plataforma',?)", Boolean.class, permiso))
                    .as("Auditoría %s", permiso).isEqualTo(List.of("SELECT", "INSERT").contains(permiso));
        }
        for (String permiso : List.of("INSERT", "UPDATE")) {
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'negocio',?)", Boolean.class, permiso)).isTrue();
        }
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'negocio','DELETE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForList("select conname from pg_constraint where conrelid='auditoria_plataforma'::regclass", String.class))
                .contains("auditoria_plataforma_actor_fk", "auditoria_plataforma_negocio_fk", "auditoria_plataforma_objetivo_fk",
                        "auditoria_plataforma_accion_valida", "auditoria_plataforma_destino_coherente");
        for (String restriccion : List.of("auditoria_plataforma_accion_valida", "auditoria_plataforma_destino_coherente")) {
            assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conrelid='auditoria_plataforma'::regclass and conname=?",
                    String.class, restriccion)).contains("REENVIAR_INVITACION_PROPIETARIO", "REVOCAR_INVITACION_PROPIETARIO");
        }
    }
}
