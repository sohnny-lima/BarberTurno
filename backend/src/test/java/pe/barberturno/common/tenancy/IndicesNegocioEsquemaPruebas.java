package pe.barberturno.common.tenancy;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Verifica los accesos aditivos de V107 sin depender del plan elegido por PostgreSQL. */
final class IndicesNegocioEsquemaPruebas {
    private IndicesNegocioEsquemaPruebas() { }
    static void comprobar(JdbcTemplate jdbc) {
        for (String indice : List.of("reserva_negocio_barbero_inicio_ix", "reserva_negocio_cliente_inicio_ix",
                "reserva_negocio_inicio_ix", "servicio_negocio_activo_ix", "barbero_negocio_activo_ix",
                "notificacion_negocio_usuario_ix", "jornada_negocio_barbero_ix", "bloqueo_negocio_barbero_ix",
                "auditoria_reserva_negocio_reserva_ix")) {
            assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname='public' and indexname=?", String.class, indice))
                    .as(indice).contains("USING btree (negocio_id,");
        }
    }
}
