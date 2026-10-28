package pe.barberturno.whatsapp;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración de {@code RegistroEntradaWhatsApp} (12-v2-09-whatsapp-salida.md §4.4, CI-8).
 * Valida que cada mensaje entrante recibido registra o actualiza la ventana de atención en {@code conversacion}
 * con {@code GREATEST(actual, LEAST(enviadoEn, ahora))}, sin retroceder ante mensajes desordenados, sin
 * adelantarse al reloj del servidor y sin crear fichas de cliente ni almacenar el contenido (V2-10).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class RegistroEntradaWhatsAppIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private ReceptorMensajesWhatsApp receptorMensajes;

    private MensajeEntrante mensaje(long negocioId, String waId, Instant enviadoEn) {
        ObjectNode dato = mapper.createObjectNode();
        dato.put("from", waId);
        dato.putObject("text").put("body", "Texto entrante");
        return new MensajeEntrante(1L, negocioId, waId, OptionalLong.empty(), "Cliente Perfil",
                "wamid.ENTRANTE_001", "text", enviadoEn, dato);
    }

    @Test
    void recibir_mensajeEntrante_creaConversacionSiNoExisteConUltimaEntradaEn() {
        Instant enviado = reloj.instant().minus(Duration.ofMinutes(10));

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorMensajes.recibir(mensaje(a, WA_CLIENTE, enviado));
            return null;
        }));

        var fila = ejecutor.ejecutar(a, () ->
                sql.queryForMap("SELECT * FROM conversacion WHERE negocio_id = ? AND wa_id = ?", a, WA_CLIENTE));
        assertThat(fila.get("wa_id")).isEqualTo(WA_CLIENTE);
        assertThat(((Timestamp) fila.get("ultima_entrada_en")).toInstant()).isEqualTo(enviado);

        // No crea fichas de cliente ni almacena contenido
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT count(*) FROM cliente WHERE negocio_id = ?", Integer.class, a))).isZero();
    }

    @Test
    void recibir_mensajeConTimestampMasReciente_actualizaUltimaEntradaEn() {
        Instant t1 = reloj.instant().minus(Duration.ofHours(2));
        Instant t2 = reloj.instant().minus(Duration.ofMinutes(15));
        crearConversacionConVentana(a, WA_CLIENTE, t1);

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorMensajes.recibir(mensaje(a, WA_CLIENTE, t2));
            return null;
        }));

        var fila = ejecutor.ejecutar(a, () ->
                sql.queryForMap("SELECT ultima_entrada_en FROM conversacion WHERE negocio_id = ? AND wa_id = ?", a, WA_CLIENTE));
        assertThat(((Timestamp) fila.get("ultima_entrada_en")).toInstant()).isEqualTo(t2);
    }

    @Test
    void recibir_mensajeAtrasado_noRetrocedeUltimaEntradaEn() {
        Instant tReciente = reloj.instant().minus(Duration.ofMinutes(10));
        Instant tAtrasado = reloj.instant().minus(Duration.ofHours(5));
        crearConversacionConVentana(a, WA_CLIENTE, tReciente);

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorMensajes.recibir(mensaje(a, WA_CLIENTE, tAtrasado));
            return null;
        }));

        var fila = ejecutor.ejecutar(a, () ->
                sql.queryForMap("SELECT ultima_entrada_en FROM conversacion WHERE negocio_id = ? AND wa_id = ?", a, WA_CLIENTE));
        assertThat(((Timestamp) fila.get("ultima_entrada_en")).toInstant())
                .as("La ventana no debe retroceder")
                .isEqualTo(tReciente);
    }

    @Test
    void recibir_mensajeConTimestampEnElFuturo_seAcotaAlRelojSinAdelantarse() {
        Instant ahora = reloj.instant();
        Instant futuro = ahora.plus(Duration.ofHours(2)); // Dispositivo del cliente con reloj adelantado
        crearConversacionConVentana(a, WA_CLIENTE, ahora.minus(Duration.ofHours(1)));

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorMensajes.recibir(mensaje(a, WA_CLIENTE, futuro));
            return null;
        }));

        var fila = ejecutor.ejecutar(a, () ->
                sql.queryForMap("SELECT ultima_entrada_en FROM conversacion WHERE negocio_id = ? AND wa_id = ?", a, WA_CLIENTE));
        assertThat(((Timestamp) fila.get("ultima_entrada_en")).toInstant())
                .as("La ventana debe acotarse a ahora y no adelantarse al futuro")
                .isEqualTo(ahora);
    }

    @Test
    void aislamiento_mismoWaIdEnDosNegocios_actualizaSoloElNegocioReceptor() {
        Instant tA = reloj.instant().minus(Duration.ofHours(3));
        Instant tB = reloj.instant().minus(Duration.ofHours(10));
        crearConversacionConVentana(a, WA_CLIENTE, tA);
        crearConversacionConVentana(b, WA_CLIENTE, tB);

        Instant nuevoA = reloj.instant().minus(Duration.ofMinutes(5));
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorMensajes.recibir(mensaje(a, WA_CLIENTE, nuevoA));
            return null;
        }));

        var filaA = ejecutor.ejecutar(a, () ->
                sql.queryForMap("SELECT ultima_entrada_en FROM conversacion WHERE negocio_id = ? AND wa_id = ?", a, WA_CLIENTE));
        var filaB = ejecutor.ejecutar(b, () ->
                sql.queryForMap("SELECT ultima_entrada_en FROM conversacion WHERE negocio_id = ? AND wa_id = ?", b, WA_CLIENTE));

        assertThat(((Timestamp) filaA.get("ultima_entrada_en")).toInstant()).isEqualTo(nuevoA);
        assertThat(((Timestamp) filaB.get("ultima_entrada_en")).toInstant()).isEqualTo(tB);
    }
}
