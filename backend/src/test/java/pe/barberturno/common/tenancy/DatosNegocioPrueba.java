package pe.barberturno.common.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;

/** Escenario SQL completo reutilizado en aislamiento y en la actualización desde v1. */
record DatosNegocioPrueba(long cliente, long admin, long barbero, long servicio, long reserva, long ficha) {
    static DatosNegocioPrueba crear(JdbcTemplate jdbc, String sufijo) {
        long cliente = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, correo, telefono, password_hash, rol, privacidad_aceptada_en,
                    creado_en, actualizado_en) VALUES ('Cliente ficticio', ?, '999000001', 'hash-ficticio',
                    'CLIENTE', '2026-09-28T14:00Z', '2026-09-28T14:00Z', '2026-09-28T14:00Z') RETURNING id
                """, Long.class, "cliente-" + sufijo + "@ejemplo.test");
        long admin = jdbc.queryForObject("""
                INSERT INTO usuario (nombre, correo, password_hash, rol, creado_en, actualizado_en)
                VALUES ('Profesional ficticio', ?, 'hash-ficticio', 'ADMIN',
                    '2026-09-28T14:00Z', '2026-09-28T14:00Z') RETURNING id
                """, Long.class, "admin-" + sufijo + "@ejemplo.test");
        long barbero = jdbc.queryForObject("""
                INSERT INTO barbero (usuario_id, creado_en, actualizado_en)
                VALUES (?, '2026-09-28T14:00Z', '2026-09-28T14:00Z') RETURNING id
                """, Long.class, admin);
        long servicio = jdbc.queryForObject("""
                INSERT INTO servicio (nombre, duracion_min, precio, creado_en, actualizado_en)
                VALUES ('Corte homónimo', 30, 20, '2026-09-28T14:00Z', '2026-09-28T14:00Z') RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO jornada (barbero_id, dia_semana, hora_inicio, hora_fin) VALUES (?, 4, '09:00', '18:00')", barbero);
        jdbc.update("""
                INSERT INTO bloqueo (barbero_id, inicio, fin, motivo, creado_por, creado_en)
                VALUES (?, '2026-10-01T17:00-05:00', '2026-10-01T17:30-05:00', 'Prueba', ?, '2026-09-28T14:00Z')
                """, barbero, admin);
        long ficha = cliente;
        if (jdbc.queryForObject("select to_regclass('public.cliente') is not null", Boolean.class)) {
            ficha = jdbc.queryForObject("""
                    INSERT INTO cliente (usuario_id, nombre, telefono, privacidad_aceptada_en, creado_en, actualizado_en)
                    SELECT id, nombre, telefono, privacidad_aceptada_en, creado_en, actualizado_en FROM usuario WHERE id=? RETURNING id
                    """, Long.class, cliente);
        }
        long reserva = reservar(jdbc, ficha, admin, barbero, servicio, "CONFIRMADA", 0);
        return new DatosNegocioPrueba(cliente, admin, barbero, servicio, reserva, ficha);
    }

    static long reservar(JdbcTemplate jdbc, long cliente, long admin, long barbero, long servicio, String estado, int minutos) {
        long reserva = jdbc.queryForObject("""
                INSERT INTO reserva (cliente_id, barbero_id, servicio_id, inicio, fin, duracion_ref_min,
                    precio_ref, estado, creada_por, creado_en, actualizado_en)
                VALUES (?, ?, ?, '2026-10-01T10:00-05:00'::timestamptz + make_interval(mins => ?),
                    '2026-10-01T10:30-05:00'::timestamptz + make_interval(mins => ?), 30, 20, ?, ?,
                    '2026-09-28T14:00Z', '2026-09-28T14:00Z') RETURNING id
                """, Long.class, cliente, barbero, servicio, minutos, minutos, estado, admin);
        jdbc.update("""
                INSERT INTO auditoria_reserva (reserva_id, actor_id, accion, estado_nuevo, datos_nuevos, creado_en)
                VALUES (?, ?, 'CREAR', ?, '{}', '2026-09-28T14:00Z')
                """, reserva, admin, estado);
        long cuenta = jdbc.queryForObject("select to_regclass('public.cliente') is not null", Boolean.class)
                ? jdbc.queryForObject("select usuario_id from cliente where id=?", Long.class, cliente) : cliente;
        jdbc.update("""
                INSERT INTO notificacion (usuario_id, reserva_id, tipo, mensaje, creado_en)
                VALUES (?, ?, 'CREAR', 'Aviso de prueba', '2026-09-28T14:00Z')
                """, cuenta, reserva);
        return reserva;
    }
}
