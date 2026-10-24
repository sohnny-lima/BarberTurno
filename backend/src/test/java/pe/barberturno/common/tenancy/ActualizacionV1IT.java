package pe.barberturno.common.tenancy;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class ActualizacionV1IT extends MigracionPruebaBase {
    private static final List<String> TABLAS = List.of("usuario", "servicio", "barbero", "jornada", "bloqueo", "reserva", "auditoria_reserva", "notificacion");

    @Test
    void datosV1_conservaHuellasIdentidadesYExclusionesYAislaLasRelaciones() throws Exception {
        var completa = flyway(null);
        completa.clean();
        var ultimaV1 = ultimaV1(completa);
        var v1 = flyway(ultimaV1.toString());
        v1.migrate();
        assertThat(v1.info().current().getVersion()).isEqualTo(ultimaV1);
        assertThat(v1.info().pending()).isEmpty();
        DatosNegocioPrueba a;
        Map<String, String> huellas;
        Map<String, Long> maximos = new LinkedHashMap<>();
        try (var propietario = conexion(true)) {
            var jdbc = jdbc(propietario);
            // Antes de V100 el propietario representa a la aplicación v1 sin RLS.
            a = DatosNegocioPrueba.crear(jdbc, "actualizacion");
            var estados = List.of("PENDIENTE", "EN_ATENCION", "COMPLETADA", "NO_ASISTIO", "CANCELADA");
            for (int i = 0; i < estados.size(); i++) {
                // Cancelada solapada y las demás contiguas, sin modificar las EXCLUDE de v1.
                DatosNegocioPrueba.reservar(jdbc, a.cliente(), a.admin(), a.barbero(), a.servicio(), estados.get(i), i == 4 ? 0 : (i + 1) * 30);
            }
            long profesional = jdbc.queryForObject("""
                    INSERT INTO usuario (nombre, correo, password_hash, rol, creado_en, actualizado_en)
                    VALUES ('Barbero ficticio', 'barbero-v1@ejemplo.test', 'hash-ficticio', 'BARBERO',
                        '2026-09-28T14:00Z', '2026-09-28T14:00Z') RETURNING id
                    """, Long.class);
            long segundoBarbero = jdbc.queryForObject("INSERT INTO barbero (usuario_id, creado_en, actualizado_en) VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id", Long.class, profesional);
            jdbc.update("INSERT INTO jornada (barbero_id, dia_semana, hora_inicio, hora_fin) VALUES (?, 1, '09:00', '13:00')", segundoBarbero);
            jdbc.update("""
                    INSERT INTO usuario (nombre, correo, telefono, password_hash, rol, privacidad_aceptada_en, creado_en, actualizado_en)
                    VALUES ('Otra persona ficticia', 'cliente-dos-v1@ejemplo.test', '999000002', 'hash-ficticio',
                        'CLIENTE', '2026-09-28T14:00Z', '2026-09-28T14:00Z', '2026-09-28T14:00Z')
                    """);
            jdbc.update("""
                    INSERT INTO servicio (nombre, duracion_min, precio, creado_en, actualizado_en)
                    VALUES ('Barba de v1', 20, 15, '2026-09-28T14:00Z', '2026-09-28T14:00Z')
                    """);
            jdbc.update("update usuario set activo=false where correo='cliente-dos-v1@ejemplo.test'");
            huellas = huellas(jdbc);
            for (String tabla : TABLAS) maximos.put(tabla, jdbc.queryForObject("select max(id) from " + tabla, Long.class));
        }
        flyway("102").migrate();
        try (var aplicacion = conexion(false)) {
            contexto(aplicacion, 1L);
            // Reproduce la pérdida de ámbito del relleno original antes de la corrección aditiva.
            assertThat(jdbc(aplicacion).queryForObject("select count(*) from usuario u join cliente c on c.usuario_id=u.id where u.correo='cliente-dos-v1@ejemplo.test' and c.activo", Integer.class)).isZero();
            aplicacion.commit();
        }
        completa.migrate();
        comprobarMigracionCompleta(completa);
        long b;
        // Excepción de plataforma de V2-DA-20: solo la fila negocio B, nunca sus datos de negocio.
        try (var propietario = conexion(true); var consulta = propietario.createStatement(); var resultado = consulta.executeQuery("""
                INSERT INTO negocio (slug, nombre, creado_en, actualizado_en)
                VALUES ('negocio-b', 'Negocio B ficticio', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id
                """)) {
            resultado.next();
            b = resultado.getLong(1);
        }
        DatosNegocioPrueba datosB;
        try (var aplicacion = conexion(false)) {
            var jdbc = jdbc(aplicacion);
            contexto(aplicacion, 1L);
            assertThat(huellas(jdbc)).isEqualTo(huellas);
            assertThat(jdbc.queryForObject("select tolerancia_inicio_min from negocio where id=1", Integer.class)).isEqualTo(15);
            assertThat(jdbc.queryForObject("select count(*) from barbero b join usuario u on u.id=b.usuario_id where b.nombre_visible is distinct from u.nombre or b.telefono_contacto is distinct from u.telefono", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from barbero", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from cliente", Integer.class))
                    .isEqualTo(jdbc.queryForObject("select count(*) from usuario where rol='CLIENTE'", Integer.class));
            assertThat(jdbc.queryForObject("select count(*) from cliente c join usuario u on u.id=c.usuario_id where c.id<>u.id or c.nombre<>u.nombre or c.telefono is distinct from u.telefono or not c.activo or c.privacidad_aceptada_en is distinct from u.privacidad_aceptada_en or c.creado_en<>u.creado_en or c.actualizado_en<>u.actualizado_en", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from usuario u join cliente c on c.usuario_id=u.id where u.correo='cliente-dos-v1@ejemplo.test' and not u.activo and c.activo", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from reserva r join cliente c on c.id=r.cliente_id where c.id<>c.usuario_id", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select nextval(pg_get_serial_sequence('cliente', 'id'))", Long.class))
                    .isGreaterThan(jdbc.queryForObject("select max(id) from cliente", Long.class));
            assertThat(jdbc.queryForObject("select count(*) from membresia", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from membresia m join usuario u on u.id=m.usuario_id where m.negocio_id=1 and m.estado='ACTIVA' and m.rol=case u.rol when 'ADMIN' then 'PROPIETARIO' else 'BARBERO' end and u.rol in ('ADMIN','BARBERO')", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForList("select distinct rol_plataforma from usuario", String.class)).containsExactly("NINGUNO");
            IdentidadEsquemaPruebas.comprobar(jdbc, a.cliente());
            for (String tabla : EsquemaV2IT.TABLAS) {
                assertThat(jdbc.queryForList("select distinct negocio_id from " + tabla, Long.class)).as(tabla).containsExactly(1L);
            }
            assertThat(jdbc.queryForList("select distinct estado from reserva", String.class)).containsExactlyInAnyOrder(
                    "PENDIENTE", "CONFIRMADA", "EN_ATENCION", "COMPLETADA", "CANCELADA", "NO_ASISTIO");
            for (String tabla : TABLAS) {
                assertThat(jdbc.queryForObject("select nextval(pg_get_serial_sequence(?, 'id'))", Long.class, tabla))
                        .as("identidad de " + tabla).isGreaterThan(maximos.get(tabla));
            }
            aplicacion.commit();
            for (String tabla : EsquemaV2IT.TABLAS) assertThat(jdbc.queryForObject("select count(*) from " + tabla, Integer.class)).as(tabla).isZero();
            contexto(aplicacion, b);
            datosB = DatosNegocioPrueba.crear(jdbc, "b");
            aplicacion.commit();
            contexto(aplicacion, 1L);
            assertThat(huellas(jdbc).entrySet()).containsAll(huellas.entrySet().stream().filter(e -> !e.getKey().equals("usuario")).toList());
            aplicacion.rollback();

            rechazar(aplicacion, "23P01", "reserva_sin_solape_barbero", () ->
                    DatosNegocioPrueba.reservar(jdbc, a.cliente(), a.admin(), a.barbero(), a.servicio(), "CONFIRMADA", 0));
            rechazar(aplicacion, "23503", "reserva_barbero_fk", () ->
                    DatosNegocioPrueba.reservar(jdbc, a.cliente(), a.admin(), datosB.barbero(), a.servicio(), "CANCELADA", 0));
            rechazar(aplicacion, "23503", "reserva_servicio_fk", () ->
                    DatosNegocioPrueba.reservar(jdbc, a.cliente(), a.admin(), a.barbero(), datosB.servicio(), "CANCELADA", 0));
            rechazar(aplicacion, "23503", "reserva_cliente_fk", () ->
                    DatosNegocioPrueba.reservar(jdbc, datosB.ficha(), a.admin(), a.barbero(), a.servicio(), "CANCELADA", 0));
            rechazar(aplicacion, "23505", "cliente_usuario_uk", () -> jdbc.update("""
                    INSERT INTO cliente (usuario_id, nombre, creado_en, actualizado_en)
                    VALUES (?, 'Ficha duplicada', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, a.cliente()));
            rechazar(aplicacion, "23503", "jornada_barbero_fk", () -> jdbc.update("INSERT INTO jornada (barbero_id, dia_semana, hora_inicio, hora_fin) VALUES (?, 1, '09:00', '18:00')", datosB.barbero()));
            rechazar(aplicacion, "23503", "bloqueo_barbero_fk", () -> jdbc.update("""
                    INSERT INTO bloqueo (barbero_id, inicio, fin, motivo, creado_por, creado_en)
                    VALUES (?, '2026-10-01T17:00-05:00', '2026-10-01T17:30-05:00', 'Prueba', ?, CURRENT_TIMESTAMP)
                    """, datosB.barbero(), a.admin()));
            rechazar(aplicacion, "23503", "auditoria_reserva_reserva_fk", () -> jdbc.update("""
                    INSERT INTO auditoria_reserva (reserva_id, actor_id, accion, estado_nuevo, datos_nuevos, creado_en)
                    VALUES (?, ?, 'CREAR', 'CONFIRMADA', '{}', CURRENT_TIMESTAMP)
                    """, datosB.reserva(), a.admin()));
            rechazar(aplicacion, "23503", "notificacion_reserva_fk", () -> jdbc.update("""
                    INSERT INTO notificacion (usuario_id, reserva_id, tipo, mensaje, creado_en)
                    VALUES (?, ?, 'CREAR', 'Prueba', CURRENT_TIMESTAMP)
                    """, a.cliente(), datosB.reserva()));
        }
    }

    @Test void reservaConCuentaNoCliente_laMigracionFallaSinCorregirEnSilencio() throws Exception {
        var completa = flyway(null);
        completa.clean();
        flyway(ultimaV1(completa).toString()).migrate();
        try (var propietario = conexion(true)) {
            var jdbc = jdbc(propietario);
            var datos = DatosNegocioPrueba.crear(jdbc, "invalida");
            jdbc.update("update reserva set cliente_id=? where id=?", datos.admin(), datos.reserva());
        }
        assertThatThrownBy(completa::migrate).hasStackTraceContaining("V102: hay reservas cuyo cliente no es una cuenta CLIENTE");
    }

    private static Map<String, String> huellas(JdbcTemplate jdbc) {
        var resultado = new LinkedHashMap<String, String>();
        for (String tabla : TABLAS) {
            resultado.put(tabla, jdbc.queryForObject("select count(*)::text || ':' || md5(string_agg((to_jsonb(t) - 'negocio_id' - 'rol_plataforma' - 'nombre_visible' - 'telefono_contacto')::text, ',' order by id)) from " + tabla + " t", String.class));
        }
        return resultado;
    }

    private static void rechazar(Connection aplicacion, String estado, String restriccion, Runnable accion) throws Exception {
        contexto(aplicacion, 1L);
        try {
            var error = catchThrowableOfType(DataAccessException.class, accion::run);
            assertThat(error).isNotNull();
            var causa = (PSQLException) error.getMostSpecificCause();
            assertThat(causa.getSQLState()).isEqualTo(estado);
            assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo(restriccion);
        } finally {
            aplicacion.rollback();
        }
    }
}
