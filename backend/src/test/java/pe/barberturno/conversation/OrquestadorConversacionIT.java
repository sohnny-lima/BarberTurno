package pe.barberturno.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.whatsapp.MensajeEntrante;
import pe.barberturno.whatsapp.ReceptorMensajesWhatsApp;
import pe.barberturno.whatsapp.WhatsAppSalidaPruebaBase;

/**
 * La conversación guiada como receptor real de V2-08 con el rol de la aplicación (13-v2-10 §2, CJ-9):
 * es el único {@link ReceptorMensajesWhatsApp}, registra la ventana y responde por la outbox; y si el
 * negocio deja de poder atender entre la resolución y la entrega (canal desconectado, negocio suspendido
 * o sin propietario), no escribe nada: ni conversación, ni ventana, ni ficha, ni respuesta.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class OrquestadorConversacionIT extends WhatsAppSalidaPruebaBase {
    @Autowired ReceptorMensajesWhatsApp receptor;
    @Autowired pe.barberturno.whatsapp.RegistroEntradaWhatsApp registroEntrada;

    private MensajeEntrante texto(long negocioId, String waId, String cuerpo) {
        var dato = mapper.createObjectNode().put("from", waId).put("id", "wamid.prueba." + System.nanoTime())
                .put("type", "text");
        dato.putObject("text").put("body", cuerpo);
        return new MensajeEntrante(1, negocioId, waId, OptionalLong.empty(), "Ana", dato.path("id").asString(),
                "text", reloj.instant(), dato);
    }

    private void recibir(MensajeEntrante mensaje) {
        ejecutor.ejecutar(mensaje.negocioId(), () -> new TransactionTemplate(gestor).execute(tx -> {
            receptor.recibir(mensaje);
            return null;
        }));
    }

    private int conversaciones(long negocioId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject("SELECT count(*) FROM conversacion", Integer.class));
    }

    @Test
    void receptor_esLaConversacionGuiada() {
        assertThat(receptor).isInstanceOf(OrquestadorConversacion.class);
    }

    @Test
    void negocioOperativo_registraLaVentanaYRespondeConElMenuEnLaOutbox() {
        recibir(texto(a, WA_CLIENTE, "hola"));
        assertThat(conversaciones(a)).isEqualTo(1);
        var mensajes = filasMensajes(a);
        assertThat(mensajes).hasSize(1);
        assertThat(mensajes.getFirst()).containsEntry("direccion", "SALIENTE").containsEntry("origen", "ASISTENTE")
                .containsEntry("tipo", "INTERACTIVO").containsEntry("estado", "PENDIENTE");
        assertThat(conversaciones(b)).isZero();
        assertThat(filasMensajes(b)).isEmpty();
    }

    @Test
    void canalDesconectado_noEscribeNada() {
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                sql.update("UPDATE canal_whatsapp SET estado = 'DESCONECTADO' WHERE negocio_id = ?", a)));
        recibir(texto(a, WA_CLIENTE, "hola"));
        assertThat(conversaciones(a)).isZero();
        assertThat(filasMensajes(a)).isEmpty();
    }

    @Test
    void negocioSuspendido_noEscribeNada() {
        sql.update("UPDATE negocio SET estado = 'SUSPENDIDO' WHERE id = ?", a);
        recibir(texto(a, WA_CLIENTE, "hola"));
        assertThat(conversaciones(a)).isZero();
        assertThat(filasMensajes(a)).isEmpty();
    }

    @Test
    void sinPropietarioActivo_noEscribeNada() {
        sql.update("""
                UPDATE membresia SET estado = 'REVOCADA', motivo_revocacion = 'PLATAFORMA', actualizado_en = ?
                WHERE negocio_id = ? AND rol = 'PROPIETARIO'""", java.sql.Timestamp.from(reloj.instant()), a);
        recibir(texto(a, WA_CLIENTE, "hola"));
        assertThat(conversaciones(a)).isZero();
        assertThat(filasMensajes(a)).isEmpty();
    }

    private MensajeEntrante boton(long negocioId, String waId, String id) {
        var dato = mapper.createObjectNode().put("from", waId).put("id", "wamid.prueba." + System.nanoTime())
                .put("type", "interactive");
        dato.putObject("interactive").put("type", "button_reply").putObject("button_reply").put("id", id).put("title", "x");
        return new MensajeEntrante(1, negocioId, waId, OptionalLong.empty(), "Ana", dato.path("id").asString(),
                "interactive", reloj.instant(), dato);
    }

    /** Conversación con ficha, estado con franja pendiente y una operación vigente. */
    private long[] conversacionConPropuesta() {
        long conversacion = crearConversacionConVentana(a, WA_CLIENTE, reloj.instant());
        var ahora = java.sql.Timestamp.from(reloj.instant());
        long ficha = ejecutor.ejecutar(a, () -> sql.queryForObject("""
                INSERT INTO cliente (nombre, wa_id, privacidad_aceptada_en, creado_en, actualizado_en)
                VALUES ('Ana', ?, ?, ?, ?) RETURNING id""", Long.class, WA_CLIENTE, ahora, ahora, ahora));
        long operacion = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx -> {
            sql.update("""
                    UPDATE conversacion SET cliente_id = ?, estado = '{"paso":"CONFIRMACION","pendiente":"f:1:1:1"}'::jsonb
                    WHERE id = ?""", ficha, conversacion);
            return sql.queryForObject("""
                    INSERT INTO operacion_pendiente (conversacion_id, cliente_id, tipo, datos, nonce, expira_en,
                        creado_en, actualizado_en)
                    VALUES (?, ?, 'CANCELAR', '{}'::jsonb, decode(repeat('ab', 16), 'hex'), ?, ?, ?) RETURNING id""",
                    Long.class, conversacion, ficha, java.sql.Timestamp.from(reloj.instant().plus(Duration.ofDays(3))),
                    ahora, ahora);
        }));
        return new long[] {conversacion, operacion};
    }

    private String estado(long conversacion) {
        return ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT CAST(estado AS text) FROM conversacion WHERE id = ?", String.class, conversacion));
    }

    private String estadoOperacion(long operacion) {
        return ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT estado FROM operacion_pendiente WHERE id = ?", String.class, operacion));
    }

    @Test
    void entradaTras24Horas_vaciaElEstadoYAnulaLaOperacionAunqueElPasoNoVuelvaAlMenu() {
        var ids = conversacionConPropuesta();
        reloj.adelantar(Duration.ofHours(24));
        recibir(boton(a, WA_CLIENTE, "m:RESERVAR"));
        assertThat(estado(ids[0])).doesNotContain("pendiente").doesNotContain("CONFIRMACION");
        assertThat(estadoOperacion(ids[1])).isEqualTo("ANULADA");
    }

    @Test
    void entradaAntesDe24Horas_conservaLaOperacionVigenteEnUnPasoQueNoEsElMenu() {
        var ids = conversacionConPropuesta();
        reloj.adelantar(Duration.ofHours(24).minusSeconds(1));
        recibir(boton(a, WA_CLIENTE, "m:RESERVAR"));
        assertThat(estado(ids[0])).contains("pendiente");
        assertThat(estadoOperacion(ids[1])).isEqualTo("VIGENTE");
    }

    @Test
    void registrar_devuelveLaEntradaAnteriorSoloAlActualizar() {
        var primera = texto(a, WA_CLIENTE, "hola");
        var registro = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                registroEntrada.registrar(primera)));
        assertThat(registro.entradaAnterior()).isEmpty();
        var anterior = reloj.instant();
        reloj.adelantar(Duration.ofMinutes(5));
        var segunda = texto(a, WA_CLIENTE, "hola");
        var actualizado = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                registroEntrada.registrar(segunda)));
        assertThat(actualizado.conversacionId()).isEqualTo(registro.conversacionId());
        assertThat(actualizado.entradaAnterior()).contains(anterior);
    }
}
