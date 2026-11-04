package pe.barberturno.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;

/**
 * Retención de la conversación con el rol de la aplicación (V2-DA-67; 13-v2-10 §8, CJ-10): solo se borran
 * conversaciones sin ficha vencidas y sin mensajes pendientes, en cada negocio; las políticas restrictivas
 * de V115 impiden borrar lo demás aunque se intente; los cuerpos de eventos solo se vacían con un plazo
 * configurado.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class RetencionWhatsAppIT extends WhatsAppSalidaPruebaBase {
    private static final Duration PLAZO = Duration.ofDays(30);

    @Autowired RetencionWhatsApp retencion;
    @Autowired ConversacionPropiedades propiedades;

    private int contar(long negocioId, String tabla, long conversacionId) {
        String columna = tabla.equals("conversacion") ? "id" : "conversacion_id";
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT count(*) FROM " + tabla + " WHERE " + columna + " = ?", Integer.class, conversacionId));
    }

    private void mensaje(long negocioId, long conversacionId, String estado) {
        ejecutor.ejecutar(negocioId, () -> sql.update("""
                INSERT INTO mensaje_whatsapp (conversacion_id, direccion, origen, tipo, contenido, estado,
                    error_codigo, proximo_intento, creado_en, actualizado_en)
                VALUES (?, 'SALIENTE', 'ASISTENTE', 'TEXTO', '{"texto":"hola"}'::jsonb, ?, ?, ?, ?, ?)""",
                conversacionId, estado, estado.equals("FALLIDO") ? "VENTANA_CERRADA" : null,
                estado.equals("PENDIENTE") ? Timestamp.from(reloj.instant()) : null,
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));
    }

    private long conFicha(long negocioId, long conversacionId, String waId) {
        long ficha = ejecutor.ejecutar(negocioId, () -> sql.queryForObject("""
                INSERT INTO cliente (nombre, wa_id, privacidad_aceptada_en, creado_en, actualizado_en)
                VALUES ('Ficha', ?, ?, ?, ?) RETURNING id""", Long.class, waId,
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));
        ejecutor.ejecutar(negocioId, () -> sql.update("UPDATE conversacion SET cliente_id = ? WHERE id = ?", ficha, conversacionId));
        return ficha;
    }

    @Test
    void purgar_borraSoloLasConversacionesSinFichaVencidasYSinPendientes() {
        Instant vieja = reloj.instant().minus(PLAZO).minusSeconds(1);
        Instant reciente = reloj.instant().minus(PLAZO).plusSeconds(60);
        long vencida = crearConversacionConVentana(a, "51999000101", vieja);
        mensaje(a, vencida, "FALLIDO");
        long vencidaConPendiente = crearConversacionConVentana(a, "51999000102", vieja);
        mensaje(a, vencidaConPendiente, "PENDIENTE");
        long vencidaConFicha = crearConversacionConVentana(a, "51999000103", vieja);
        conFicha(a, vencidaConFicha, "51999000103");
        long noVencida = crearConversacionConVentana(a, "51999000104", reciente);
        long vencidaEnB = crearConversacionConVentana(b, "51999000101", vieja);

        int borradas = retencion.purgar();

        assertThat(borradas).isEqualTo(2);
        assertThat(contar(a, "conversacion", vencida)).isZero();
        assertThat(contar(a, "mensaje_whatsapp", vencida)).isZero();
        assertThat(contar(b, "conversacion", vencidaEnB)).isZero();
        assertThat(contar(a, "conversacion", vencidaConPendiente)).isEqualTo(1);
        assertThat(contar(a, "mensaje_whatsapp", vencidaConPendiente)).isEqualTo(1);
        assertThat(contar(a, "conversacion", vencidaConFicha)).isEqualTo(1);
        assertThat(contar(a, "conversacion", noVencida)).isEqualTo(1);
    }

    @Test
    void purgar_sinCreacionNiEntradaReciente_usaLaFechaDeCreacion() {
        long sinEntrada = crearConversacionConVentana(a, "51999000105", null);
        reloj.adelantar(PLAZO.minusSeconds(1));
        assertThat(retencion.purgar()).isZero();
        assertThat(contar(a, "conversacion", sinEntrada)).isEqualTo(1);
        reloj.adelantar(Duration.ofSeconds(2));
        assertThat(retencion.purgar()).isEqualTo(1);
        assertThat(contar(a, "conversacion", sinEntrada)).isZero();
    }

    @Test
    void politicasRestrictivas_impidenBorrarConversacionesConFichaOMensajesPendientes() {
        long conFicha = crearConversacionConVentana(a, "51999000106", reloj.instant().minus(Duration.ofDays(400)));
        conFicha(a, conFicha, "51999000106");
        long conPendiente = crearConversacionConVentana(a, "51999000107", reloj.instant());
        mensaje(a, conPendiente, "PENDIENTE");

        int borradasConFicha = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                sql.update("DELETE FROM conversacion WHERE id = ?", conFicha)));
        int borradosPendientes = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                sql.update("DELETE FROM mensaje_whatsapp WHERE conversacion_id = ?", conPendiente)));

        assertThat(borradasConFicha).isZero();
        assertThat(borradosPendientes).isZero();
        assertThat(contar(a, "conversacion", conFicha)).isEqualTo(1);
        assertThat(contar(a, "mensaje_whatsapp", conPendiente)).isEqualTo(1);
    }

    @Test
    void cuerposDeEventos_sinPlazoConfigurado_noSeTocan() {
        var sinPlazos = new ConversacionPropiedades(propiedades.privacidad(), propiedades.inactividad(),
                propiedades.vigenciaOperacion(), new ConversacionPropiedades.Retencion(false, Duration.ofHours(1), 200,
                        PLAZO, null, null, null));
        var desactivada = new RetencionWhatsApp(entityManager, canales, ejecutor, gestor, sinPlazos, reloj);
        assertThat(sinPlazos.retencion().cuerpoEventos()).isNull();
        long evento = insertarEventoTerminado(reloj.instant().minus(Duration.ofDays(400)));
        desactivada.purgar();
        assertThat(cuerpo(evento)).isNotEqualTo("{}");
    }

    @Test
    void cuerposDeEventos_conPlazo_vacianSoloLosTerminadosVencidos() {
        var conPlazo = new ConversacionPropiedades(propiedades.privacidad(), propiedades.inactividad(),
                propiedades.vigenciaOperacion(), new ConversacionPropiedades.Retencion(false, Duration.ofHours(1), 200,
                        PLAZO, Duration.ofDays(30), null, null));
        var retencionConPlazo = new RetencionWhatsApp(entityManager, canales, ejecutor, gestor, conPlazo, reloj);
        long vencido = insertarEventoTerminado(reloj.instant().minus(Duration.ofDays(31)));
        long reciente = insertarEventoTerminado(reloj.instant().minus(Duration.ofDays(29)));
        long pendiente = insertarEventoPendiente(reloj.instant().minus(Duration.ofDays(31)));

        retencionConPlazo.purgar();

        assertThat(cuerpo(vencido)).isEqualTo("{}");
        assertThat(cuerpo(reciente)).isNotEqualTo("{}");
        assertThat(cuerpo(pendiente)).isNotEqualTo("{}");
    }

    private long insertarEventoTerminado(Instant procesadoEn) {
        return sql.queryForObject("""
                INSERT INTO evento_whatsapp_entrante (clave_idempotencia, tipo, phone_number_id, negocio_id, cuerpo,
                    estado, intentos, proximo_intento, recibido_en, procesado_en)
                VALUES (?, 'MENSAJE', ?, ?, '{"dato":{"from":"51999000001"}}'::jsonb, 'PROCESADO', 1, ?, ?, ?)
                RETURNING id""", Long.class, "clave-" + procesadoEn.toEpochMilli() + "-" + System.nanoTime(),
                NUMERO_A, a, Timestamp.from(procesadoEn), Timestamp.from(procesadoEn), Timestamp.from(procesadoEn));
    }

    private long insertarEventoPendiente(Instant recibidoEn) {
        return sql.queryForObject("""
                INSERT INTO evento_whatsapp_entrante (clave_idempotencia, tipo, phone_number_id, cuerpo,
                    estado, intentos, proximo_intento, recibido_en)
                VALUES (?, 'MENSAJE', ?, '{"dato":{"from":"51999000001"}}'::jsonb, 'PENDIENTE', 0, ?, ?)
                RETURNING id""", Long.class, "clave-pendiente-" + System.nanoTime(), NUMERO_A,
                Timestamp.from(recibidoEn), Timestamp.from(recibidoEn));
    }

    private String cuerpo(long eventoId) {
        return sql.queryForObject("SELECT CAST(cuerpo AS text) FROM evento_whatsapp_entrante WHERE id = ?",
                String.class, eventoId);
    }
}
