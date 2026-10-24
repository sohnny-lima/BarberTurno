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
            assertThat(jdbc.queryForObject("select tolerancia_inicio_min from negocio where id=1", Integer.class)).isEqualTo(15);
            assertThat(jdbc.queryForObject("select nombre_visible from barbero where id=?", String.class, datos.barbero())).isEqualTo("Profesional ficticio");
            assertThat(jdbc.queryForMap("select is_nullable, character_maximum_length from information_schema.columns where table_name='barbero' and column_name='nombre_visible'"))
                    .containsEntry("is_nullable", "NO").containsEntry("character_maximum_length", 100);
            assertThat(jdbc.queryForList("select distinct rol_plataforma from usuario", String.class)).containsExactly("NINGUNO");
            IdentidadEsquemaPruebas.comprobar(jdbc, datos.cliente());
            VinculoActivoEsquemaPruebas.comprobar(jdbc, datos.cliente());
            assertThat(datos.reserva()).isEqualTo(100);
            for (String tabla : EsquemaV2IT.TABLAS) {
                assertThat(jdbc.queryForObject("select count(*) from " + tabla + " where negocio_id=1", Integer.class)).as(tabla).isEqualTo(1);
            }
            conexion.commit();
            assertThat(jdbc.queryForObject("select count(*) from reserva", Integer.class)).isZero();
            long b;
            try (var propietario = conexion(true)) {
                b = jdbc(propietario).queryForObject("insert into negocio (slug, nombre, creado_en, actualizado_en) values ('perfil-b', 'Negocio B ficticio', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) returning id", Long.class);
            }
            contexto(conexion, b);
            jdbc.update("insert into barbero (usuario_id, nombre_visible, creado_en, actualizado_en) values (?, 'Perfil B', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", datos.admin());
            assertThat(jdbc.queryForObject("select count(*) from barbero where usuario_id=?", Integer.class, datos.admin())).isEqualTo(1);
            conexion.commit();
            contexto(conexion, 1L);
            assertThatThrownBy(() -> jdbc.update("insert into barbero (usuario_id, nombre_visible, creado_en, actualizado_en) values (?, 'Duplicado A', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", datos.admin()))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class).satisfies(error -> {
                        var causa = (org.postgresql.util.PSQLException) ((org.springframework.dao.DataAccessException) error).getMostSpecificCause();
                        assertThat(causa.getSQLState()).isEqualTo("23505");
                        assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo("barbero_usuario_uk");
                    });
            conexion.rollback();
        }
    }
}
