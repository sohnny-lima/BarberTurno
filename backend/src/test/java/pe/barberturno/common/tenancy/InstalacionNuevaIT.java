package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InstalacionNuevaIT extends MigracionPruebaBase {
    @Test
    void esquemaVacio_instalaLasDoceTablasYPermiteReservaCompletaComoAplicacion() throws Exception {
        var flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        comprobarMigracionCompleta(flyway);
        try (var conexion = conexion(false)) {
            var jdbc = jdbc(conexion);
            assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
            assertThat(jdbc.queryForObject("select count(*) from pg_tables where schemaname='public' and tablename<>'flyway_schema_history'", Integer.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("select count(*) from membresia", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from negocio where slug='barberturno'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from pg_class where relnamespace='public'::regnamespace and relrowsecurity and relforcerowsecurity", Integer.class)).isEqualTo(8);
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where contype='f' and array_length(conkey,1)=2", Integer.class)).isEqualTo(7);
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user, 'negocio', 'INSERT')", Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject("select relrowsecurity or relforcerowsecurity from pg_class where oid='vinculo_cliente'::regclass", Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user, 'vinculo_cliente', 'SELECT')", Boolean.class)).isTrue();
            for (String permiso : java.util.List.of("INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, 'vinculo_cliente', ?)", Boolean.class, permiso)).isFalse();
            }
            contexto(conexion, 1L);
            var datos = DatosNegocioPrueba.crear(jdbc, "instalacion");
            assertThat(jdbc.queryForList("select distinct rol_plataforma from usuario", String.class)).containsExactly("NINGUNO");
            IdentidadEsquemaPruebas.comprobar(jdbc, datos.cliente());
            assertThat(datos.reserva()).isEqualTo(100);
            for (String tabla : EsquemaV2IT.TABLAS) {
                assertThat(jdbc.queryForObject("select count(*) from " + tabla + " where negocio_id=1", Integer.class)).as(tabla).isEqualTo(1);
            }
            conexion.commit();
            assertThat(jdbc.queryForObject("select count(*) from reserva", Integer.class)).isZero();
        }
    }
}
