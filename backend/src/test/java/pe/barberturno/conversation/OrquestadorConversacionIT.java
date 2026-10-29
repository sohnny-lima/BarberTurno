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

    @Test
    void conversacionInactiva_vaciaElEstadoAlRecibir() {
        long conversacion = crearConversacionConVentana(a, WA_CLIENTE, reloj.instant());
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx -> sql.update(
                "UPDATE conversacion SET estado = '{\"paso\":\"FRANJA\"}'::jsonb WHERE id = ?", conversacion)));
        reloj.adelantar(Duration.ofHours(24));
        recibir(texto(a, WA_CLIENTE, "hola"));
        String estado = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT CAST(estado AS text) FROM conversacion WHERE id = ?", String.class, conversacion));
        assertThat(estado).doesNotContain("FRANJA");
    }
}
