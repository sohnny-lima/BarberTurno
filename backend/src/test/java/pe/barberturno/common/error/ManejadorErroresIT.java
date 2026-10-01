package pe.barberturno.common.error;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ManejadorErroresIT {
    @Autowired
    private DataSource dataSource;

    @ParameterizedTest
    @CsvSource({
        "barbero,23P01,reserva_sin_solape_barbero,FRANJA_NO_DISPONIBLE",
        "cliente,23P01,reserva_sin_solape_cliente,CLIENTE_CON_RESERVA_SOLAPADA",
        "correo,23505,usuario_correo_uk,CORREO_DUPLICADO",
        "nombre,23505,servicio_nombre_uk,NOMBRE_DUPLICADO"
    })
    void postgres_metadatosReales_traduceRestriccionesSinExponerlas(
            String caso, String estado, String restriccion, ErrorCodigo codigo) throws Exception {
        // Solo tablas temporales privadas y rollback; no cambia datos ni el DDL de V1.
        SQLException error;
        try (Connection conexion = dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try (var sentencia = conexion.createStatement()) {
                sentencia.execute("""
                        CREATE TEMP TABLE t08_solape (
                          barbero integer, cliente integer, franja tstzrange,
                          CONSTRAINT reserva_sin_solape_barbero EXCLUDE USING gist (barbero WITH =, franja WITH &&),
                          CONSTRAINT reserva_sin_solape_cliente EXCLUDE USING gist (cliente WITH =, franja WITH &&)
                        ) ON COMMIT DROP
                        """);
                sentencia.execute("""
                        INSERT INTO t08_solape VALUES (1, 1,
                          tstzrange('2026-10-01T10:00:00-05:00', '2026-10-01T10:30:00-05:00', '[)'))
                        """);
                sentencia.execute("""
                        CREATE TEMP TABLE t08_unico (
                          correo text CONSTRAINT usuario_correo_uk UNIQUE,
                          nombre text CONSTRAINT servicio_nombre_uk UNIQUE
                        ) ON COMMIT DROP
                        """);
                sentencia.execute("INSERT INTO t08_unico VALUES ('cliente@ejemplo.test', 'Corte de prueba')");
                String sql = switch (caso) {
                    case "barbero" -> "INSERT INTO t08_solape SELECT 1, 2, franja FROM t08_solape";
                    case "cliente" -> "INSERT INTO t08_solape SELECT 2, 1, franja FROM t08_solape";
                    case "correo" -> "INSERT INTO t08_unico VALUES ('cliente@ejemplo.test', 'Otro corte')";
                    case "nombre" -> "INSERT INTO t08_unico VALUES ('otro@ejemplo.test', 'Corte de prueba')";
                    default -> throw new IllegalArgumentException("Caso de prueba desconocido.");
                };
                error = catchThrowableOfType(SQLException.class, () -> sentencia.execute(sql));
                assertThat((Throwable) error).isNotNull();
                assertThat(error.getSQLState()).isEqualTo(estado);
            } finally {
                conexion.rollback();
            }
        }
        var controlador = new ManejadorErroresTest.ControladorPrueba();
        controlador.error = new DataIntegrityViolationException("Violación de prueba.", error);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new ManejadorErrores()).build();
        var resultado = mvc.perform(get("/prueba/error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value(codigo.name())).andReturn();
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("Exception", "org.", "SQL", restriccion, "cliente@ejemplo.test", "Corte de prueba");
    }
}
