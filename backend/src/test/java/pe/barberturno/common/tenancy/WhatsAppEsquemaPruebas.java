package pe.barberturno.common.tenancy;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Esquema de WhatsApp entrante y canal visto por el rol de la aplicación: tablas de plataforma sin RLS, permisos por columna (con UPDATE de cuerpo en V115) y restricciones con nombre. */
final class WhatsAppEsquemaPruebas {
    private WhatsAppEsquemaPruebas() { }

    static final Map<String, List<String>> ACTUALIZABLES = Map.of(
            "canal_whatsapp", List.of("waba_id", "phone_number_id", "numero_visible", "estado", "actualizado_en"),
            "evento_whatsapp_entrante", List.of("cuerpo", "negocio_id", "estado", "motivo", "intentos", "proximo_intento", "procesado_en"));
    static final Map<String, List<String>> FIJAS = Map.of(
            "canal_whatsapp", List.of("id", "negocio_id", "token_cifrado", "creado_en"),
            "evento_whatsapp_entrante", List.of("id", "clave_idempotencia", "tipo", "phone_number_id", "recibido_en"));

    static void comprobar(JdbcTemplate jdbc) {
        for (String tabla : ACTUALIZABLES.keySet()) {
            assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid=?::regclass", Boolean.class, tabla))
                    .as("%s sin RLS", tabla).isFalse();
            for (String permiso : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, ?, ?)", Boolean.class, tabla, permiso))
                        .as("%s %s", tabla, permiso).isEqualTo(List.of("SELECT", "INSERT").contains(permiso));
            }
            for (String columna : ACTUALIZABLES.get(tabla)) {
                assertThat(jdbc.queryForObject("select has_column_privilege(current_user, ?, ?, 'UPDATE')", Boolean.class, tabla, columna))
                        .as("UPDATE %s.%s", tabla, columna).isTrue();
            }
            for (String columna : FIJAS.get(tabla)) {
                assertThat(jdbc.queryForObject("select has_column_privilege(current_user, ?, ?, 'UPDATE')", Boolean.class, tabla, columna))
                        .as("UPDATE %s.%s", tabla, columna).isFalse();
            }
        }
        assertThat(jdbc.queryForList("select conname from pg_constraint where conrelid='canal_whatsapp'::regclass", String.class))
                .contains("canal_whatsapp_negocio_fk", "canal_whatsapp_negocio_uk", "canal_whatsapp_estado_valido",
                        "canal_whatsapp_waba_formato", "canal_whatsapp_numero_formato", "canal_whatsapp_visible_formato");
        assertThat(jdbc.queryForList("select conname from pg_constraint where conrelid='evento_whatsapp_entrante'::regclass", String.class))
                .contains("evento_whatsapp_negocio_fk", "evento_whatsapp_tipo_valido", "evento_whatsapp_estado_valido",
                        "evento_whatsapp_motivo_valido", "evento_whatsapp_motivo_coherente", "evento_whatsapp_intentos_validos");
        assertThat(jdbc.queryForList("select indexname from pg_indexes where tablename in ('canal_whatsapp','evento_whatsapp_entrante','cliente')", String.class))
                .contains("canal_whatsapp_numero_uk", "evento_whatsapp_clave_uk", "evento_whatsapp_cola_ix", "cliente_wa_uk");
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conname='cliente_identificable'", String.class))
                .contains("wa_id IS NOT NULL");
        for (String restriccion : List.of("auditoria_plataforma_accion_valida", "auditoria_plataforma_destino_coherente")) {
            assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conname=?", String.class, restriccion))
                    .contains("REGISTRAR_CANAL_WHATSAPP", "DESCONECTAR_CANAL_WHATSAPP");
        }
    }
}
