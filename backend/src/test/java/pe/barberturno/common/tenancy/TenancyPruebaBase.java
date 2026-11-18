package pe.barberturno.common.tenancy;

import java.sql.DriverManager;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.support.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1", "barberturno.admin.correo=",
        "barberturno.admin.password=", "barberturno.admin.nombre=", "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc
@Import({TenancyPruebaBase.Configuracion.class, RelojAjustable.Configuracion.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class TenancyPruebaBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcNegocioPruebas sql;
    @Autowired Environment entorno;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ProveedorNegocioProgramable proveedor;
    @Autowired RelojAjustable reloj;

    @BeforeEach
    void prepararTenancy() {
        proveedor.reiniciar();
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
    }

    @AfterEach
    void limpiarTenancy() {
        proveedor.reiniciar();
        LimpiezaBaseDatos.limpiar(sql);
    }

    long negocioB() throws Exception {
        // Única escritura de plataforma autorizada al propietario, V2-DA-20.
        try (var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"));
                var sentencia = conexion.prepareStatement("""
                        INSERT INTO negocio (slug, nombre, creado_en, actualizado_en)
                        VALUES ('negocio-b', 'Barbería B ficticia', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id
                        """)) {
            if (!"barberturno_v2_test".equals(conexion.getCatalog())) {
                throw new IllegalStateException("La preparación requiere la base v2 de pruebas.");
            }
            try (var resultado = sentencia.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    <T> T enNegocio(Long id, Supplier<T> accion) {
        proveedor.fijar(id);
        try {
            return new TransactionTemplate(gestor).execute(tx -> accion.get());
        } finally {
            proveedor.reiniciar();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuracion {
        @Bean
        ProveedorNegocioProgramable proveedorProgramable() {
            return new ProveedorNegocioProgramable();
        }
    }
}
