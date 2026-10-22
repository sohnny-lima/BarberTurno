package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.catalog.ServicioRepository;
import static org.assertj.core.api.Assertions.*;

class ContextoNegocioIT extends TenancyPruebaBase {
    static final String INSERTAR = """
            INSERT INTO servicio (nombre, duracion_min, precio, creado_en, actualizado_en)
            VALUES ('Corte de contexto', 30, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id
            """;
    @Autowired ServicioRepository servicios;

    @Test
    void mismaConexion_dosNegociosNoCompartenFilasNiContexto() throws Exception {
        long b = negocioB();
        int pid = enNegocio(b, () -> {
            jdbc.queryForObject(INSERTAR, Long.class);
            return jdbc.queryForObject("select pg_backend_pid()", Integer.class);
        });
        assertThat(enNegocio(1L, () -> jdbc.queryForObject("select pg_backend_pid()", Integer.class))).isEqualTo(pid);
        assertThat(enNegocio(1L, () -> jdbc.queryForObject("select count(*) from servicio", Integer.class))).isZero();
        assertThat(enNegocio(b, () -> servicios.findAll())).hasSize(1);
        assertThat(jdbc.queryForObject("select nullif(current_setting('app.negocio_id', true), '')", String.class)).isNull();
    }

    @Test
    void rollback_descartaElContextoLocalEnLaMismaConexion() {
        proveedor.fijar(1L);
        int pid = new TransactionTemplate(gestor).execute(tx -> {
            tx.setRollbackOnly();
            assertThat(jdbc.queryForObject("select app_negocio_actual()", Long.class)).isEqualTo(1L);
            return jdbc.queryForObject("select pg_backend_pid()", Integer.class);
        });
        assertThat(jdbc.queryForObject("select pg_backend_pid()", Integer.class)).isEqualTo(pid);
        assertThat(jdbc.queryForObject("select nullif(current_setting('app.negocio_id', true), '')", String.class)).isNull();
    }

    @Test
    void sinContexto_noLeeLasSieteTablasYRechazaInsert() {
        enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "a"));
        for (String tabla : EsquemaV2IT.TABLAS) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabla, Integer.class)).as(tabla).isZero();
            assertThat(enNegocio(null, () -> jdbc.queryForObject("select count(*) from " + tabla, Integer.class)))
                    .as("transacción sin ámbito: " + tabla).isZero();
        }
        assertThatThrownBy(() -> jdbc.queryForObject(INSERTAR, Long.class)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> enNegocio(null, () -> jdbc.queryForObject(INSERTAR, Long.class)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void withCheck_rechazaInsertYUpdateDeOtroNegocio() throws Exception {
        long b = negocioB();
        long ajeno = enNegocio(b, () -> jdbc.queryForObject(INSERTAR, Long.class));
        assertThat(enNegocio(1L, () -> jdbc.update("update servicio set nombre='Cambio ajeno' where id=?", ajeno))).isZero();
        assertThat(enNegocio(1L, () -> jdbc.update("delete from servicio where id=?", ajeno))).isZero();
        long id = enNegocio(1L, () -> jdbc.queryForObject(INSERTAR, Long.class));
        assertThatThrownBy(() -> enNegocio(1L, () -> jdbc.update("update servicio set negocio_id=? where id=?", b, id)))
                .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThatThrownBy(() -> enNegocio(1L, () -> jdbc.update("""
                INSERT INTO servicio (negocio_id, nombre, duracion_min, precio, creado_en, actualizado_en)
                VALUES (?, 'Intruso', 30, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, b))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void repositorioHeredado_soloLecturaAbreSuPropioContexto() {
        enNegocio(1L, () -> jdbc.queryForObject(INSERTAR, Long.class));
        proveedor.fijar(1L);
        assertThat(servicios.findAll()).hasSize(1);
        proveedor.fijar(null);
        assertThat(servicios.findAll()).isEmpty();
    }
}
