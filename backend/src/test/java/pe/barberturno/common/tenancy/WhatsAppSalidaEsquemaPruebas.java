package pe.barberturno.common.tenancy;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificación del esquema de salida de WhatsApp de V114 visto por el rol de la aplicación
 * {@code barberturno_v2_app} (12-v2-09-whatsapp-salida.md §3 y §6, CI-11).
 * Comprueba RLS habilitada y forzada en {@code conversacion} y {@code mensaje_whatsapp}, políticas
 * de aislamiento de negocio, permisos estrictos por columna (sin DELETE), restricciones con nombre,
 * claves foráneas compuestas e índices parciales del outbox.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class WhatsAppSalidaEsquemaPruebas {

    private WhatsAppSalidaEsquemaPruebas() { }

    private static final Map<String, List<String>> ACTUALIZABLES = Map.of(
            "conversacion", List.of("ultima_entrada_en", "actualizado_en"),
            "mensaje_whatsapp", List.of("wamid", "estado", "intentos", "inciertos",
                    "proximo_intento", "error_codigo", "codigo_meta", "enviado_como", "actualizado_en"));

    private static final Map<String, List<String>> FIJAS = Map.of(
            "conversacion", List.of("id", "negocio_id", "wa_id", "cliente_id", "modo", "estado", "version", "creado_en"),
            "mensaje_whatsapp", List.of("id", "negocio_id", "conversacion_id", "direccion", "origen",
                    "tipo", "contenido", "reserva_id", "creado_en"));

    /**
     * Ejecuta todas las comprobaciones del esquema de V114 contra la base de datos de pruebas.
     * @param jdbc plantilla JDBC autenticada con el rol de la aplicación
     */
    public static void comprobar(JdbcTemplate jdbc) {
        // 1. RLS y FORCE RLS en conversacion y mensaje_whatsapp
        for (String tabla : List.of("conversacion", "mensaje_whatsapp")) {
            assertThat(jdbc.queryForObject("select relrowsecurity and relforcerowsecurity from pg_class where oid=?::regclass",
                    Boolean.class, tabla)).as("%s con RLS FORCE", tabla).isTrue();

            var politicas = jdbc.queryForList("select cmd, qual, with_check from pg_policies where schemaname='public' and tablename=?", tabla);
            assertThat(politicas).as("políticas de %s", tabla).isNotEmpty();
            assertThat(politicas.getFirst().get("qual").toString()).contains("negocio_id = app_negocio_actual()");
            assertThat(politicas.getFirst().get("with_check").toString()).contains("negocio_id = app_negocio_actual()");

            assertThat(jdbc.queryForMap("select is_nullable, column_default from information_schema.columns where table_name=? and column_name='negocio_id'", tabla))
                    .containsEntry("is_nullable", "NO")
                    .containsEntry("column_default", "app_negocio_actual()");

            // Permisos a nivel de tabla: SELECT e INSERT permitidos; UPDATE, DELETE, TRUNCATE denegados
            for (String permiso : List.of("SELECT", "INSERT")) {
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, ?, ?)", Boolean.class, tabla, permiso))
                        .as("%s %s permitido", tabla, permiso).isTrue();
            }
            for (String permiso : List.of("UPDATE", "DELETE", "TRUNCATE")) {
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, ?, ?)", Boolean.class, tabla, permiso))
                        .as("%s a nivel de tabla %s denegado", tabla, permiso).isFalse();
            }

            // Permisos por columna
            for (String columna : ACTUALIZABLES.get(tabla)) {
                assertThat(jdbc.queryForObject("select has_column_privilege(current_user, ?, ?, 'UPDATE')", Boolean.class, tabla, columna))
                        .as("UPDATE %s.%s permitido", tabla, columna).isTrue();
            }
            for (String columna : FIJAS.get(tabla)) {
                assertThat(jdbc.queryForObject("select has_column_privilege(current_user, ?, ?, 'UPDATE')", Boolean.class, tabla, columna))
                        .as("UPDATE %s.%s denegado", tabla, columna).isFalse();
            }
        }

        // 2. FKs compuestas que involucran conversacion y mensaje_whatsapp
        List<String> fksCompuestas = jdbc.queryForList("""
                select conname from pg_constraint where contype='f' and array_length(conkey,1)=2
                """, String.class);
        assertThat(fksCompuestas).contains("conversacion_cliente_fk", "mensaje_conversacion_fk", "mensaje_reserva_fk");

        // 3. Restricciones e índices de conversacion
        List<String> restriccionesConversacion = jdbc.queryForList(
                "select conname from pg_constraint where conrelid='conversacion'::regclass", String.class);
        assertThat(restriccionesConversacion).contains("conversacion_negocio_id_uk", "conversacion_wa_uk", "conversacion_cliente_uk");

        // 4. Índices de mensaje_whatsapp
        List<String> indicesMensaje = jdbc.queryForList(
                "select indexname from pg_indexes where tablename='mensaje_whatsapp'", String.class);
        assertThat(indicesMensaje).contains("mensaje_wamid_uk", "mensaje_salida_cola_ix", "mensaje_conversacion_pendiente_ix");

        // 5. Ajuste de V2-08: clave_idempotencia de evento_whatsapp_entrante ampliada a 160 caracteres
        assertThat(jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_name='evento_whatsapp_entrante' and column_name='clave_idempotencia'
                """, Integer.class)).isEqualTo(160);
    }
}
