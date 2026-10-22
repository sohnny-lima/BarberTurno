package pe.barberturno.support;

import java.sql.DriverManager;

/** Limpieza como propietario, limitada a la base v2 de pruebas y conservando el negocio único. */
public final class LimpiezaBaseDatos {

    private LimpiezaBaseDatos() {
    }

    /** Reinicia las identidades (reserva comienza en 100) y conserva el historial Flyway. */
    public static void limpiar(JdbcNegocioPruebas jdbc) {
        // Nunca ejecutar la utilidad contra la base de desarrollo o producción.
        if (!"barberturno_v2_test".equals(jdbc.queryForObject("select current_database()", String.class))) {
            throw new IllegalStateException("La limpieza requiere la base barberturno_v2_test.");
        }
        var entorno = jdbc.entorno();
        try (var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"),
                entorno.getRequiredProperty("spring.flyway.password")); var consulta = conexion.createStatement()) {
            if (!"barberturno_v2_test".equals(conexion.getCatalog())) {
                throw new IllegalStateException("Destino de limpieza no autorizado.");
            }
            consulta.execute("""
                TRUNCATE notificacion, auditoria_reserva, reserva, bloqueo,
                    jornada, barbero, servicio, usuario RESTART IDENTITY CASCADE
                """);
            consulta.execute("DELETE FROM negocio WHERE id <> 1");
        } catch (java.sql.SQLException fallo) {
            throw new IllegalStateException("No se pudo limpiar la base v2 de pruebas.", fallo);
        }
    }
}
