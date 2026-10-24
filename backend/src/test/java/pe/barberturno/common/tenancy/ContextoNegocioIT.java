package pe.barberturno.common.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.scheduling.BarberoRepository;
import static org.assertj.core.api.Assertions.*;

class ContextoNegocioIT extends TenancyPruebaBase {
    static final String INSERTAR = """
            INSERT INTO servicio (nombre, duracion_min, precio, creado_en, actualizado_en)
            VALUES ('Corte de contexto', 30, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id
            """;
    @Autowired ServicioRepository servicios;
    @Autowired BarberoRepository barberos;
    @PersistenceContext EntityManager em;

    @Test
    void escrituraHeredada_sinTransaccionEnvolventePermiteInsertYDeleteConContexto() {
        proveedor.fijar(1L);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var servicio = servicios.saveAndFlush(new Servicio("Corte persistido", "Prueba", (short) 30,
                java.math.BigDecimal.TEN, reloj.instant()));
        assertThat(servicios.findById(servicio.getId())).isPresent();
        servicios.deleteAllByIdInBatch(java.util.List.of(servicio.getId()));
        assertThat(servicios.findById(servicio.getId())).isEmpty();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void consultaDeclarada_sinTransaccionEnvolventeLeeSoloElNegocioActual() throws Exception {
        long b = negocioB();
        enNegocio(b, () -> DatosNegocioPrueba.crear(jdbc, "b"));
        enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "a"));
        proveedor.fijar(1L);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(barberos.countByActivoTrue(1L)).isEqualTo(1);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        proveedor.fijar(null);
        assertThat(barberos.countByActivoTrue(1L)).isZero();
    }

    @Test
    void entityManagerCompartido_sinTransaccionFallaAunqueExistanFilas() {
        enNegocio(1L, () -> jdbc.queryForObject(INSERTAR, Long.class));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThatThrownBy(() -> em.createQuery("select count(s) from Servicio s", Long.class).getSingleResult())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hibernate requiere una transacción de Spring activa para acceder a datos.");
    }

    @Test
    void entityManagerCompartido_conTransaccionLeeLasFilasDelNegocio() {
        enNegocio(1L, () -> jdbc.queryForObject(INSERTAR, Long.class));
        assertThat(enNegocio(1L, () -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return em.createQuery("select count(s) from Servicio s", Long.class).getSingleResult();
        })).isEqualTo(1L);
    }

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
