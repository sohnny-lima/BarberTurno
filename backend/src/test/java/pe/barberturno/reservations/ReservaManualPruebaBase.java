package pe.barberturno.reservations;

import java.sql.DriverManager;
import org.junit.jupiter.api.BeforeEach;
import pe.barberturno.support.*;

/** Conserva los escenarios de confirmación manual con parámetros en B y sin modificar el negocio A. */
abstract class ReservaManualPruebaBase extends ReservaPruebaBase {
    @Override
    @BeforeEach void preparar() {
        LimpiezaBaseDatos.limpiar(jdbc);
        var entorno = jdbc.entorno();
        try (var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"))) {
            if (!"barberturno_v2_test".equals(conexion.getCatalog())) throw new IllegalStateException("Solo v2_test.");
            try (var sentencia = conexion.createStatement()) {
                sentencia.execute("insert into negocio (id, slug, nombre, confirmacion_manual, creado_en, actualizado_en) overriding system value values (2, 'manual-b', 'Negocio B ficticio', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            }
        } catch (java.sql.SQLException error) { throw new IllegalStateException("No se pudo preparar el negocio B.", error); }
        reloj.reiniciar();
        SqlPruebas.limpiar();
        datos = new DatosPrueba(usuarios, barberos, servicios, membresias, clientes, 2L);
        cliente = datos.cliente("cliente");
        barbero = perfil("uno");
        servicio = datos.servicio();
    }
}
