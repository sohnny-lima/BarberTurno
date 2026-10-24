package pe.barberturno.common.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Acredita V106 con la conexión de aplicación y el contexto local del llamador. */
final class VinculoActivoEsquemaPruebas {
    private VinculoActivoEsquemaPruebas() { }
    static void comprobar(JdbcTemplate jdbc, long cuenta) {
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
        assertThat(jdbc.queryForObject("select count(*) from cliente c left join vinculo_cliente v on v.negocio_id=c.negocio_id and v.usuario_id=c.usuario_id where c.usuario_id is not null and (v.usuario_id is null or v.activo is distinct from c.activo)", Integer.class)).isZero();
        boolean original = jdbc.queryForObject("select activo from cliente where usuario_id=?", Boolean.class, cuenta);
        for (boolean activo : new boolean[]{false, true, original}) {
            jdbc.update("update cliente set activo=? where usuario_id=?", activo, cuenta);
            assertThat(jdbc.queryForObject("select activo from vinculo_cliente where negocio_id=app_negocio_actual() and usuario_id=?", Boolean.class, cuenta)).isEqualTo(activo);
        }
        assertThat(jdbc.queryForObject("select has_column_privilege(current_user,'vinculo_cliente','activo','UPDATE')", Boolean.class)).isFalse();
    }
}
