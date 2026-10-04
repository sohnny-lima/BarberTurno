package pe.barberturno.demo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.barberturno.BarberTurnoApplication;
import pe.barberturno.common.config.ConfiguracionProduccion;
import pe.barberturno.common.time.ClockConfig;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;

/** Arranques reales de Spring con demo, pools cerrados por contexto y protección del reloj en prod. */
class DatosDemoRunnerIT {
    private static final String PASSWORD = "ClaveDemoPrueba32";
    private static final Instant FIJO = Instant.parse("2026-09-28T14:00:00Z");

    private ConfigurableApplicationContext arrancar(String password) {
        return new SpringApplicationBuilder(BarberTurnoApplication.class).run(
                "--spring.profiles.active=test,demo", "--server.port=0",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--barberturno.admin.correo=", "--barberturno.admin.password=", "--barberturno.admin.nombre=",
                "--barberturno.reloj-fijo=2026-09-28T09:00:00-05:00", "--BT_DEMO_PASSWORD=" + password);
    }

    private Map<String, List<Map<String, Object>>> instantanea(JdbcTemplate jdbc) {
        var resultado = new java.util.LinkedHashMap<String, List<Map<String, Object>>>();
        for (String tabla : List.of("usuario", "barbero", "servicio", "jornada", "bloqueo",
                "reserva", "auditoria_reserva", "notificacion")) {
            resultado.put(tabla, jdbc.queryForList("select * from " + tabla + " order by id"));
        }
        return resultado;
    }

