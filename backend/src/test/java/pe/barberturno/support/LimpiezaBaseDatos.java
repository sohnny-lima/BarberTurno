package pe.barberturno.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Limpieza exclusiva de las ocho tablas de negocio de la base de pruebas. */
public final class LimpiezaBaseDatos {

    private LimpiezaBaseDatos() {
    }

    /** Reinicia las identidades (reserva comienza en 100) y conserva el historial Flyway. */
    public static void limpiar(JdbcTemplate jdbc) {
        // Nunca ejecutar la utilidad contra la base de desarrollo o producción.
        if (!"barberturno_test".equals(jdbc.queryForObject("select current_database()", String.class))) {
            throw new IllegalStateException("La limpieza requiere la base barberturno_test.");
        }
        jdbc.execute("""
                TRUNCATE notificacion, auditoria_reserva, reserva, bloqueo,
                    jornada, barbero, servicio, usuario RESTART IDENTITY CASCADE
                """);
    }
}