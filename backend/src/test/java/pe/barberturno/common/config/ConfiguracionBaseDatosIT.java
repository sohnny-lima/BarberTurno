package pe.barberturno.common.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class ConfiguracionBaseDatosIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void conectar_utilizaPostgresql18YLaBaseDePruebas() throws Exception {
        try (Connection conexion = dataSource.getConnection();
                Statement consulta = conexion.createStatement();
                ResultSet resultado = consulta.executeQuery("SELECT current_database()")) {
            assertThat(conexion.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(conexion.getMetaData().getDatabaseMajorVersion()).isEqualTo(18);
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString(1)).isEqualTo("barberturno_test");
        }
    }

    @Test
    void conexionesDelPool_configuranElTiempoMaximoDeBloqueo() throws Exception {
        try (Connection primera = dataSource.getConnection();
                Connection segunda = dataSource.getConnection()) {
            comprobarTiempoDeBloqueo(primera);
            comprobarTiempoDeBloqueo(segunda);
        }
    }

    private void comprobarTiempoDeBloqueo(Connection conexion) throws Exception {
        try (Statement consulta = conexion.createStatement();
                ResultSet resultado = consulta.executeQuery("SHOW lock_timeout")) {
            assertThat(resultado.next()).isTrue();
            assertThat(resultado.getString(1)).isEqualTo("5s");
        }
    }
}
