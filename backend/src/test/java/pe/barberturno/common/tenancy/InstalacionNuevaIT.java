package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InstalacionNuevaIT extends MigracionPruebaBase {
    @Test
    void esquemaVacio_instalaLasNueveTablasYPermiteReservaCompletaComoAplicacion() throws Exception {
        var flyway = flyway(null);
        flyway.clean();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        try (var conexion = conexion(false)) {
            var jdbc = jdbc(conexion);
            assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
            assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname='public' and tablename<>'flyway_schema_history'", Integer.class)).isEqualTo(9);
            assertThat(jdbc.queryForObject("select count(*) from negocio where slug='barberturno'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from pg_class where relnamespace='public'::regnamespace and relrowsecurity and relforcerowsecurity", Integer.class)).isEqualTo(7);
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where contype='f' and array_length(conkey,1)=2", Integer.class)).isEqualTo(6);
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user, 'negocio', 'INSERT')", Boolean.class)).isFalse();
            contexto(conexion, 1L);
            var datos = DatosNegocioPrueba.crear(jdbc, "instalacion");
            assertThat(datos.reserva()).isEqualTo(100);
            for (String tabla : EsquemaV2IT.TABLAS) {
                assertThat(jdbc.queryForObject("select count(*) from " + tabla + " where negocio_id=1", Integer.class)).as(tabla).isEqualTo(1);
            }
            conexion.commit();
            assertThat(jdbc.queryForObject("select count(*) from reserva", Integer.class)).isZero();
        }
    }
}
