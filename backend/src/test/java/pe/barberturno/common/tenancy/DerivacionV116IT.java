package pe.barberturno.common.tenancy;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/** Defensas V116 probadas con el rol operativo y dos negocios, sin desactivar RLS. */
class DerivacionV116IT extends TenancyPruebaBase {
    @Test void permisosExactos_indiceYPoliticasRestrictivas_sustituyenLasAnteriores() {
        for (String tabla : List.of("conversacion", "mensaje_whatsapp", "operacion_pendiente")) {
            Set<String> permitidas = switch (tabla) {
                case "conversacion" -> Set.of("cliente_id", "estado", "version", "ultima_entrada_en", "actualizado_en",
                        "modo", "derivada_en", "motivo_derivacion", "ultima_respuesta_personal_en");
                case "mensaje_whatsapp" -> Set.of("contenido", "wamid", "estado", "intentos", "inciertos",
                        "proximo_intento", "error_codigo", "codigo_meta", "enviado_como", "actualizado_en");
                default -> Set.of("estado", "resultado", "actualizado_en");
            };
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,'UPDATE')", Boolean.class, tabla)).isFalse();
            for (String columna : jdbc.queryForList("select column_name from information_schema.columns where table_name=?", String.class, tabla)) {
                assertThat(jdbc.queryForObject("select has_column_privilege(current_user,?,?,'UPDATE')", Boolean.class, tabla, columna))
                        .as("%s.%s", tabla, columna).isEqualTo(permitidas.contains(columna));
            }
            assertThat(jdbc.queryForObject("select has_table_privilege(current_user,?,'DELETE')", Boolean.class, tabla)).isTrue();
            var politicas = jdbc.queryForList("select permissive,cmd,qual from pg_policies where tablename=?", tabla);
            assertThat(politicas).hasSize(2);
            var borrado = politicas.stream().filter(p -> p.get("cmd").equals("DELETE")).findFirst().orElseThrow();
            assertThat(borrado.get("permissive")).isEqualTo("RESTRICTIVE");
            assertThat(borrado.get("qual").toString()).contains("anonimizada_en IS NOT NULL", "negocio_id", "cliente_id");
        }
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where indexname='conversacion_bandeja_ix'", String.class))
                .contains("(negocio_id, ultima_entrada_en DESC)", "WHERE ((modo)::text = 'HUMANO'::text)");
        assertThat(jdbc.queryForObject("select count(*) from pg_trigger where tgname='mensaje_retirada_contenido' and not tgisinternal", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select is_nullable,data_type from information_schema.columns where table_name='notificacion' and column_name='reserva_id'"))
                .containsEntry("is_nullable", "YES").containsEntry("data_type", "bigint");
        for (String columna : List.of("nombre", "wa_id", "telefono", "anonimizada_en")) {
            assertThat(jdbc.queryForObject("select has_column_privilege(current_user,'cliente',?,'UPDATE')", Boolean.class, columna)).isTrue();
        }
    }

    @Test void dosRamasDeConversacion_yMotivos_rechazanEstadosIncompletos() {
        long f = enNegocio(1L, () -> ficha("51900000001"));
        long c = enNegocio(1L, () -> conversacion("51900000001", f));
        for (String asignacion : List.of("modo='HUMANO'", "derivada_en=CURRENT_TIMESTAMP", "motivo_derivacion='PETICION'",
                "ultima_respuesta_personal_en=CURRENT_TIMESTAMP")) {
            falla("23514", "conversacion_derivacion_coherente", () -> enNegocio(1L,
                    () -> jdbc.update("update conversacion set " + asignacion + " where id=?", c)));
        }
        falla("23514", "conversacion_motivo_derivacion_valido", () -> enNegocio(1L, () -> jdbc.update("""
                update conversacion set modo='HUMANO',derivada_en=CURRENT_TIMESTAMP,motivo_derivacion='OTRO' where id=?
                """, c)));
        for (String motivo : List.of("PETICION", "SIN_PROGRESO", "LIMITE", "HERRAMIENTA")) {
            enNegocio(1L, () -> jdbc.update("""
                    update conversacion set modo='HUMANO',derivada_en=CURRENT_TIMESTAMP,motivo_derivacion=? where id=?
                    """, motivo, c));
        }
        long sinFicha = enNegocio(1L, () -> conversacion("51900000002", null));
        falla("23514", "conversacion_derivacion_coherente", () -> enNegocio(1L, () -> jdbc.update("""
                update conversacion set modo='HUMANO',derivada_en=CURRENT_TIMESTAMP,motivo_derivacion='PETICION' where id=?
                """, sinFicha)));
    }

    @Test void actorPersonal_yOrigenEntrante_seExigenEnSql() {
        var d = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "v116"));
        long c = enNegocio(1L, () -> conversacion("51900000001", null));
        falla("23514", "mensaje_persona_coherente", () -> enNegocio(1L, () -> mensaje(c, "SALIENTE", "PERSONA", null, false)));
        falla("23514", "mensaje_persona_coherente", () -> enNegocio(1L, () -> mensaje(c, "SALIENTE", "SISTEMA", d.admin(), false)));
        falla("23503", "mensaje_enviado_por_fk", () -> enNegocio(1L, () -> mensaje(c, "SALIENTE", "PERSONA", 999999L, false)));
        falla("23514", "mensaje_entrada_origen_coherente", () -> enNegocio(1L, () -> mensaje(c, "ENTRANTE", "SISTEMA", null, true)));
        enNegocio(1L, () -> mensaje(c, "ENTRANTE", "CLIENTE", null, true));
        long id = enNegocio(1L, () -> mensaje(c, "SALIENTE", "PERSONA", d.admin(), false));
        enNegocio(1L, () -> jdbc.update("update mensaje_whatsapp set estado='FALLIDO',error_codigo='MODO_HUMANO' where id=?", id));
    }

    @Test void aviso_destinoExclusivo_yFkCompuesta_noPermitenCruzarNegocios() throws Exception {
        long b = negocioB();
        var d = enNegocio(1L, () -> DatosNegocioPrueba.crear(jdbc, "aviso-v116"));
        long c = enNegocio(1L, () -> conversacion("51900000001", null));
        long ajena = enNegocio(b, () -> conversacion("51900000001", null));
        falla("23514", "notificacion_destino_unico", () -> enNegocio(1L, () -> aviso(d.admin(), null, null)));
        falla("23514", "notificacion_destino_unico", () -> enNegocio(1L, () -> aviso(d.admin(), d.reserva(), c)));
        falla("23503", "notificacion_conversacion_fk", () -> enNegocio(1L, () -> aviso(d.admin(), null, ajena)));
        enNegocio(1L, () -> aviso(d.admin(), null, c));
        enNegocio(1L, () -> aviso(d.admin(), d.reserva(), null));
        assertThat(enNegocio(b, () -> jdbc.queryForObject("select count(*) from notificacion", Integer.class))).isZero();
    }

    @Test void contenido_soloSeRetiraTerminadoConFicha_yNoSeRestaura() {
        long f = enNegocio(1L, () -> ficha("51900000001"));
        long c = enNegocio(1L, () -> conversacion("51900000001", f));
        long sinFicha = enNegocio(1L, () -> conversacion("51900000002", null));
        long pendiente = enNegocio(1L, () -> mensaje(c, "SALIENTE", "SISTEMA", null, false));
        long terminado = enNegocio(1L, () -> mensaje(c, "ENTRANTE", "CLIENTE", null, true));
        long sinConsentir = enNegocio(1L, () -> mensaje(sinFicha, "ENTRANTE", "CLIENTE", null, true));
        for (long id : List.of(pendiente, sinConsentir)) {
            falla("23514", null, () -> enNegocio(1L, () -> jdbc.update("update mensaje_whatsapp set contenido='{\"retirado\":true}' where id=?", id)));
        }
        falla("23514", null, () -> enNegocio(1L, () -> jdbc.update("""
                update mensaje_whatsapp set estado='FALLIDO',error_codigo='VENTANA_CERRADA',contenido='{"retirado":true}' where id=?
                """, pendiente)));
        falla("23514", null, () -> enNegocio(1L, () -> jdbc.update("update mensaje_whatsapp set contenido='{\"texto\":\"otro\"}' where id=?", terminado)));
        falla("23514", null, () -> enNegocio(1L, () -> jdbc.update("""
                update mensaje_whatsapp set estado='PENDIENTE',contenido='{"retirado":true}' where id=?
                """, terminado)));
        assertThat(enNegocio(1L, () -> jdbc.update("update mensaje_whatsapp set contenido='{\"retirado\":true}' where id=?", terminado))).isEqualTo(1);
        falla("23514", null, () -> enNegocio(1L, () -> jdbc.update("update mensaje_whatsapp set contenido='{\"texto\":\"restaurado\"}' where id=?", terminado)));
        // El disparador no impide las actualizaciones normales del enviador.
        assertThat(enNegocio(1L, () -> jdbc.update("update mensaje_whatsapp set contenido=contenido,intentos=intentos+1 where id=?", pendiente))).isEqualTo(1);
    }

    @Test void borrado_operacionesVigentes_yMensajesPendientes_siguenProtegidos_alAnonimizar() {
        long f = enNegocio(1L, () -> ficha("51900000001"));
        long c = enNegocio(1L, () -> conversacion("51900000001", f));
        enNegocio(1L, () -> jdbc.update("""
                insert into operacion_pendiente(conversacion_id,cliente_id,tipo,datos,nonce,expira_en,creado_en,actualizado_en)
                values (?,?,'CREAR','{}',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, c, f, new byte[16]));
        assertThat(enNegocio(1L, () -> jdbc.update("delete from operacion_pendiente"))).isZero();
        long pendiente = enNegocio(1L, () -> mensaje(c, "SALIENTE", "SISTEMA", null, false));
        long terminado = enNegocio(1L, () -> mensaje(c, "ENTRANTE", "CLIENTE", null, true));
        enNegocio(1L, () -> jdbc.update("update cliente set wa_id=null,telefono=null,anonimizada_en=CURRENT_TIMESTAMP where id=?", f));
        assertThat(enNegocio(1L, () -> jdbc.update("delete from operacion_pendiente"))).isEqualTo(1);
        assertThat(enNegocio(1L, () -> jdbc.update("delete from mensaje_whatsapp where id in (?,?)", pendiente, terminado))).isEqualTo(1);
        assertThat(enNegocio(1L, () -> jdbc.queryForObject("select count(*) from mensaje_whatsapp where id=?", Integer.class, pendiente))).isEqualTo(1);
    }

    private long ficha(String wa) { return jdbc.queryForObject("insert into cliente(nombre,wa_id,creado_en,actualizado_en) values ('Ficha',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id", Long.class, wa); }
    private long conversacion(String wa, Long ficha) { return jdbc.queryForObject("insert into conversacion(wa_id,cliente_id,creado_en,actualizado_en) values (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id", Long.class, wa, ficha); }
    private long mensaje(long c, String direccion, String origen, Long actor, boolean recibido) {
        return jdbc.queryForObject("""
                insert into mensaje_whatsapp(conversacion_id,direccion,origen,enviado_por,tipo,contenido,estado,proximo_intento,creado_en,actualizado_en)
                values (?,?,?,?,'TEXTO','{"texto":"ficticio"}',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id
                """, Long.class, c, direccion, origen, actor, recibido ? "RECIBIDO" : "PENDIENTE");
    }
    private int aviso(long usuario, Long reserva, Long conversacion) {
        return jdbc.update("insert into notificacion(usuario_id,reserva_id,conversacion_id,tipo,mensaje,creado_en) values (?,?,?,'DERIVACION','Atención',CURRENT_TIMESTAMP)", usuario, reserva, conversacion);
    }
    private void falla(String estado, String restriccion, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(DataAccessException.class, e -> {
            assertThat(((SQLException) e.getMostSpecificCause()).getSQLState()).isEqualTo(estado);
            if (restriccion != null) assertThat(((org.postgresql.util.PSQLException) e.getMostSpecificCause())
                    .getServerErrorMessage().getConstraint()).isEqualTo(restriccion);
        });
    }
}
