package pe.barberturno.common.tenancy;

import java.util.*;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/** Permisos reales, RLS, restricciones y defensa de retención de V115, sin modificar ayudantes ajenos. */
class ConversacionV115IT extends TenancyPruebaBase {
    @Test void permisosExactos_rlsForzada_yColumnasInmutables() {
        var actualizables = Map.of(
                "operacion_pendiente", Set.of("estado", "resultado", "actualizado_en"),
                "conversacion", Set.of("cliente_id", "estado", "version", "ultima_entrada_en", "actualizado_en"),
                "evento_whatsapp_entrante", Set.of("cuerpo", "negocio_id", "estado", "motivo", "intentos", "proximo_intento", "procesado_en"));
        for (var entrada : actualizables.entrySet()) {
            String tabla = entrada.getKey();
            for (var columna : jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' and table_name=?", String.class, tabla)) {
                assertThat(jdbc.queryForObject("select has_column_privilege(current_user,?,?,'UPDATE')", Boolean.class, tabla, columna))
                        .as("UPDATE %s.%s", tabla, columna).isEqualTo(entrada.getValue().contains(columna));
                for (String privilegio : List.of("SELECT", "INSERT")) {
                    assertThat(jdbc.queryForObject("select has_column_privilege(current_user,?,?,?)", Boolean.class, tabla, columna, privilegio)).isTrue();
                }
            }
            for (String privilegio : List.of("TRUNCATE", "REFERENCES", "TRIGGER")) {
                assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,?)", Boolean.class, tabla, privilegio)).isFalse();
            }
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,'DELETE')", Boolean.class, tabla))
                    .isEqualTo(tabla.equals("conversacion"));
        }
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'mensaje_whatsapp','DELETE')", Boolean.class)).isTrue();
        for (String tabla : List.of("operacion_pendiente", "conversacion", "mensaje_whatsapp")) {
            assertThat(jdbc.queryForMap("select relrowsecurity,relforcerowsecurity from pg_class where oid=?::regclass", tabla))
                    .containsEntry("relrowsecurity", true).containsEntry("relforcerowsecurity", true);
        }
        for (String tabla : List.of("conversacion", "mensaje_whatsapp")) {
            assertThat(jdbc.queryForObject("select count(*) from pg_policies where tablename=? and cmd='DELETE' and permissive='RESTRICTIVE'", Long.class, tabla)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from pg_policies where tablename=? and cmd='ALL' and permissive='PERMISSIVE'", Long.class, tabla)).isEqualTo(1);
        }
    }

    @Test void trigger_enlaceInicialSoloMismoRemitente_yNoSeCambiaNiSeVacia() {
        long ficha = enNegocio(1L, () -> ficha("51900000001"));
        long otra = enNegocio(1L, () -> ficha("51900000002"));
        long conversacion = enNegocio(1L, () -> conversacion("51900000001", null));
        falla("23514", () -> enNegocio(1L, () -> jdbc.update("update conversacion set cliente_id=? where id=?", otra, conversacion)));
        assertThat(enNegocio(1L, () -> jdbc.update("update conversacion set cliente_id=? where id=?", ficha, conversacion))).isEqualTo(1);
        assertThat(enNegocio(1L, () -> jdbc.update("update conversacion set estado='{}',version=version+1 where id=?", conversacion))).isEqualTo(1);
        falla("23514", () -> enNegocio(1L, () -> jdbc.update("update conversacion set cliente_id=null where id=?", conversacion)));
        falla("23514", () -> enNegocio(1L, () -> jdbc.update("update conversacion set cliente_id=? where id=?", otra, conversacion)));
        falla("23514", () -> enNegocio(1L, () -> conversacion("51900000003", ficha)));
        assertThat(enNegocio(1L, () -> jdbc.queryForObject("select cliente_id from conversacion where id=?", Long.class, conversacion))).isEqualTo(ficha);
    }

    @Test void checks_nonceResultadoEstadoTipo_yUnicaVigente() {
        long ficha = enNegocio(1L, () -> ficha("51900000001"));
        long conv = enNegocio(1L, () -> conversacion("51900000001", ficha));
        for (int longitud : List.of(0, 15, 17)) {
            falla("23514", () -> enNegocio(1L, () -> insertar(conv, ficha, "CREAR", "VIGENTE", longitud, null)));
        }
        for (String estado : List.of("EJECUTADA", "RECHAZADA")) {
            falla("23514", () -> enNegocio(1L, () -> insertar(conv, ficha, "CREAR", estado, 16, null)));
            enNegocio(1L, () -> insertar(conv, ficha, "CREAR", estado, 16, "{}"));
        }
        for (String estado : List.of("VIGENTE", "ANULADA", "CADUCADA")) {
            falla("23514", () -> enNegocio(1L, () -> insertar(conv, ficha, "CREAR", estado, 16, "{}")));
        }
        falla("23514", () -> enNegocio(1L, () -> insertar(conv, ficha, "INVENTAR", "ANULADA", 16, null)));
        falla("23514", () -> enNegocio(1L, () -> insertar(conv, ficha, "CREAR", "OTRA", 16, null)));
        enNegocio(1L, () -> insertar(conv, ficha, "CREAR", "VIGENTE", 16, null));
        falla("23505", () -> enNegocio(1L, () -> insertar(conv, ficha, "CANCELAR", "VIGENTE", 16, null)));
        assertThat(enNegocio(1L, () -> jdbc.update("update operacion_pendiente set estado='ANULADA',actualizado_en=CURRENT_TIMESTAMP where estado='VIGENTE'"))).isEqualTo(1);
        enNegocio(1L, () -> insertar(conv, ficha, "REPROGRAMAR", "VIGENTE", 16, null));
        falla("42501", () -> enNegocio(1L, () -> jdbc.update("delete from operacion_pendiente")));
    }

    @Test void dosNegocios_fkCompuestasAConversacionYFicha_sinContextoNoSeVeNada() throws Exception {
        long b = negocioB();
        long ficha = enNegocio(1L, () -> ficha("51900000001"));
        long conv = enNegocio(1L, () -> conversacion("51900000001", ficha));
        long fichaB = enNegocio(b, () -> ficha("51900000001"));
        long convB = enNegocio(b, () -> conversacion("51900000001", fichaB));
        falla("23503", () -> enNegocio(1L, () -> insertar(convB, ficha, "CREAR", "VIGENTE", 16, null)));
        falla("23503", () -> enNegocio(1L, () -> insertar(conv, fichaB, "CREAR", "VIGENTE", 16, null)));
        enNegocio(1L, () -> insertar(conv, ficha, "CREAR", "VIGENTE", 16, null));
        assertThat(enNegocio(b, () -> jdbc.queryForObject("select count(*) from operacion_pendiente", Long.class))).isZero();
        assertThat(enNegocio(null, () -> jdbc.queryForObject("select count(*) from operacion_pendiente", Long.class))).isZero();
        falla("42501", () -> enNegocio(null, () -> insertar(conv, ficha, "CREAR", "VIGENTE", 16, null)));
    }

    @Test void borradoRestrictivo_niConversacionConFicha_niPendiente_niLigadoAReserva() {
        var datos = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "v115"));
        enNegocio(1L, () -> jdbc.update("update cliente set wa_id='51900000001' where id=?", datos.ficha()));
        long conFicha = enNegocio(1L, () -> conversacion("51900000001", datos.ficha()));
        long sinFicha = enNegocio(1L, () -> conversacion("51900000002", null));
        enNegocio(1L, () -> {
            assertThat(jdbc.update("delete from conversacion where id=?", conFicha)).isZero();
            long protegido = mensaje(conFicha, null, true);
            long pendiente = mensaje(sinFicha, null, false);
            long ligado = mensaje(sinFicha, datos.reserva(), true);
            long borrable = mensaje(sinFicha, null, true);
            assertThat(jdbc.update("delete from mensaje_whatsapp where id in (?,?,?)", protegido, pendiente, ligado)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from mensaje_whatsapp where id in (?,?,?)", Long.class, protegido, pendiente, ligado)).isEqualTo(3);
            assertThat(jdbc.update("delete from mensaje_whatsapp where id=?", borrable)).isEqualTo(1);
            long vacia = conversacion("51900000003", null);
            assertThat(jdbc.update("delete from conversacion where id=?", vacia)).isEqualTo(1);
            return null;
        });
    }

    @Test void reservaCanalYAuditoria_unSoloActor_yFkCompuesta() throws Exception {
        var datos = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "actor-v115"));
        long b = negocioB();
        long ajena = enNegocio(b, () -> ficha("51900000001"));
        falla("23514", () -> enNegocio(1L, () -> jdbc.update("update reserva set creada_por=null where id=?", datos.reserva())));
        falla("23514", () -> enNegocio(1L, () -> jdbc.update("update reserva set canal='OTRO' where id=?", datos.reserva())));
        enNegocio(1L, () -> jdbc.update("update reserva set canal='WHATSAPP',creada_por=null where id=?", datos.reserva()));
        falla("23514", () -> enNegocio(1L, () -> jdbc.update("update auditoria_reserva set actor_id=null")));
        falla("23514", () -> enNegocio(1L, () -> jdbc.update("update auditoria_reserva set actor_cliente_id=?", datos.ficha())));
        falla("23503", () -> enNegocio(1L, () -> jdbc.update("update auditoria_reserva set actor_id=null,actor_cliente_id=?", ajena)));
        enNegocio(1L, () -> jdbc.update("update auditoria_reserva set actor_id=null,actor_cliente_id=?", datos.ficha()));
    }

    private long ficha(String wa) {
        return jdbc.queryForObject("insert into cliente(nombre,wa_id,creado_en,actualizado_en) values ('Ficha ficticia',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id", Long.class, wa);
    }
    private long conversacion(String wa, Long ficha) {
        return jdbc.queryForObject("insert into conversacion(wa_id,cliente_id,creado_en,actualizado_en) values (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id", Long.class, wa, ficha);
    }
    private long insertar(long conv, long ficha, String tipo, String estado, int longitud, String resultado) {
        return jdbc.queryForObject("""
                insert into operacion_pendiente(conversacion_id,cliente_id,tipo,datos,nonce,estado,expira_en,resultado,creado_en,actualizado_en)
                values (?,?,?,'{}',?,?,CURRENT_TIMESTAMP,cast(? as jsonb),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id
                """, Long.class, conv, ficha, tipo, new byte[longitud], estado, resultado);
    }
    private long mensaje(long conv, Long reserva, boolean terminado) {
        return jdbc.queryForObject("""
                insert into mensaje_whatsapp(conversacion_id,reserva_id,direccion,origen,tipo,contenido,estado,error_codigo,proximo_intento,creado_en,actualizado_en)
                values (?,?,'SALIENTE','SISTEMA','TEXTO','{}',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id
                """, Long.class, conv, reserva, terminado ? "FALLIDO" : "PENDIENTE", terminado ? "VENTANA_CERRADA" : null);
    }
    private void falla(String sqlState, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(DataAccessException.class,
                e -> assertThat(((SQLException) e.getMostSpecificCause()).getSQLState()).isEqualTo(sqlState));
    }
}
