package pe.barberturno.common.tenancy;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Comprobaciones exactas de V117 reutilizadas en instalación y esquema en uso. */
final class ConversacionIAEsquemaPruebas {
    private ConversacionIAEsquemaPruebas() { }
    static void comprobar(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        var actualizables = Map.of(
                "turno_ia", Set.of("texto", "estado", "motivo", "intentos", "reclamado_hasta", "actualizado_en"),
                "llamada_ia", Set.of("entrada", "salida", "cache_leida", "cache_escrita", "coste_micro_usd", "estado"),
                "uso_ia", Set.of("llamadas", "entrada", "salida", "cache_leida", "cache_escrita", "coste_micro_usd"));
        for (var entrada : actualizables.entrySet()) {
            String tabla = entrada.getKey();
            assertThat(jdbc.queryForMap("select relrowsecurity,relforcerowsecurity from pg_class where oid=?::regclass", tabla))
                    .containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
            assertThat(jdbc.queryForObject("select count(*) from pg_policies where tablename=? and cmd='ALL' and qual is not null and with_check is not null", Long.class, tabla)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select column_default from information_schema.columns where table_name=? and column_name='negocio_id'", String.class, tabla)).isEqualTo("app_negocio_actual()");
            for (String privilegio : List.of("SELECT", "INSERT", "DELETE", "UPDATE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                boolean permitido = List.of("SELECT", "INSERT").contains(privilegio) || "DELETE".equals(privilegio) && !tabla.equals("uso_ia");
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,?)", Boolean.class, tabla, privilegio))
                        .as("%s %s", privilegio, tabla).isEqualTo(permitido);
            }
            for (String columna : jdbc.queryForList("select column_name from information_schema.columns where table_name=?", String.class, tabla))
                assertThat(jdbc.queryForObject("select has_column_privilege(current_user,?,?,'UPDATE')", Boolean.class, tabla, columna))
                        .as("UPDATE %s.%s", tabla, columna).isEqualTo(entrada.getValue().contains(columna));
        }
        assertThat(jdbc.queryForObject("select count(*) from pg_policies where tablename='turno_ia' and cmd='DELETE' and permissive='RESTRICTIVE'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select has_function_privilege(current_user,'incrementar_revision_conversacion()','EXECUTE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select has_column_privilege(current_user,'conversacion','revision','UPDATE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='uso_ia' and column_name='conversacion_id'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_name='conversacion' and column_name='entradas_minuto'", String.class)).isEqualTo("smallint");
    }
}
