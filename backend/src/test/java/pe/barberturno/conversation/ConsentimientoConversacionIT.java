package pe.barberturno.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;

/**
 * Consentimiento y ficha de WhatsApp con el rol de la aplicación y RLS forzada (13-v2-10 §7, CJ-3):
 * la ficha nace solo al aceptar, con el {@code wa_id} y la fecha de aceptación, sin teléfono; la
 * aceptación es idempotente, reutiliza la ficha del mismo {@code wa_id}, no cruza negocios, y el
 * disparador de V115 impide cambiar o falsear el enlace de la conversación.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class ConsentimientoConversacionIT extends WhatsAppSalidaPruebaBase {
    @Autowired ConversacionesWhatsApp conversaciones;
    @Autowired ConsentimientoConversacion consentimiento;

    private long aceptar(long negocioId, long conversacionId, String nombre) {
        return ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(tx ->
                consentimiento.aceptar(conversaciones.bloquear(conversacionId), nombre)));
    }

    private Map<String, Object> ficha(long negocioId, long fichaId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForMap("""
                SELECT negocio_id, usuario_id, nombre, telefono, wa_id, privacidad_aceptada_en, activo
                FROM cliente WHERE id = ?""", fichaId));
    }

    private Long fichaDeConversacion(long negocioId, long conversacionId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT cliente_id FROM conversacion WHERE id = ?", Long.class, conversacionId));
    }

    private int fichasConWaId(long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM cliente WHERE wa_id = ?", Integer.class, waId));
    }

    @Test
    void aceptar_sinFicha_creaLaFichaDelWaIdSinTelefonoYLaEnlaza() {
        long conversacion = crearConversacion(a, WA_CLIENTE);
        assertThat(fichasConWaId(a, WA_CLIENTE)).isZero();

        long fichaId = aceptar(a, conversacion, "  Ana \t Pérez ");

        var fila = ficha(a, fichaId);
        assertThat(((Number) fila.get("negocio_id")).longValue()).isEqualTo(a);
        assertThat(fila.get("usuario_id")).isNull();
        assertThat(fila.get("telefono")).isNull();
        assertThat(fila.get("nombre")).isEqualTo("Ana Pérez");
        assertThat(fila.get("wa_id")).isEqualTo(WA_CLIENTE);
        assertThat(((Timestamp) fila.get("privacidad_aceptada_en")).toInstant()).isEqualTo(reloj.instant());
        assertThat(fila.get("activo")).isEqualTo(true);
        assertThat(fichaDeConversacion(a, conversacion)).isEqualTo(fichaId);
    }

    @Test
    void aceptar_dosVeces_esIdempotenteYNoCreaOtraFicha() {
        long conversacion = crearConversacion(a, WA_CLIENTE);
        long primera = aceptar(a, conversacion, "Ana");
        long segunda = aceptar(a, conversacion, "Otro nombre");
        assertThat(segunda).isEqualTo(primera);
        assertThat(fichasConWaId(a, WA_CLIENTE)).isEqualTo(1);
        assertThat(ficha(a, primera).get("nombre")).isEqualTo("Ana");
    }

    @Test
    void aceptar_conFichaPreviaDelMismoWaIdSinAceptacion_laReutilizaYFijaLaFecha() {
        long previa = ejecutor.ejecutar(a, () -> sql.queryForObject("""
                INSERT INTO cliente (nombre, wa_id, creado_en, actualizado_en) VALUES ('Ficha previa', ?, ?, ?)
                RETURNING id""", Long.class, WA_CLIENTE, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));
        reloj.adelantar(java.time.Duration.ofMinutes(5));
        long conversacion = crearConversacion(a, WA_CLIENTE);

        long fichaId = aceptar(a, conversacion, "Ana");

        assertThat(fichaId).isEqualTo(previa);
        assertThat(fichasConWaId(a, WA_CLIENTE)).isEqualTo(1);
        var fila = ficha(a, previa);
        assertThat(fila.get("nombre")).isEqualTo("Ficha previa");
        assertThat(((Timestamp) fila.get("privacidad_aceptada_en")).toInstant()).isEqualTo(reloj.instant());
    }

    @Test
    void aceptar_conFichaPreviaYaAceptada_conservaSuFechaDeAceptacion() {
        Instant aceptadaAntes = reloj.instant();
        long previa = ejecutor.ejecutar(a, () -> sql.queryForObject("""
                INSERT INTO cliente (nombre, wa_id, privacidad_aceptada_en, creado_en, actualizado_en)
                VALUES ('Ficha previa', ?, ?, ?, ?) RETURNING id""", Long.class, WA_CLIENTE,
                Timestamp.from(aceptadaAntes), Timestamp.from(aceptadaAntes), Timestamp.from(aceptadaAntes)));
        reloj.adelantar(java.time.Duration.ofDays(2));
        long conversacion = crearConversacion(a, WA_CLIENTE);

        aceptar(a, conversacion, "Ana");

        assertThat(((Timestamp) ficha(a, previa).get("privacidad_aceptada_en")).toInstant()).isEqualTo(aceptadaAntes);
    }

    @Test
    void aceptar_mismoWaIdEnDosNegocios_creaUnaFichaPorNegocioSinCruzarlas() {
        long conversacionA = crearConversacion(a, WA_CLIENTE);
        long conversacionB = crearConversacion(b, WA_CLIENTE);

        long fichaA = aceptar(a, conversacionA, "Ana");
        long fichaB = aceptar(b, conversacionB, "Ana");

        assertThat(fichaA).isNotEqualTo(fichaB);
        assertThat(((Number) ficha(a, fichaA).get("negocio_id")).longValue()).isEqualTo(a);
        assertThat(((Number) ficha(b, fichaB).get("negocio_id")).longValue()).isEqualTo(b);
        assertThat(fichasConWaId(a, WA_CLIENTE)).isEqualTo(1);
        assertThat(fichasConWaId(b, WA_CLIENTE)).isEqualTo(1);
        // Con el contexto de A, la ficha de B no existe.
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT count(*) FROM cliente WHERE id = ?",
                Integer.class, fichaB))).isZero();
    }

    @Test
    void enlace_fichaYaFijada_noSePuedeCambiarAOtraFicha() {
        long conversacion = crearConversacion(a, WA_CLIENTE);
        aceptar(a, conversacion, "Ana");
        long otra = ejecutor.ejecutar(a, () -> sql.queryForObject("""
                INSERT INTO cliente (nombre, wa_id, privacidad_aceptada_en, creado_en, actualizado_en)
                VALUES ('Otra', '51999000777', ?, ?, ?) RETURNING id""", Long.class,
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                sql.update("UPDATE conversacion SET cliente_id = ? WHERE id = ?", otra, conversacion))))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThat(fichaDeConversacion(a, conversacion)).isNotEqualTo(otra);
    }

    @Test
    void enlace_fichaDeOtroWaId_seRechazaAunqueLaConversacionNoTengaFicha() {
        long conversacion = crearConversacion(a, WA_CLIENTE);
        long otra = ejecutor.ejecutar(a, () -> sql.queryForObject("""
                INSERT INTO cliente (nombre, wa_id, privacidad_aceptada_en, creado_en, actualizado_en)
                VALUES ('Otra', '51999000777', ?, ?, ?) RETURNING id""", Long.class,
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                conversaciones.enlazarFicha(conversacion, otra))))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        assertThat(fichaDeConversacion(a, conversacion)).isNull();
    }
}
