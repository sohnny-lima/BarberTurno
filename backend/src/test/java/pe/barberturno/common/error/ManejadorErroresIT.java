package pe.barberturno.common.error;

import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.reservations.*;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.support.DatosPrueba;
import pe.barberturno.users.UsuarioRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class ManejadorErroresIT {
    @org.springframework.beans.factory.annotation.Autowired pe.barberturno.customers.ClienteRepository clientes;
    @org.springframework.beans.factory.annotation.Autowired pe.barberturno.platform.MembresiaRepository membresias;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager gestor;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private BarberoRepository barberos;
    @Autowired private ServicioRepository servicios;
    @Autowired private ReservaRepository reservas;

    @ParameterizedTest
    @CsvSource({
        "barbero,23P01,reserva_sin_solape_barbero,FRANJA_NO_DISPONIBLE",
        "cliente,23P01,reserva_sin_solape_cliente,CLIENTE_CON_RESERVA_SOLAPADA",
        "correo,23505,usuario_correo_uk,CORREO_DUPLICADO",
        "nombre,23505,servicio_nombre_uk,NOMBRE_DUPLICADO"
    })
    void postgres_metadatosReales_traduceRestriccionesSinExponerlas(
            String caso, String estado, String restriccion, ErrorCodigo codigo) throws Exception {
        // Restricciones reales con el rol operativo y el contexto de Spring; sin DDL ni TEMP.
        SQLException error = new TransactionTemplate(gestor).execute(tx -> {
            tx.setRollbackOnly();
            var datos = new DatosPrueba(usuarios, barberos, servicios, membresias, clientes);
            var cliente = datos.cliente("cliente");
            var otroCliente = datos.cliente("otro-cliente");
            var barbero = datos.barbero("barbero");
            var otroBarbero = datos.barbero("otro-barbero");
            var servicio = datos.servicio();
            var reserva = reservas.saveAndFlush(new Reserva(clientes.findByUsuarioId(cliente.getId()).orElseThrow(), barbero, servicio, DatosPrueba.AHORA,
                    EstadoReserva.CONFIRMADA, cliente, DatosPrueba.AHORA));
            return jdbc.execute((ConnectionCallback<SQLException>) conexion -> {
                try (var sentencia = conexion.createStatement()) {
                    String copiarReserva = """
                            INSERT INTO reserva (cliente_id, barbero_id, servicio_id, inicio, fin,
                                duracion_ref_min, precio_ref, estado, creada_por, creado_en, actualizado_en)
                            SELECT %d, %d, servicio_id, inicio, fin, duracion_ref_min, precio_ref, estado,
                                creada_por, creado_en, actualizado_en FROM reserva WHERE id=%d
                            """;
                    String sql = switch (caso) {
                        case "barbero" -> copiarReserva.formatted(clientes.buscarIdPorUsuario(otroCliente.getId()).orElseThrow(), barbero.getId(), reserva.getId());
                        case "cliente" -> copiarReserva.formatted(clientes.buscarIdPorUsuario(cliente.getId()).orElseThrow(), otroBarbero.getId(), reserva.getId());
                        case "correo" -> """
                                INSERT INTO usuario (nombre, correo, telefono, password_hash, rol, privacidad_aceptada_en, creado_en, actualizado_en)
                                SELECT nombre, correo, telefono, password_hash, rol, privacidad_aceptada_en, creado_en, actualizado_en
                                FROM usuario WHERE id=%d
                                """.formatted(cliente.getId());
                        case "nombre" -> """
                                INSERT INTO servicio (nombre, descripcion, duracion_min, precio, creado_en, actualizado_en)
                                SELECT nombre, descripcion, duracion_min, precio, creado_en, actualizado_en FROM servicio WHERE id=%d
                                """.formatted(servicio.getId());
                        default -> throw new IllegalArgumentException("Caso de prueba desconocido.");
                    };
                        return catchThrowableOfType(SQLException.class, () -> sentencia.execute(sql));
                }
            });
        });
        assertThat((Throwable) error).isNotNull();
        assertThat(error.getSQLState()).isEqualTo(estado);
        assertThat(((org.postgresql.util.PSQLException) error).getServerErrorMessage().getConstraint()).isEqualTo(restriccion);
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
