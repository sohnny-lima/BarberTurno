package pe.barberturno.common.tenancy;

import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/** CI-10: RLS, permisos y defensas con el rol real, nunca superusuario. */
class ConversacionV117IT extends TenancyPruebaBase {
    @Test void estructura_yPermisosExactos() { ConversacionIAEsquemaPruebas.comprobar(jdbc); }

    @Test void revision_creceEnCualquierUpdate_yNoEsEscribible() {
        long c = enNegocio(1L, this::conversacion);
        enNegocio(1L, () -> {
            assertThat(jdbc.queryForObject("select revision from conversacion where id=?", Long.class, c)).isZero();
            for (String cambio : List.of("estado=estado", "version=version+1", "entradas_minuto=1", "ultima_entrada_en=CURRENT_TIMESTAMP"))
                jdbc.update("update conversacion set " + cambio + " where id=?", c);
            assertThat(jdbc.queryForObject("select revision from conversacion where id=?", Long.class, c)).isEqualTo(4L);
            return null;
        });
        falla("42501", () -> enNegocio(1L, () -> jdbc.update("update conversacion set revision=0 where id=?", c)));
    }

    @Test void pendientes_unicoPorConversacion_yBorradoRestrictivo() {
        long c = enNegocio(1L, this::conversacion);
        long t = enNegocio(1L, () -> turno(c, "PENDIENTE", "Texto", 0));
        falla("23505", () -> enNegocio(1L, () -> turno(c, "PENDIENTE", "Otro", 0)));
        assertThat(enNegocio(1L, () -> jdbc.update("delete from turno_ia where id=?", t))).isZero();
        enNegocio(1L, () -> jdbc.update("update turno_ia set estado='DESCARTADO',texto=null where id=?", t));
        assertThat(enNegocio(1L, () -> jdbc.update("delete from turno_ia where id=?", t))).isEqualTo(1);
        for (String estado : List.of("HECHO", "DESCARTADO", "FALLIDO")) {
            falla("23514", () -> enNegocio(1L, () -> turno(c, estado, "Texto", 0)));
            enNegocio(1L, () -> turno(c, estado, null, 0));
        }
        falla("23514", () -> enNegocio(1L, () -> turno(c, "PENDIENTE", null, 0)));
        for (int intentos : List.of(-1, 4)) falla("23514", () -> enNegocio(1L, () -> turno(c, "PENDIENTE", "Texto", intentos)));
    }

    @Test void dosNegocios_noCruzanConversacionNiTurno_ySinContextoFallaInsert() throws Exception {
        long b = negocioB();
        long ca = enNegocio(1L, this::conversacion), cb = enNegocio(b, this::conversacion);
        long ta = enNegocio(1L, () -> turno(ca, "PENDIENTE", "Texto", 0));
        long tb = enNegocio(b, () -> turno(cb, "PENDIENTE", "Otro", 0));
        assertThat(enNegocio(null, () -> jdbc.queryForObject("select count(*) from turno_ia", Long.class))).isZero();
        assertThat(enNegocio(b, () -> jdbc.queryForList("select id from turno_ia", Long.class))).containsExactly(tb);
        falla("23503", () -> enNegocio(1L, () -> turno(cb, "PENDIENTE", "Otro", 0)));
        falla("23503", () -> enNegocio(1L, () -> llamada(tb, ca, 1)));
        falla("42501", () -> enNegocio(null, () -> turno(ca, "PENDIENTE", "Otro", 0)));
        enNegocio(1L, () -> llamada(ta, ca, 1));
        falla("23505", () -> enNegocio(1L, () -> llamada(ta, ca, 1)));
        assertThat(enNegocio(b, () -> jdbc.queryForObject("select count(*) from llamada_ia", Long.class))).isZero();
    }

    @Test void uso_upsertRealBajoRls_noBorrableNiNegativo() throws Exception {
        long b = negocioB();
        enNegocio(1L, () -> agregado(1L, 10));
        enNegocio(1L, () -> agregado(1L, 20));
        assertThat(enNegocio(1L, () -> jdbc.queryForObject("select coste_micro_usd from uso_ia", Long.class))).isEqualTo(30L);
        falla("42501", () -> enNegocio(b, () -> agregado(1L, 1)));
        falla("42501", () -> enNegocio(null, () -> agregado(1L, 1)));
        falla("42501", () -> enNegocio(1L, () -> jdbc.update("delete from uso_ia")));
        falla("23514", () -> enNegocio(b, () -> agregado(b, -1)));
        assertThat(enNegocio(null, () -> jdbc.queryForObject("select count(*) from uso_ia", Long.class))).isZero();
        assertThat(enNegocio(b, () -> jdbc.queryForObject("select count(*) from uso_ia", Long.class))).isZero();
    }

    private long conversacion() {
        return jdbc.queryForObject("""
                insert into conversacion(wa_id,creado_en,actualizado_en)
                values ('51900000117',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id
                """, Long.class);
    }
    private long turno(long c, String estado, String texto, int intentos) {
        return jdbc.queryForObject("""
                insert into turno_ia(conversacion_id,revision,texto,estado,intentos,creado_en,actualizado_en)
                values (?,0,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id
                """, Long.class, c, texto, estado, intentos);
    }
    private int llamada(long t, long c, int numero) {
        return jdbc.update("""
                insert into llamada_ia(turno_id,numero,conversacion_id,dia,modelo,coste_reservado_micro_usd)
                values (?,?,?,CURRENT_DATE,'modelo-ficticio',1)
                """, t, numero, c);
    }
    private int agregado(long negocio, long coste) {
        return jdbc.update("""
                insert into uso_ia(negocio_id,dia,llamadas,coste_micro_usd) values (?,CURRENT_DATE,1,?)
                on conflict (negocio_id,dia) do update set llamadas=uso_ia.llamadas+1,
                    coste_micro_usd=uso_ia.coste_micro_usd+EXCLUDED.coste_micro_usd
                """, negocio, coste);
    }
    private void falla(String sqlState, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(DataAccessException.class,
                e -> assertThat(((SQLException) e.getMostSpecificCause()).getSQLState()).isEqualTo(sqlState));
    }
}
