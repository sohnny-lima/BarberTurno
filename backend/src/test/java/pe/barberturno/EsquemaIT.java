package pe.barberturno;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@Import(EsquemaIT.RelojFijoConfig.class)
class EsquemaIT {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager gestorTransacciones;

    @Autowired
    private Clock clock;

    @Test
    void migrar_aplicaVersionUnoYCreaLasOchoTablasConBtreeGist() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                ORDER BY tablename
                """, String.class)).containsExactly(
                "auditoria_reserva", "barbero", "bloqueo", "jornada",
                "notificacion", "reserva", "servicio", "usuario");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'reserva'::regclass AND contype = 'x'
                ORDER BY conname
                """, String.class)).containsExactly(
                "reserva_sin_solape_barbero", "reserva_sin_solape_cliente");
    }

    @Test
    void reservar_conSolapeDelMismoBarbero_rechaza() {
        enTransaccion(datos -> {
            datos.reserva(datos.cliente, datos.barbero, "10:00", "10:30", 30, "CONFIRMADA");
            comprobarRestriccion(() -> datos.reserva(
                    datos.otroCliente, datos.barbero, "10:10", "10:40", 30, "CONFIRMADA"),
                    "23P01", "reserva_sin_solape_barbero");
        });
    }

    @Test
    void reservar_conFranjasContiguas_permite() {
        enTransaccion(datos -> {
            long primera = datos.reserva(
                    datos.cliente, datos.barbero, "10:00", "10:30", 30, "CONFIRMADA");
            long segunda = datos.reserva(
                    datos.cliente, datos.barbero, "10:30", "11:00", 30, "CONFIRMADA");
            assertThat(segunda).isGreaterThan(primera);
            assertThat(datos.totalReservas()).isEqualTo(2);
        });
    }

    @Test
    void reservar_conSolapeDeUnaCancelada_permite() {
        enTransaccion(datos -> {
            datos.reserva(datos.cliente, datos.barbero, "10:00", "10:30", 30, "CANCELADA");
            datos.reserva(datos.cliente, datos.barbero, "10:10", "10:40", 30, "CONFIRMADA");
            assertThat(datos.totalReservas()).isEqualTo(2);
        });
    }

    @Test
    void reservar_conSolapeDelMismoClienteYDistintoBarbero_rechaza() {
        enTransaccion(datos -> {
            datos.reserva(datos.cliente, datos.barbero, "10:00", "10:30", 30, "CONFIRMADA");
            comprobarRestriccion(() -> datos.reserva(
                    datos.cliente, datos.otroBarbero, "10:10", "10:40", 30, "CONFIRMADA"),
                    "23P01", "reserva_sin_solape_cliente");
        });
    }

    @Test
    void reservar_conFinIncoherente_rechaza() {
        enTransaccion(datos -> comprobarRestriccion(
                () -> datos.reserva(datos.cliente, datos.barbero, "10:00", "10:40", 30, "CONFIRMADA"),
                "23514", "reserva_fin_coherente"));
    }

    @Test
    void crearUsuario_conCorreoEnMayusculas_rechaza() {
        enTransaccion(datos -> comprobarRestriccion(
                () -> datos.usuario("Cliente@ejemplo.test", "CLIENTE", "999000001", datos.ahora),
                "23514", "usuario_correo_minusculas"));
    }

    @Test
    void crearServicio_conDuracionVeinticinco_rechaza() {
        enTransaccion(datos -> comprobarRestriccion(
                () -> datos.servicio("Duración inválida", 25),
                "23514", "servicio_duracion_min_check"));
    }

    @ParameterizedTest
    @CsvSource({"10:00, 10:00", "11:00, 10:00"})
    void crearJornada_conInicioIgualOPosteriorAlFin_rechaza(String inicio, String fin) {
        enTransaccion(datos -> comprobarRestriccion(() -> datos.jdbc.update("""
                INSERT INTO jornada (barbero_id, dia_semana, hora_inicio, hora_fin)
                VALUES (?, 1, CAST(? AS time), CAST(? AS time))
                """, datos.barbero, inicio, fin),
                "23514", "jornada_intervalo_valido"));
    }

    @Test
    void reservar_alTerminarUnaCancelada_permite() {
        enTransaccion(datos -> {
            datos.reserva(datos.cliente, datos.barbero, "10:00", "10:30", 30, "CANCELADA");
            datos.reserva(datos.cliente, datos.barbero, "10:30", "11:00", 30, "CONFIRMADA");
            assertThat(datos.totalReservas()).isEqualTo(2);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reservar_conSolapeDeNoAsistio_rechazaEnAmbosOrdenes(boolean noAsistioPrimero) {
        enTransaccion(datos -> {
            datos.reserva(datos.cliente, datos.barbero, "10:00", "10:30", 30,
                    noAsistioPrimero ? "NO_ASISTIO" : "CONFIRMADA");
            comprobarRestriccion(() -> datos.reserva(
                    datos.otroCliente, datos.barbero, "10:10", "10:40", 30,
                    noAsistioPrimero ? "CONFIRMADA" : "NO_ASISTIO"),
                    "23P01", "reserva_sin_solape_barbero");
        });
    }

    @Test
    void crearUsuario_conRolFueraDeLaLista_rechaza() {
        enTransaccion(datos -> comprobarRestriccion(
                () -> datos.usuario("rol-invalido@ejemplo.test", "OTRO", null, null),
                "23514", "usuario_rol_check"));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "false, false"})
    void crearCliente_sinTelefonoOConsentimiento_rechaza(boolean conTelefono, boolean conConsentimiento) {
        enTransaccion(datos -> comprobarRestriccion(() -> datos.usuario(
                "incompleto@ejemplo.test", "CLIENTE",
                conTelefono ? "999000003" : null, conConsentimiento ? datos.ahora : null),
                "23514", "usuario_cliente_completo"));
    }

    @Test
    void crearServicio_conNombreDuplicadoEnOtroCaso_rechaza() {
        enTransaccion(datos -> comprobarRestriccion(
                () -> datos.servicio("CORTE DE PRUEBA", 30),
                "23505", "servicio_nombre_uk"));
    }

    @Test
    void reservar_asignaIdentidadDesdeCien() {
        enTransaccion(datos -> {
            assertThat(datos.jdbc.queryForObject("""
                    SELECT seqstart FROM pg_sequence
                    WHERE seqrelid = pg_get_serial_sequence('reserva', 'id')::regclass
                    """, Long.class)).isEqualTo(100L);
            assertThat(datos.reserva(datos.cliente, datos.barbero,
                    "10:00", "10:30", 30, "CONFIRMADA")).isGreaterThanOrEqualTo(100L);
        });
    }

    private void enTransaccion(Consumer<Datos> caso) {
        new TransactionTemplate(gestorTransacciones).executeWithoutResult(estado -> {
            // Las secuencias avanzan aunque la transacción se revierta; no se reinician.
            estado.setRollbackOnly();
            caso.accept(new Datos(new JdbcTemplate(dataSource), Timestamp.from(clock.instant())));
        });
    }

    private void comprobarRestriccion(Runnable escritura, String sqlState, String restriccion) {
        DataAccessException error = catchThrowableOfType(DataAccessException.class, escritura::run);
        assertThat(error).isNotNull();
        assertThat(error.getMostSpecificCause()).isInstanceOf(PSQLException.class);
        PSQLException postgres = (PSQLException) error.getMostSpecificCause();
        assertThat(postgres.getSQLState()).isEqualTo(sqlState);
        assertThat(postgres.getServerErrorMessage()).isNotNull();
        assertThat(postgres.getServerErrorMessage().getConstraint()).isEqualTo(restriccion);
    }

    private static final class Datos {

        private final JdbcTemplate jdbc;
        private final Timestamp ahora;
        private final long cliente;
        private final long otroCliente;
        private final long barbero;
        private final long otroBarbero;
        private final long servicio;

        private Datos(JdbcTemplate jdbc, Timestamp ahora) {
            this.jdbc = jdbc;
            this.ahora = ahora;
            cliente = usuario("cliente-uno@ejemplo.test", "CLIENTE", "999000001", ahora);
            otroCliente = usuario("cliente-dos@ejemplo.test", "CLIENTE", "999000002", ahora);
            barbero = barbero(usuario("barbero-uno@ejemplo.test", "BARBERO", null, null));
            otroBarbero = barbero(usuario("barbero-dos@ejemplo.test", "BARBERO", null, null));
            servicio = servicio("Corte de prueba", 30);
        }

        private long usuario(String correo, String rol, String telefono, Timestamp consentimiento) {
            return jdbc.queryForObject("""
                    INSERT INTO usuario (nombre, correo, telefono, password_hash, rol,
                        privacidad_aceptada_en, creado_en, actualizado_en)
                    VALUES ('Persona ficticia', ?, ?, 'hash-ficticio-no-utilizable', ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, correo, telefono, rol, consentimiento, ahora, ahora);
        }

        private long barbero(long usuario) {
            return jdbc.queryForObject("""
                    INSERT INTO barbero (usuario_id, creado_en, actualizado_en)
                    VALUES (?, ?, ?) RETURNING id
                    """, Long.class, usuario, ahora, ahora);
        }

        private long servicio(String nombre, int duracion) {
            return jdbc.queryForObject("""
                    INSERT INTO servicio (nombre, duracion_min, precio, creado_en, actualizado_en)
                    VALUES (?, ?, 20.00, ?, ?) RETURNING id
                    """, Long.class, nombre, duracion, ahora, ahora);
        }

        private long reserva(long clienteId, long barberoId,
                String inicio, String fin, int duracion, String estado) {
            return jdbc.queryForObject("""
                    INSERT INTO reserva (cliente_id, barbero_id, servicio_id, inicio, fin,
                        duracion_ref_min, precio_ref, estado, creada_por, creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, ?, 20.00, ?, ?, ?, ?) RETURNING id
                    """, Long.class, clienteId, barberoId, servicio,
                    instante(inicio), instante(fin), duracion, estado, clienteId, ahora, ahora);
        }

        private Timestamp instante(String hora) {
            return Timestamp.from(OffsetDateTime.parse("2026-09-28T" + hora + ":00-05:00").toInstant());
        }

        private int totalReservas() {
            return jdbc.queryForObject("SELECT count(*) FROM reserva", Integer.class);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojFijoConfig {

        @Bean
        @Primary
        Clock relojDePruebas() {
            return Clock.fixed(Instant.parse("2026-09-28T14:00:00Z"), ZoneId.of("America/Lima"));
        }
    }
}
