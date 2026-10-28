package pe.barberturno.whatsapp;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas de integración del receptor de estados de WhatsApp {@link ReceptorEstadosWhatsApp}
 * (12-v2-09-whatsapp-salida.md §4.4, CI-3, CI-5 y CI-7).
 * Valida la progresión monótona de estados ({@code sent} < {@code delivered} < {@code read}),
 * la conciliación por {@code biz_opaque_callback_data}, la protección ante segundos {@code wamid},
 * el rechazo de {@code failed} tardío tras entrega, y el aislamiento estricto entre negocios.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class EstadosMensajesWhatsAppIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private ReceptorEstadosWhatsApp receptorEstados;

    private long insertarMensajeSaliente(long negocioId, long conversacionId, EstadoMensaje estado,
            String wamid, TipoMensaje enviadoComo, OptionalLong reservaId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject("""
                INSERT INTO mensaje_whatsapp(negocio_id, conversacion_id, direccion, origen, tipo,
                    contenido, wamid, estado, enviado_como, intentos, inciertos, proximo_intento, reserva_id,
                    creado_en, actualizado_en)
                VALUES (?, ?, 'SALIENTE', 'SISTEMA', 'TEXTO', '{"texto":"Prueba"}'::jsonb, ?, ?, ?,
                    1, 0, ?, ?, ?, ?) RETURNING id
                """, Long.class, negocioId, conversacionId, wamid, estado.name(),
                enviadoComo == null ? null : enviadoComo.name(),
                estado == EstadoMensaje.PENDIENTE ? Timestamp.from(reloj.instant()) : null,
                reservaId.isPresent() ? reservaId.getAsLong() : null,
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));
    }

    private EstadoEntrante estadoEntrante(long negocioId, String wamid, String estado, String waId,
            String callbackData, Integer codigoError) {
        ObjectNode dato = mapper.createObjectNode();
        dato.put("id", wamid);
        dato.put("status", estado);
        dato.put("recipient_id", waId);
        dato.put("timestamp", String.valueOf(reloj.instant().getEpochSecond()));
        if (callbackData != null) {
            dato.put("biz_opaque_callback_data", callbackData);
        }
        if (codigoError != null) {
            ArrayNode errors = dato.putArray("errors");
            ObjectNode err = errors.addObject();
            err.put("code", codigoError);
            err.put("title", "Error de entrega");
        }
        return new EstadoEntrante(1L, negocioId, wamid, estado, waId, reloj.instant(), dato);
    }

    @Test
    void progresoDeEstados_sent_delivered_read_avanzaEnOrden() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.PENDIENTE, null, TipoMensaje.TEXTO, OptionalLong.empty());

        // 1. sent -> ENVIADO (con conciliación o directo)
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.EST_1", "sent", WA_CLIENTE, "bt:m:" + msg + ":1", null));
            return null;
        });
        var filaSent = filaMensaje(msg);
        assertThat(filaSent.get("estado")).isEqualTo("ENVIADO");
        assertThat(filaSent.get("wamid")).isEqualTo("wamid.EST_1");

        // 2. delivered -> ENTREGADO
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.EST_1", "delivered", WA_CLIENTE, null, null));
            return null;
        });
        assertThat(filaMensaje(msg).get("estado")).isEqualTo("ENTREGADO");

        // 3. read -> LEIDO
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.EST_1", "read", WA_CLIENTE, null, null));
            return null;
        });
        assertThat(filaMensaje(msg).get("estado")).isEqualTo("LEIDO");
    }

    @Test
    void estadosDesordenadosYDuplicados_soloAvanzan_deliveredAntesDeSent() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.DESORDEN", TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega delivered primero -> ENTREGADO
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.DESORDEN", "delivered", WA_CLIENTE, null, null));
            return null;
        });
        assertThat(filaMensaje(msg).get("estado")).isEqualTo("ENTREGADO");

        // Llega sent atrasado -> no retrocede, sigue en ENTREGADO
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.DESORDEN", "sent", WA_CLIENTE, null, null));
            return null;
        });
        assertThat(filaMensaje(msg).get("estado")).isEqualTo("ENTREGADO");

        // Llega delivered duplicado -> sigue en ENTREGADO
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.DESORDEN", "delivered", WA_CLIENTE, null, null));
            return null;
        });
        assertThat(filaMensaje(msg).get("estado")).isEqualTo("ENTREGADO");
    }

    @Test
    void failed_trasDeliveredOLeido_seIgnora() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENTREGADO, "wamid.ENTREGADO_OK", TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega failed después de delivered -> debe ignorarse
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.ENTREGADO_OK", "failed", WA_CLIENTE, null, 131051));
            return null;
        });

        var fila = filaMensaje(msg);
        assertThat(fila.get("estado")).isEqualTo("ENTREGADO");
        assertThat(fila.get("error_codigo")).isNull();
    }

    @Test
    void failed_desdeEnviado_pasaAFallidoConFalloEntregaYAvisoSiHayReserva() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.FALLARA", TipoMensaje.TEXTO, OptionalLong.of(reserva));

        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.FALLARA", "failed", WA_CLIENTE, null, 131051));
            return null;
        });

        var fila = filaMensaje(msg);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.FALLO_ENTREGA.name());
        assertThat(fila.get("codigo_meta")).isEqualTo(131051);

        // Aviso interno generado
        assertThat(contarNotificaciones(a, titular.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, encargado.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, barberoCuenta.getId())).isEqualTo(1);

        String texto = sql.queryForObject("SELECT mensaje FROM notificacion WHERE usuario_id = ? LIMIT 1",
                String.class, titular.getId());
        assertThat(texto).contains("No se pudo enviar por WhatsApp el mensaje de la reserva BT-" + reserva)
                .contains(MotivoFallo.FALLO_ENTREGA.descripcion());
    }

    @Test
    void conciliacion_sentConBizOpaqueCallbackData_conciliaMensajePendiente() {
        long conv = crearConversacion(a, WA_CLIENTE);
        // Mensaje pendiente sin wamid tras un resultado desconocido
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.PENDIENTE, null, TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega aviso sent de Meta con el dato de conciliación bt:m:<id>:<ficha>
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.CONCILIADO_001", "sent", WA_CLIENTE,
                    "bt:m:" + msg + ":1", null));
            return null;
        });

        var fila = filaMensaje(msg);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("wamid")).isEqualTo("wamid.CONCILIADO_001");
        assertThat(fila.get("proximo_intento")).isNull();
    }

    @Test
    void conciliacion_segundoWamidDelMismoMensaje_noSobrescribeElPrimero() {
        long conv = crearConversacion(a, WA_CLIENTE);
        // El mensaje ya tiene un wamid asignado
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.PRIMERO", TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega otro aviso con un segundo wamid (por un reintento duplicado)
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.SEGUNDO_DUPLICADO", "sent", WA_CLIENTE,
                    "bt:m:" + msg + ":1", null));
            return null;
        });

        var fila = filaMensaje(msg);
        assertThat(fila.get("wamid"))
                .as("El segundo wamid duplicado no debe sobrescribir el primero")
                .isEqualTo("wamid.PRIMERO");
    }

    @Test
    void aislamiento_avisoDeEstadoEnCanalB_noAfectaMensajeDeA() {
        long convA = crearConversacion(a, WA_CLIENTE);
        long msgA = insertarMensajeSaliente(a, convA, EstadoMensaje.ENVIADO, "wamid.DE_A", TipoMensaje.TEXTO, OptionalLong.empty());

        // Se recibe el aviso en el contexto del negocio B con el wamid de A
        assertThatThrownBy(() -> ejecutor.ejecutar(b, () -> {
            receptorEstados.recibir(estadoEntrante(b, "wamid.DE_A", "delivered", WA_CLIENTE, null, null));
            return null;
        })).isInstanceOf(Exception.class);

        // El mensaje en A permanece intacto
        assertThat(filaMensaje(msgA).get("estado")).isEqualTo("ENVIADO");
    }

    @Test
    void wamidDesconocido_lanzaExcepcionParaReintento() {
        long conv = crearConversacion(a, WA_CLIENTE);

        // Wamid que no existe en el sistema ni tiene dato de conciliación válido
        assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.INEXISTENTE", "delivered", WA_CLIENTE, null, null));
            return null;
        })).isInstanceOf(Exception.class);
    }

    @Test
    void estadosDeCanalDesconectadoONegocioSuspendido_seAplican() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.CANAL_DESCONECTADO", TipoMensaje.TEXTO, OptionalLong.empty());

        // Desconectamos el canal y suspendemos el negocio
        ejecutor.ejecutar(a, () -> sql.update("UPDATE canal_whatsapp SET estado = 'DESCONECTADO' WHERE negocio_id = ?", a));
        sql.update("UPDATE negocio SET estado = 'SUSPENDIDO' WHERE id = ?", a);

        // El aviso de estado aún debe aplicarse porque es contabilidad de un mensaje ya salido
        ejecutor.ejecutar(a, () -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.CANAL_DESCONECTADO", "delivered", WA_CLIENTE, null, null));
            return null;
        });

        assertThat(filaMensaje(msg).get("estado")).isEqualTo("ENTREGADO");
    }
}
