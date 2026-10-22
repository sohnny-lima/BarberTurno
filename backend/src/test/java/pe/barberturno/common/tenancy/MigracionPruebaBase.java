package pe.barberturno.common.tenancy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1", "barberturno.admin.correo=",
        "barberturno.admin.password=", "barberturno.admin.nombre="})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class MigracionPruebaBase {
    @Autowired Environment entorno;

    String url() {
        String test = entorno.getRequiredProperty("spring.datasource.url");
        if (!test.endsWith("/barberturno_v2_test")) {
            throw new IllegalStateException("La suite requiere la URL aislada de v2_test.");
        }
        return test.substring(0, test.length() - "barberturno_v2_test".length()) + "barberturno_v2_migracion";
    }

    Connection conexion(boolean propietario) throws Exception {
        var conexion = DriverManager.getConnection(url(), entorno.getRequiredProperty(propietario ? "spring.flyway.user" : "spring.datasource.username"),
                entorno.getRequiredProperty(propietario ? "spring.flyway.password" : "spring.datasource.password"));
        if (!"barberturno_v2_migracion".equals(conexion.getCatalog())) {
            conexion.close();
            throw new IllegalStateException("Destino de migraciones no autorizado.");
        }
        return conexion;
    }

    Flyway flyway(String version) throws Exception {
        // Abrir y comprobar realmente la base ANTES de permitir clean().
        try (var verificacion = conexion(true)) {
            if (!"barberturno_v2_migracion".equals(verificacion.getCatalog())) throw new IllegalStateException("Destino inválido.");
        }
        var config = Flyway.configure().dataSource(url(), entorno.getRequiredProperty("spring.flyway.user"),
                entorno.getRequiredProperty("spring.flyway.password"))
                .cleanDisabled(false).locations("classpath:db/migration")
                .placeholders(Map.of("rolAplicacion", entorno.getRequiredProperty("spring.datasource.username"), "negocioUnicoSlug", "barberturno"));
        if (version != null) config.target(version);
        return config.load();
    }

    @AfterEach
    void limpiarMigracion() throws Exception {
        flyway(null).clean();
    }

    static JdbcTemplate jdbc(Connection conexion) {
        return new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
    }

    static void contexto(Connection conexion, Long negocio) throws Exception {
        conexion.setAutoCommit(false);
        try (var consulta = conexion.prepareStatement("select set_config('app.negocio_id', ?, true)")) {
            consulta.setString(1, negocio == null ? "" : negocio.toString());
            consulta.execute();
        }
    }
}