    @Test void arranquesDemo_sinClaveSinCarga_yDosReiniciosSinDuplicadosNiCambios() {
        Map<String, List<Map<String, Object>>> antes;
        // Contexto de preparación: también comprueba la ausencia de contraseña y los rollbacks.
        try (var contexto = arrancar("")) {
            var jdbc = contexto.getBean(JdbcTemplate.class);
            LimpiezaBaseDatos.limpiar(jdbc);
            try {
                var servicio = contexto.getBean(DatosDemoService.class);
                var runner = contexto.getBean(DatosDemoRunner.class);
                runner.run(new DefaultApplicationArguments());
                assertThat(instantanea(jdbc).values()).allSatisfy(filas -> assertThat(filas).isEmpty());
                assertThatThrownBy(() -> servicio.cargar("invalida"))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("RN-25");
                assertThat(instantanea(jdbc).values()).allSatisfy(filas -> assertThat(filas).isEmpty());

                jdbc.update("insert into servicio (nombre, descripcion, duracion_min, precio, activo, creado_en, actualizado_en)"
                        + " values ('Corte clásico', 'Distinto', 30, 99, true, ?, ?)",
                        java.sql.Timestamp.from(FIJO), java.sql.Timestamp.from(FIJO));
                assertThatThrownBy(() -> servicio.cargar(PASSWORD)).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("catálogo");
                assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("select precio from servicio", java.math.BigDecimal.class))
                        .isEqualByComparingTo("99");
                LimpiezaBaseDatos.limpiar(jdbc);

                servicio.cargar(PASSWORD);
                comprobarEscenario(contexto);
                assertThat(contexto.getBean(UsuarioRepository.class).findByCorreo("admin-demo@ejemplo.test")).isPresent();
                var sinCambios = instantanea(jdbc);
                servicio.cargar("OtraClavePrueba32");
                assertThat(instantanea(jdbc)).isEqualTo(sinCambios);
                assertThat(contexto.getBean(PasswordEncoder.class).matches(PASSWORD,
                        contexto.getBean(UsuarioRepository.class).findByCorreo("cliente@ejemplo.test")
                                .orElseThrow().getPasswordHash())).isTrue();
                LimpiezaBaseDatos.limpiar(jdbc);

                // Un administrador inicial ya configurado prevalece; no se crea admin-demo.
                var usuarios = contexto.getBean(UsuarioRepository.class);
                usuarios.saveAndFlush(new Usuario("Admin inicial ficticio", "inicial@ejemplo.test", null,
                        contexto.getBean(PasswordEncoder.class).encode(PASSWORD), Rol.ADMIN, null, FIJO));
                servicio.cargar(PASSWORD);
                assertThat(usuarios.findByCorreo("admin-demo@ejemplo.test")).isEmpty();
                assertThat(jdbc.queryForObject("select count(*) from usuario where rol='ADMIN'", Integer.class)).isOne();
                antes = instantanea(jdbc);
            } catch (RuntimeException | Error e) {
                LimpiezaBaseDatos.limpiar(jdbc);
                throw e;
            }
        }
        // Dos arranques completos: el runner automático encuentra el marcador persistido.
        for (int i = 0; i < 2; i++) {
            try (var contexto = arrancar(PASSWORD)) {
                var jdbc = contexto.getBean(JdbcTemplate.class);
                try {
                    comprobarEscenario(contexto);
                    assertThat(instantanea(jdbc)).isEqualTo(antes);
                } finally {
                    if (i == 1) LimpiezaBaseDatos.limpiar(jdbc);
                }
            }
        }
    }

    private void comprobarEscenario(ConfigurableApplicationContext contexto) {
        JdbcTemplate jdbc = contexto.getBean(JdbcTemplate.class);
        assertThat(contexto.getBean(Clock.class).instant()).isEqualTo(FIJO);
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from barbero", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from jornada", Integer.class)).isEqualTo(24);
        assertThat(jdbc.queryForObject("select count(*) from bloqueo", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from reserva", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from auditoria_reserva", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from notificacion", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForList("select nombre from usuario where rol='CLIENTE' order by id", String.class))
                .containsExactly("Cliente de demostración", "Ana", "Luis");
        assertThat(jdbc.queryForList("select especialidad from barbero", String.class)).containsOnly("Corte y barba");
        assertThat(jdbc.queryForObject("select count(*) from usuario where correo not like '%@ejemplo.test'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from jornada where dia_semana=7", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select estado from reserva order by id", String.class))
                .containsExactly("CONFIRMADA", "CONFIRMADA", "COMPLETADA", "CONFIRMADA", "PENDIENTE");
        assertThat(jdbc.queryForList("select datos_nuevos->>'referenciaDemo' from auditoria_reserva order by id",
                String.class)).containsExactly("BT-101", "BT-102", "BT-103", "BT-104", "BT-100");
        assertThat(jdbc.queryForList("select inicio from reserva order by id", java.sql.Timestamp.class))
                .extracting(java.sql.Timestamp::toInstant).containsExactly(
                        Instant.parse("2026-10-01T15:00:00Z"), Instant.parse("2026-09-28T14:30:00Z"),
                        Instant.parse("2026-09-27T15:00:00Z"), Instant.parse("2026-09-28T15:00:00Z"),
                        Instant.parse("2026-09-29T16:00:00Z"));
        assertThat(jdbc.queryForList("select precio_ref from reserva order by id", java.math.BigDecimal.class))
                .containsExactly(new java.math.BigDecimal("25.00"), new java.math.BigDecimal("20.00"),
                        new java.math.BigDecimal("25.00"), new java.math.BigDecimal("20.00"),
                        new java.math.BigDecimal("25.00"));
        assertThat(jdbc.queryForList("select duracion_ref_min from reserva order by id", Integer.class))
                .containsExactly(30, 20, 30, 20, 30);
        assertThat(jdbc.queryForList("select fin from reserva order by id", java.sql.Timestamp.class))
                .extracting(java.sql.Timestamp::toInstant).containsExactly(
                        Instant.parse("2026-10-01T15:30:00Z"), Instant.parse("2026-09-28T14:50:00Z"),
                        Instant.parse("2026-09-27T15:30:00Z"), Instant.parse("2026-09-28T15:20:00Z"),
                        Instant.parse("2026-09-29T16:30:00Z"));
        long carlos = jdbc.queryForObject("select b.id from barbero b join usuario u on u.id=b.usuario_id"
                + " where u.correo='carlos@ejemplo.test'", Long.class);
        long corte = jdbc.queryForObject("select id from servicio where nombre='Corte clásico'", Long.class);
        String respuesta = org.springframework.web.client.RestClient.create("http://localhost:"
                + contexto.getEnvironment().getProperty("local.server.port")).get()
                .uri("/api/disponibilidad?fecha=2026-10-01&servicioId=" + corte + "&barberoId=" + carlos)
                .retrieve().body(String.class);
        var horas = new java.util.ArrayList<String>();
        contexto.getBean(tools.jackson.databind.json.JsonMapper.class).readTree(respuesta).get("franjas")
                .forEach(n -> horas.add(java.time.OffsetDateTime.parse(n.get("inicio").asString())
                        .toLocalTime().toString()));
        assertThat(horas).contains("09:00", "10:30", "17:00").doesNotContain("10:00",
                "15:40", "15:50", "16:00", "16:10", "16:20", "16:30", "16:40", "16:50");
    }

    @ParameterizedTest @ValueSource(strings = {"demo", "test"})
    void relojFijo_funcionaSoloEnLosPerfilesPermitidos(String perfil) {
        new ApplicationContextRunner().withUserConfiguration(ClockConfig.class)
                .withInitializer(c -> c.getEnvironment().setActiveProfiles(perfil))
                .withPropertyValues("barberturno.reloj-fijo=2026-09-28T09:00:00-05:00")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    assertThat(c.getBean(Clock.class).instant()).isEqualTo(FIJO);
                    assertThat(c.getBean(Clock.class).getZone()).isEqualTo(TiempoNegocio.ZONA);
                });
    }

    @Test void relojFijo_enDevSeIgnora() {
        new ApplicationContextRunner().withUserConfiguration(ClockConfig.class)
                .withInitializer(c -> c.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("barberturno.reloj-fijo=2026-09-28T09:00:00-05:00")
                .run(c -> assertThat(c.getBean(Clock.class)).isEqualTo(Clock.system(TiempoNegocio.ZONA)));
    }

    @Test void relojFijo_invalidoEnDemoImpideArrancar() {
        new ApplicationContextRunner().withUserConfiguration(ClockConfig.class)
                .withInitializer(c -> c.getEnvironment().setActiveProfiles("demo"))
                .withPropertyValues("barberturno.reloj-fijo=sin-desfase")
                .run(c -> assertThat(c).hasFailed());
    }

    @ParameterizedTest @ValueSource(strings = {"2026-09-28T09:00:00-05:00", ""})
    void relojFijo_enProdFallaAntesDeCrearBeansOConexiones(String valor) {
        new ApplicationContextRunner().withUserConfiguration(ConfiguracionProduccion.class, ClockConfig.class)
                .withInitializer(c -> c.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("barberturno.reloj-fijo=" + valor)
                .run(c -> {
                    assertThat(c).hasFailed();
                    assertThat(c.getStartupFailure()).hasMessageContaining("barberturno.reloj-fijo");
                });
    }
}
