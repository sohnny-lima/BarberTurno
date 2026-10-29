package pe.barberturno.common.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.List;
import java.util.Map;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificación del esquema de salida de WhatsApp de V114 visto por el rol de la aplicación
 * {@code barberturno_v2_app} (12-v2-09-whatsapp-salida.md §3 y §6, CI-11).
 * Comprueba RLS habilitada y forzada en {@code conversacion} y {@code mensaje_whatsapp}, políticas
 * únicas {@code ALL} de aislamiento de negocio, permisos estrictos por columna y a nivel de tabla
 * (incluyendo denegación de DELETE, TRUNCATE, REFERENCES y TRIGGER), aislamiento sin contexto,
 * restricciones {@code CHECK} con validación de SQLState y nombre de restricción, claves foráneas
 * compuestas y definiciones completas de índices parciales del outbox.
 * No deja ningún estado persistente en la conexión ni en la base de datos: opera dentro de una
 * transacción o punto de recuperación local revertido al finalizar.
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
     * Ejecuta todas las comprobaciones del esquema de V114 contra la base de datos de pruebas sin dejar estado.
     * @param jdbc plantilla JDBC autenticada con el rol de la aplicación
     */
    public static void comprobar(JdbcTemplate jdbc) {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            boolean autoCommitOriginal = con.getAutoCommit();
            Savepoint puntoInicial = null;
            if (autoCommitOriginal) {
                con.setAutoCommit(false);
            } else {
                puntoInicial = con.setSavepoint();
            }
            var jdbcLocal = new JdbcTemplate(new SingleConnectionDataSource(con, true));
            try {
                ejecutarComprobaciones(con, jdbcLocal);
                return null;
            } finally {
                if (autoCommitOriginal) {
                    try {
                        con.rollback();
                    } finally {
                        con.setAutoCommit(true);
                    }
                } else {
                    con.rollback(puntoInicial);
                    try {
                        con.releaseSavepoint(puntoInicial);
                    } catch (SQLException ignored) {
                    }
                }
            }
        });
    }

    private static void ejecutarComprobaciones(Connection con, JdbcTemplate jdbc) throws SQLException {
        // 1. RLS y FORCE RLS en conversacion y mensaje_whatsapp
        for (String tabla : List.of("conversacion", "mensaje_whatsapp")) {
            assertThat(jdbc.queryForObject("select relrowsecurity and relforcerowsecurity from pg_class where oid=?::regclass",
                    Boolean.class, tabla)).as("%s con RLS FORCE", tabla).isTrue();

            // Verificación de política única ALL por tabla
            var politicas = jdbc.queryForList("select policyname, cmd, qual, with_check from pg_policies where schemaname='public' and tablename=?", tabla);
            assertThat(politicas).as("políticas de %s", tabla).hasSize(1);
            var pol = politicas.getFirst();
            assertThat(pol.get("cmd")).as("comando de política en %s", tabla).isEqualTo("ALL");
            assertThat(pol.get("qual").toString()).as("qual de %s", tabla).contains("negocio_id = app_negocio_actual()");
            assertThat(pol.get("with_check").toString()).as("with_check de %s", tabla).contains("negocio_id = app_negocio_actual()");

            assertThat(jdbc.queryForMap("select is_nullable, column_default from information_schema.columns where table_name=? and column_name='negocio_id'", tabla))
                    .containsEntry("is_nullable", "NO")
                    .containsEntry("column_default", "app_negocio_actual()");

            // Permisos a nivel de tabla: SELECT e INSERT permitidos; UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER denegados
            for (String permiso : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, ?, ?)", Boolean.class, tabla, permiso))
                        .as("%s %s", tabla, permiso).isEqualTo(List.of("SELECT", "INSERT").contains(permiso));
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

        // 2. Aislamiento sin contexto de negocio
        jdbc.execute("SELECT set_config('app.negocio_id', '', true)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversacion", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje_whatsapp", Integer.class)).isZero();
        Savepoint spAislamiento = con.setSavepoint();
        try {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO conversacion (wa_id, creado_en, actualizado_en)
                    VALUES ('51999000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """)).isInstanceOf(DataAccessException.class);
        } finally {
            con.rollback(spAislamiento);
            try {
                con.releaseSavepoint(spAislamiento);
            } catch (SQLException ignored) {
            }
        }

        // 3. FKs compuestas que involucran conversacion y mensaje_whatsapp
        List<String> fksCompuestas = jdbc.queryForList("""
                select conname from pg_constraint where contype='f' and array_length(conkey,1)=2
                """, String.class);
        assertThat(fksCompuestas).contains("conversacion_cliente_fk", "mensaje_conversacion_fk", "mensaje_reserva_fk");

        // 4. Restricciones con nombre en conversacion y mensaje_whatsapp
        List<String> restriccionesConversacion = jdbc.queryForList(
                "select conname from pg_constraint where conrelid='conversacion'::regclass", String.class);
        assertThat(restriccionesConversacion).contains("conversacion_negocio_id_uk", "conversacion_wa_uk",
                "conversacion_cliente_uk", "conversacion_wa_formato", "conversacion_modo_valido");

        List<String> restriccionesMensaje = jdbc.queryForList(
                "select conname from pg_constraint where conrelid='mensaje_whatsapp'::regclass", String.class);
        assertThat(restriccionesMensaje).contains("mensaje_direccion_valida", "mensaje_origen_valido",
                "mensaje_tipo_valido", "mensaje_estado_valido", "mensaje_enviado_como_valido", "mensaje_error_valido",
                "mensaje_intentos_validos", "mensaje_error_coherente", "mensaje_pendiente_programado",
                "mensaje_envio_coherente", "mensaje_entrada_coherente");

        // 5. Definiciones completas de índices de mensaje_whatsapp
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where tablename='mensaje_whatsapp' and indexname='mensaje_wamid_uk'", String.class))
                .as("definición de mensaje_wamid_uk")
                .contains("UNIQUE INDEX", "(wamid)", "WHERE (wamid IS NOT NULL)");

        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where tablename='mensaje_whatsapp' and indexname='mensaje_salida_cola_ix'", String.class))
                .as("definición de mensaje_salida_cola_ix")
                .contains("(negocio_id, proximo_intento, id)")
                .contains("PENDIENTE");

        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where tablename='mensaje_whatsapp' and indexname='mensaje_conversacion_pendiente_ix'", String.class))
                .as("definición de mensaje_conversacion_pendiente_ix")
                .contains("(negocio_id, conversacion_id, id)")
                .contains("PENDIENTE");

        // 6. Operaciones inválidas que comprueban los CHECK con SQLState 23514 y nombre de restricción
        jdbc.execute("SELECT set_config('app.negocio_id', '1', true)");
        Long convId = jdbc.queryForObject("""
                INSERT INTO conversacion (wa_id, creado_en, actualizado_en)
                VALUES ('51900000999', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (negocio_id, wa_id) DO UPDATE SET actualizado_en = CURRENT_TIMESTAMP
                RETURNING id
                """, Long.class);

        // a) conversacion_wa_formato
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO conversacion (wa_id, creado_en, actualizado_en)
                VALUES ('no-numerico', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """), "conversacion_wa_formato");

        // b) conversacion_modo_valido
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO conversacion (wa_id, modo, creado_en, actualizado_en)
                VALUES ('51900000998', 'INVALIDO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """), "conversacion_modo_valido");

        // c) mensaje_intentos_validos
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO mensaje_whatsapp (conversacion_id, direccion, origen, tipo, contenido, estado,
                    intentos, proximo_intento, creado_en, actualizado_en)
                VALUES (?, 'SALIENTE', 'SISTEMA', 'TEXTO', '{"texto":"ok"}'::jsonb, 'PENDIENTE',
                    -1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, convId), "mensaje_intentos_validos");

        // d) mensaje_error_coherente
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO mensaje_whatsapp (conversacion_id, direccion, origen, tipo, contenido, estado,
                    error_codigo, creado_en, actualizado_en)
                VALUES (?, 'SALIENTE', 'SISTEMA', 'TEXTO', '{"texto":"ok"}'::jsonb, 'FALLIDO',
                    null, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, convId), "mensaje_error_coherente");

        // e) mensaje_pendiente_programado
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO mensaje_whatsapp (conversacion_id, direccion, origen, tipo, contenido, estado,
                    proximo_intento, creado_en, actualizado_en)
                VALUES (?, 'SALIENTE', 'SISTEMA', 'TEXTO', '{"texto":"ok"}'::jsonb, 'PENDIENTE',
                    null, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, convId), "mensaje_pendiente_programado");

        // f) mensaje_envio_coherente
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO mensaje_whatsapp (conversacion_id, direccion, origen, tipo, contenido, estado,
                    wamid, enviado_como, creado_en, actualizado_en)
                VALUES (?, 'SALIENTE', 'SISTEMA', 'TEXTO', '{"texto":"ok"}'::jsonb, 'ENVIADO',
                    null, 'TEXTO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, convId), "mensaje_envio_coherente");

        // g) mensaje_entrada_coherente
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO mensaje_whatsapp (conversacion_id, direccion, origen, tipo, contenido, estado,
                    proximo_intento, creado_en, actualizado_en)
                VALUES (?, 'ENTRANTE', 'CLIENTE', 'TEXTO', '{"texto":"ok"}'::jsonb, 'PENDIENTE',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, convId), "mensaje_entrada_coherente");

        // h) mensaje_enviado_como_valido
        verificarRestriccion(con, () -> jdbc.update("""
                INSERT INTO mensaje_whatsapp (conversacion_id, direccion, origen, tipo, contenido, estado,
                    wamid, enviado_como, creado_en, actualizado_en)
                VALUES (?, 'SALIENTE', 'SISTEMA', 'TEXTO', '{"texto":"ok"}'::jsonb, 'ENVIADO',
                    'wamid.val', 'OTRO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, convId), "mensaje_enviado_como_valido");

        // 7. Ajuste de V2-08: clave_idempotencia de evento_whatsapp_entrante ampliada a 160 caracteres
        assertThat(jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_name='evento_whatsapp_entrante' and column_name='clave_idempotencia'
                """, Integer.class)).isEqualTo(160);
    }

    private static void verificarRestriccion(Connection con, Runnable accion, String restriccionEsperada) throws SQLException {
        Savepoint punto = con.setSavepoint();
        try {
            assertThatThrownBy(accion::run)
                    .isInstanceOf(DataAccessException.class)
                    .satisfies(fallo -> {
                        var causa = (PSQLException) ((DataAccessException) fallo).getMostSpecificCause();
                        assertThat(causa.getSQLState()).as("SQLState 23514 (check_violation)").isEqualTo("23514");
                        assertThat(causa.getServerErrorMessage().getConstraint()).as("nombre de restricción").isEqualTo(restriccionEsperada);
                    });
        } finally {
            con.rollback(punto);
            try {
                con.releaseSavepoint(punto);
            } catch (SQLException ignored) {
            }
        }
    }
}
