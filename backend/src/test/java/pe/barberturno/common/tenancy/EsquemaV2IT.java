package pe.barberturno.common.tenancy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

class EsquemaV2IT extends TenancyPruebaBase {
    @Test void mismaCuenta_perfilesEnDosNegociosYDuplicadoLocalRechazado() throws Exception {
        long b = negocioB();
        var a = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "perfil-a"));
        enNegocio(b, () -> {
            jdbc.update("insert into barbero (usuario_id, nombre_visible, creado_en, actualizado_en) values (?, 'Perfil B', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", a.admin());
            assertThat(jdbc.queryForList("select nombre_visible from barbero where usuario_id=?", String.class, a.admin())).containsExactly("Perfil B");
            return null;
        });
        enNegocio(1L, () -> {
            assertThat(jdbc.queryForList("select nombre_visible from barbero where usuario_id=?", String.class, a.admin())).containsExactly("Profesional ficticio");
            return null;
        });
        assertThatThrownBy(() -> enNegocio(b, () -> jdbc.update("insert into barbero (usuario_id, nombre_visible, creado_en, actualizado_en) values (?, 'Duplicado', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", a.admin())))
                .isInstanceOf(DataAccessException.class).satisfies(fallo -> {
                    var causa = (org.postgresql.util.PSQLException) ((DataAccessException) fallo).getMostSpecificCause();
                    assertThat(causa.getSQLState()).isEqualTo("23505");
                    assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo("barbero_usuario_uk");
                });
    }

    static final List<String> TABLAS = List.of("servicio", "barbero", "jornada", "bloqueo", "reserva", "auditoria_reserva", "notificacion", "cliente");

    @Test void reglasLocales_columnasYValoresInicialesDeV104() {
        assertThat(jdbc.queryForMap("select data_type, is_nullable, column_default from information_schema.columns where table_name='negocio' and column_name='tolerancia_inicio_min'"))
                .containsEntry("data_type", "smallint").containsEntry("is_nullable", "NO").containsEntry("column_default", "15");
        assertThat(jdbc.queryForMap("select is_nullable, character_maximum_length from information_schema.columns where table_name='barbero' and column_name='nombre_visible'"))
                .containsEntry("is_nullable", "NO").containsEntry("character_maximum_length", 100);
        assertThat(jdbc.queryForMap("select is_nullable, character_maximum_length from information_schema.columns where table_name='barbero' and column_name='telefono_contacto'"))
                .containsEntry("is_nullable", "YES").containsEntry("character_maximum_length", 16);
    }

    @Test
    void rolReal_noEsPropietarioNiTienePrivilegiosEspeciales() {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(jdbc.queryForObject("""
                select rolsuper or rolbypassrls or rolcreatedb or rolcreaterole or rolreplication
                from pg_roles where rolname=current_user
                """, Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from pg_class where relowner=(select oid from pg_roles where rolname=current_user)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_database where datdba=(select oid from pg_roles where rolname=current_user)", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_auth_members where member=(select oid from pg_roles where rolname=current_user)", Integer.class)).isZero();
    }

    @Test
    void ochoTablas_tienenForcePoliticaCompletaYDefaultCerrado() {
        for (String tabla : TABLAS) {
            assertThat(jdbc.queryForObject("select relrowsecurity and relforcerowsecurity from pg_class where oid=?::regclass", Boolean.class, tabla)).as(tabla).isTrue();
            var politicas = jdbc.queryForList("select cmd, qual, with_check from pg_policies where schemaname='public' and tablename=?", tabla);
            assertThat(politicas).hasSize(1);
            assertThat(politicas.getFirst()).containsEntry("cmd", "ALL");
            assertThat(politicas.getFirst().get("qual").toString()).contains("negocio_id = app_negocio_actual()");
            assertThat(politicas.getFirst().get("with_check").toString()).contains("negocio_id = app_negocio_actual()");
            assertThat(jdbc.queryForMap("select is_nullable, column_default from information_schema.columns where table_name=? and column_name='negocio_id'", tabla))
                    .containsEntry("is_nullable", "NO").containsEntry("column_default", "app_negocio_actual()");
        }
        assertThat(jdbc.queryForObject("select relrowsecurity from pg_class where oid='usuario'::regclass", Boolean.class)).isFalse();
    }

    @Test
    void permisos_exactamenteDmlYLecturaDePlataformaSinDdl() {
        assertThat(jdbc.queryForObject("select has_schema_privilege(current_user, 'public', 'CREATE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select has_schema_privilege(current_user, 'public', 'USAGE')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select has_function_privilege(current_user, 'app_negocio_actual()', 'EXECUTE')", Boolean.class)).isTrue();
        for (String tabla : java.util.stream.Stream.concat(TABLAS.stream(), java.util.stream.Stream.of("usuario", "negocio", "membresia", "vinculo_cliente")).toList()) {
            for (String permiso : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
                boolean permitido = "SELECT".equals(permiso) || !List.of("negocio", "vinculo_cliente").contains(tabla) && List.of("INSERT", "UPDATE", "DELETE").contains(permiso);
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user, ?, ?)", Boolean.class, tabla, permiso))
                        .as("%s %s", tabla, permiso).isEqualTo(permitido);
            }
        }
        for (String secuencia : jdbc.queryForList("select sequencename from pg_sequences where schemaname='public'", String.class)) {
            assertThat(jdbc.queryForObject("select has_sequence_privilege(current_user, ?, 'USAGE, SELECT')", Boolean.class, secuencia)).isTrue();
            assertThat(jdbc.queryForObject("select has_sequence_privilege(current_user, ?, 'UPDATE')", Boolean.class, secuencia)).isFalse();
        }
        assertThat(jdbc.queryForObject("select count(*) from pg_default_acl where defaclrole=(select oid from pg_roles where rolname='barberturno_v2')", Integer.class)).isGreaterThanOrEqualTo(3);
    }

    @Test void membresia_sinRlsPermisosExactosYRestriccionesConNombre() {
        enNegocio(1L, () -> {
            var datos = DatosNegocioPrueba.crear(jdbc, "restricciones-membresia");
            IdentidadEsquemaPruebas.comprobar(jdbc, datos.cliente());
            return null;
        });
    }

    @Test
    void fkCompuestas_sonLasSieteRelacionesDelPlan() {
        assertThat(jdbc.queryForList("""
                select conname from pg_constraint where contype='f' and array_length(conkey,1)=2 order by conname
                """, String.class)).containsExactly("auditoria_reserva_reserva_fk", "bloqueo_barbero_fk",
                "jornada_barbero_fk", "notificacion_reserva_fk", "reserva_barbero_fk", "reserva_cliente_fk", "reserva_servicio_fk");
    }

    @Test void vinculo_soloDisparadorEscribeConClienteForceInclusoActualizacionYBorrado() {
        assertThat(jdbc.queryForObject("select relrowsecurity or relforcerowsecurity from pg_class where oid='vinculo_cliente'::regclass", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select prosecdef and proconfig @> ARRAY['search_path=pg_catalog, public'] from pg_proc where oid='mantener_vinculo_cliente()'::regprocedure", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select has_function_privilege(current_user, 'mantener_vinculo_cliente()', 'EXECUTE')", Boolean.class)).isFalse();
        var datos = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "vinculo"));
        assertThat(jdbc.queryForObject("select count(*) from vinculo_cliente where negocio_id=1 and usuario_id=?", Integer.class, datos.cliente())).isEqualTo(1);
        for (String sql : List.of("insert into vinculo_cliente values (1, " + datos.cliente() + ")",
                "update vinculo_cliente set negocio_id=1", "delete from vinculo_cliente")) {
            assertThatThrownBy(() -> enNegocio(1L, () -> jdbc.update(sql))).isInstanceOf(DataAccessException.class)
                    .satisfies(error -> assertThat(((org.postgresql.util.PSQLException) ((DataAccessException) error).getMostSpecificCause()).getSQLState()).isEqualTo("42501"));
        }
        enNegocio(1L, () -> {
            jdbc.update("update cliente set nombre='Solo local' where id=?", datos.ficha());
            assertThat(jdbc.queryForObject("select count(*) from vinculo_cliente", Integer.class)).isEqualTo(1);
            jdbc.update("delete from notificacion");
            jdbc.update("delete from auditoria_reserva");
            jdbc.update("delete from reserva");
            jdbc.update("update cliente set usuario_id=? where id=?", datos.admin(), datos.ficha());
            assertThat(jdbc.queryForList("select usuario_id from vinculo_cliente", Long.class)).containsExactly(datos.admin());
            jdbc.update("delete from cliente where id=?", datos.ficha());
            assertThat(jdbc.queryForObject("select count(*) from vinculo_cliente", Integer.class)).isZero();
            return null;
        });
    }

    @Test
    void servicioHomonimo_validoEntreNegociosDuplicadoDentroDelMismo() throws Exception {
        long b = negocioB();
        enNegocio(1L, () -> jdbc.queryForObject(ContextoNegocioIT.INSERTAR, Long.class));
        enNegocio(b, () -> jdbc.queryForObject(ContextoNegocioIT.INSERTAR, Long.class));
        assertThatThrownBy(() -> enNegocio(1L, () -> jdbc.queryForObject(ContextoNegocioIT.INSERTAR, Long.class)))
                .isInstanceOf(DataAccessException.class).satisfies(fallo -> {
                    var causa = (org.postgresql.util.PSQLException) ((DataAccessException) fallo).getMostSpecificCause();
                    assertThat(causa.getSQLState()).isEqualTo("23505");
                    assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo("servicio_nombre_uk");
                });
    }
}
