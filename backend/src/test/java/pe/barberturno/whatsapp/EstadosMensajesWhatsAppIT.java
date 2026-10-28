package pe.barberturno.whatsapp;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionTemplate;
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
@ExtendWith(OutputCaptureExtension.class)
class EstadosMensajesWhatsAppIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private ReceptorEstadosWhatsApp receptorEstados;

    @Autowired
    private AvisoFalloWhatsApp avisos;

    @Autowired
    private ProcesadorEventosWhatsApp procesadorEventos;

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

    private long insertarMensajeFallido(long negocioId, long conversacionId, MotivoFallo motivo,
            Integer codigoMeta, OptionalLong reservaId) {
        return ejecutor.ejecutar(negocioId, () -> sql.queryForObject("""
                INSERT INTO mensaje_whatsapp(negocio_id, conversacion_id, direccion, origen, tipo,
                    contenido, wamid, estado, enviado_como, intentos, inciertos, proximo_intento, error_codigo, codigo_meta,
                    reserva_id, creado_en, actualizado_en)
                VALUES (?, ?, 'SALIENTE', 'SISTEMA', 'TEXTO', '{"texto":"Prueba"}'::jsonb, null, 'FALLIDO', 'TEXTO',
                    2, 2, null, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, negocioId, conversacionId, motivo.name(), codigoMeta,
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
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.EST_1", "sent", WA_CLIENTE, "bt:m:" + msg + ":1", null));
            return null;
        }));
        var filaSent = filaMensaje(a, msg);
        assertThat(filaSent.get("estado")).isEqualTo("ENVIADO");
        assertThat(filaSent.get("wamid")).isEqualTo("wamid.EST_1");

        // 2. delivered -> ENTREGADO
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.EST_1", "delivered", WA_CLIENTE, null, null));
            return null;
        }));
        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("ENTREGADO");

        // 3. read -> LEIDO
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.EST_1", "read", WA_CLIENTE, null, null));
            return null;
        }));
        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("LEIDO");
    }

    @Test
    void estadosDesordenadosYDuplicados_soloAvanzan_deliveredAntesDeSent() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.DESORDEN", TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega delivered primero -> ENTREGADO
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.DESORDEN", "delivered", WA_CLIENTE, null, null));
            return null;
        }));
        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("ENTREGADO");

        // Llega sent atrasado -> no retrocede, sigue en ENTREGADO
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.DESORDEN", "sent", WA_CLIENTE, null, null));
            return null;
        }));
        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("ENTREGADO");

        // Llega delivered duplicado -> sigue en ENTREGADO
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.DESORDEN", "delivered", WA_CLIENTE, null, null));
            return null;
        }));
        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("ENTREGADO");
    }

    @Test
    void failed_trasDeliveredOLeido_seIgnora() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENTREGADO, "wamid.ENTREGADO_OK", TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega failed después de delivered -> debe ignorarse
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.ENTREGADO_OK", "failed", WA_CLIENTE, null, 131051));
            return null;
        }));

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("ENTREGADO");
        assertThat(fila.get("error_codigo")).isNull();
    }

    @Test
    void failed_desdeEnviado_pasaAFallidoConFalloEntregaYAvisoSiHayReserva() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.FALLARA", TipoMensaje.TEXTO, OptionalLong.of(reserva));

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.FALLARA", "failed", WA_CLIENTE, null, 131051));
            return null;
        }));

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.FALLO_ENTREGA.name());
        assertThat(fila.get("codigo_meta")).isEqualTo(131051);

        // Aviso interno generado
        assertThat(contarNotificaciones(a, titular.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, encargado.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, barberoCuenta.getId())).isEqualTo(1);

        String texto = ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT mensaje FROM notificacion WHERE usuario_id = ? LIMIT 1",
                String.class, titular.getId()));
        assertThat(texto).contains("No se pudo enviar por WhatsApp el mensaje de la reserva BT-" + reserva)
                .contains(MotivoFallo.FALLO_ENTREGA.descripcion());
    }

    @Test
    void conciliacion_sentConBizOpaqueCallbackData_conciliaMensajePendiente() {
        long conv = crearConversacion(a, WA_CLIENTE);
        // Mensaje pendiente sin wamid tras un resultado desconocido
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.PENDIENTE, null, TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega aviso sent de Meta con el dato de conciliación bt:m:<id>:<ficha>
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.CONCILIADO_001", "sent", WA_CLIENTE,
                    "bt:m:" + msg + ":1", null));
            return null;
        }));

        var fila = filaMensaje(a, msg);
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
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.SEGUNDO_DUPLICADO", "sent", WA_CLIENTE,
                    "bt:m:" + msg + ":1", null));
            return null;
        }));

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("wamid"))
                .as("El segundo wamid duplicado no debe sobrescribir el primero")
                .isEqualTo("wamid.PRIMERO");
    }

    @Test
    void aislamiento_avisoDeEstadoEnCanalB_noAfectaMensajeDeA() {
        long convA = crearConversacion(a, WA_CLIENTE);
        long msgA = insertarMensajeSaliente(a, convA, EstadoMensaje.ENVIADO, "wamid.DE_A", TipoMensaje.TEXTO, OptionalLong.empty());

        // Se recibe el aviso en el contexto del negocio B con el wamid de A
        assertThatThrownBy(() -> ejecutor.ejecutar(b, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(b, "wamid.DE_A", "delivered", WA_CLIENTE, null, null));
            return null;
        }))).isInstanceOf(Exception.class);

        // El mensaje en A permanece intacto
        assertThat(filaMensaje(a, msgA).get("estado")).isEqualTo("ENVIADO");
    }

    @Test
    void wamidDesconocido_lanzaExcepcionParaReintento() {
        long conv = crearConversacion(a, WA_CLIENTE);

        // Wamid que no existe en el sistema ni tiene dato de conciliación válido
        assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.INEXISTENTE", "delivered", WA_CLIENTE, null, null));
            return null;
        }))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wamid.INEXISTENTE");
    }

    @Test
    void wamidDesconocido_conReceptorReal_reintentaHastaErrorTerminalEnProcesador() {
        // Evento ESTADO con wamid que no existe en mensaje_whatsapp ni tiene callback data
        String wamidInexistente = "wamid.TOTALMENTE_DESCONOCIDO";
        var cuerpo = mapper.createObjectNode().put("phoneNumberId", NUMERO_A);
        cuerpo.putObject("contacto").put("waId", WA_CLIENTE).put("nombre", "Cliente");
        var dato = cuerpo.putObject("dato").put("id", wamidInexistente);
        dato.put("recipient_id", WA_CLIENTE);
        dato.put("status", "delivered");
        dato.put("timestamp", String.valueOf(reloj.instant().getEpochSecond()));

        String clave = "s:desconocido:e2e";
        sql.update("""
                INSERT INTO evento_whatsapp_entrante(clave_idempotencia, tipo, phone_number_id, cuerpo, recibido_en, proximo_intento)
                VALUES (?, 'ESTADO', ?, cast(? as jsonb), ?, ?)
                """, clave, NUMERO_A, mapper.writeValueAsString(cuerpo),
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));

        long eventoId = sql.queryForObject(
                "SELECT id FROM evento_whatsapp_entrante WHERE clave_idempotencia = ?", Long.class, clave);

        // Se procesa con el receptor real ActualizadorEstadosWhatsApp conectado:
        // cada intento falla por wamid desconocido, se reprograma con PoliticaReintentos y tras 5 intentos termina en ERROR
        for (int intento = 1; intento <= 5; intento++) {
            Instant tiempoFallo = reloj.instant();
            int procesados = procesadorEventos.procesarLote();
            assertThat(procesados).isEqualTo(intento == 5 ? 1 : 0);

            var fila = sql.queryForMap("SELECT * FROM evento_whatsapp_entrante WHERE id = ?", eventoId);
            assertThat(fila.get("intentos")).isEqualTo(intento);

            Duration espera = PoliticaReintentos.espera(intento, Duration.ofSeconds(5), Duration.ofMinutes(5));
            if (intento < 5) {
                assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
                assertThat(fila.get("motivo")).isNull();
                assertThat(((Timestamp) fila.get("proximo_intento")).toInstant()).isEqualTo(tiempoFallo.plus(espera));
                assertThat(procesadorEventos.procesarLote()).as("No debe reclamarse antes del vencimiento").isZero();
                reloj.adelantar(espera);
            } else {
                assertThat(fila.get("estado")).isEqualTo("ERROR");
                assertThat(fila.get("motivo")).isEqualTo("FALLO_PROCESAMIENTO");
                assertThat(((Timestamp) fila.get("procesado_en")).toInstant()).isEqualTo(tiempoFallo);
            }
        }
    }

    @Test
    void estadosDeCanalDesconectadoONegocioSuspendido_seAplican() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.CANAL_DESCONECTADO", TipoMensaje.TEXTO, OptionalLong.empty());

        // Desconectamos el canal y suspendemos el negocio
        ejecutor.ejecutar(a, () -> sql.update("UPDATE canal_whatsapp SET estado = 'DESCONECTADO' WHERE negocio_id = ?", a));
        sql.update("UPDATE negocio SET estado = 'SUSPENDIDO' WHERE id = ?", a);

        // El aviso de estado aún debe aplicarse porque es contabilidad de un mensaje ya salido
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.CANAL_DESCONECTADO", "delivered", WA_CLIENTE, null, null));
            return null;
        }));

        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("ENTREGADO");
    }

    @Test
    void failed_desdePendiente_fijaFalloEntregaYCodigoMeta() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.PENDIENTE, null, TipoMensaje.TEXTO, OptionalLong.empty());

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.FALLO_DESDE_PEND", "failed", WA_CLIENTE,
                    "bt:m:" + msg + ":1", 131051));
            return null;
        }));

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.FALLO_ENTREGA.name());
        assertThat(fila.get("codigo_meta")).isEqualTo(131051);
        assertThat(fila.get("wamid")).isEqualTo("wamid.FALLO_DESDE_PEND");
    }

    @Test
    void failed_desdeResultadoDesconocido_fijaFalloEntregaYCodigoMeta() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeFallido(a, conv, MotivoFallo.RESULTADO_DESCONOCIDO, null, OptionalLong.empty());

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.FALLO_POST_DESCONOCIDO", "failed", WA_CLIENTE,
                    "bt:m:" + msg + ":1", 131047));
            return null;
        }));

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.FALLO_ENTREGA.name());
        assertThat(fila.get("codigo_meta")).isEqualTo(131047);
        assertThat(fila.get("wamid")).isEqualTo("wamid.FALLO_POST_DESCONOCIDO");
    }

    @Test
    void estadoPositivo_desdeResultadoDesconocido_vaciaErrorCodigoYCodigoMeta() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeFallido(a, conv, MotivoFallo.RESULTADO_DESCONOCIDO, 131000, OptionalLong.empty());

        var filaAntes = filaMensaje(a, msg);
        assertThat(filaAntes.get("estado")).isEqualTo("FALLIDO");
        assertThat(filaAntes.get("error_codigo")).isEqualTo(MotivoFallo.RESULTADO_DESCONOCIDO.name());
        assertThat(filaAntes.get("codigo_meta")).isEqualTo(131000);

        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.RECUPERADO_OK", "delivered", WA_CLIENTE,
                    "bt:m:" + msg + ":1", null));
            return null;
        }));

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("ENTREGADO");
        assertThat(fila.get("wamid")).isEqualTo("wamid.RECUPERADO_OK");
        assertThat(fila.get("error_codigo")).as("error_codigo debe quedar limpio").isNull();
        assertThat(fila.get("codigo_meta")).as("codigo_meta no nulo previo debe quedar limpio").isNull();
    }

    @Test
    void aislamiento_datoDeConciliacionDeNegocioA_recibidoEnCanalB_noConcilia() {
        long convA = crearConversacion(a, WA_CLIENTE);
        long msgA = insertarMensajeSaliente(a, convA, EstadoMensaje.PENDIENTE, null, TipoMensaje.TEXTO, OptionalLong.empty());

        // Se recibe el aviso en el contexto de negocio B con el dato de conciliación de msgA
        assertThatThrownBy(() -> ejecutor.ejecutar(b, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(b, "wamid.DE_B_PARA_A", "sent", WA_CLIENTE,
                    "bt:m:" + msgA + ":1", null));
            return null;
        }))).isInstanceOf(IllegalStateException.class);

        // El mensaje en negocio A no se concilia y permanece pendiente sin wamid
        var filaA = filaMensaje(a, msgA);
        assertThat(filaA.get("estado")).isEqualTo("PENDIENTE");
        assertThat(filaA.get("wamid")).isNull();
    }

    @Test
    void avisoInterno_unAvisoPorTransicion_resultadoDesconocidoAFailedGeneraSegundoAvisoYFailedRepetidoNo() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.PENDIENTE, null, TipoMensaje.TEXTO, OptionalLong.of(reserva));

        // Primer aviso: simulamos el aviso de RESULTADO_DESCONOCIDO cuando el mensaje estaba pendiente
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            MensajeWhatsApp msgObj = entityManager.find(MensajeWhatsApp.class, msg);
            avisos.avisar(msgObj, MotivoFallo.RESULTADO_DESCONOCIDO);
            return null;
        }));

        // El mensaje pasa a FALLIDO con RESULTADO_DESCONOCIDO
        ejecutor.ejecutar(a, () -> sql.update(
                "UPDATE mensaje_whatsapp SET estado = 'FALLIDO', error_codigo = 'RESULTADO_DESCONOCIDO' WHERE id = ?", msg));

        int notifsDespuesPrimerAviso = contarNotificaciones(a, titular.getId());
        assertThat(notifsDespuesPrimerAviso).isEqualTo(1);

        // Llega aviso 'failed' -> transición a FALLO_ENTREGA -> genera SEGUNDO aviso
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.FALLO_TRANSICION", "failed", WA_CLIENTE,
                    "bt:m:" + msg + ":1", 131051));
            return null;
        }));

        int notifsDespuesSegundoAviso = contarNotificaciones(a, titular.getId());
        assertThat(notifsDespuesSegundoAviso).as("La transición a FALLO_ENTREGA debe generar un segundo aviso").isEqualTo(2);

        // Llega otro aviso 'failed' repetido con el mismo wamid o ficha
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.FALLO_TRANSICION", "failed", WA_CLIENTE, null, 131051));
            return null;
        }));

        int notifsDespuesTercerAviso = contarNotificaciones(a, titular.getId());
        assertThat(notifsDespuesTercerAviso).as("Un failed repetido sin cambio de motivo no debe generar otro aviso").isEqualTo(2);
    }

    private static class ExcepcionControladaParaRollback extends RuntimeException {
        ExcepcionControladaParaRollback(String mensaje) {
            super(mensaje);
        }
    }

    @Test
    void avisoInterno_transaccionRevertida_noGeneraAvisos() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.REVERTIR", TipoMensaje.TEXTO, OptionalLong.of(reserva));

        assertThatThrownBy(() -> ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.REVERTIR", "failed", WA_CLIENTE, null, 131051));

            // Verificamos dentro de la transacción que el cambio a FALLIDO y la notificación sí ocurrieron
            var filaEnTx = sql.queryForMap("SELECT estado, error_codigo FROM mensaje_whatsapp WHERE id = ?", msg);
            assertThat(filaEnTx.get("estado")).isEqualTo("FALLIDO");
            assertThat(filaEnTx.get("error_codigo")).isEqualTo(MotivoFallo.FALLO_ENTREGA.name());
            assertThat(contarNotificaciones(a, titular.getId())).isEqualTo(1);

            // Provocamos la excepción específica identificable para revertir la transacción
            throw new ExcepcionControladaParaRollback("Fallo forzado para revertir la transacción");
        }))).isInstanceOf(ExcepcionControladaParaRollback.class)
                .hasMessage("Fallo forzado para revertir la transacción");

        // Fuera de la transacción, comprobamos que el rollback deshizo tanto el aviso como el estado
        assertThat(contarNotificaciones(a, titular.getId())).isZero();
        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("ENVIADO");
        assertThat(filaMensaje(a, msg).get("error_codigo")).isNull();
    }

    @Test
    void avisoInterno_textosExactosSegunMotivo() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msgDesconocido = insertarMensajeSaliente(a, conv, EstadoMensaje.PENDIENTE, null, TipoMensaje.TEXTO, OptionalLong.of(reserva));

        // 1. Texto exacto para RESULTADO_DESCONOCIDO
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            MensajeWhatsApp msgObj = entityManager.find(MensajeWhatsApp.class, msgDesconocido);
            avisos.avisar(msgObj, MotivoFallo.RESULTADO_DESCONOCIDO);
            return null;
        }));

        String textoDesconocido = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT mensaje FROM notificacion WHERE usuario_id = ? ORDER BY id DESC LIMIT 1",
                String.class, titular.getId()));
        assertThat(textoDesconocido).isEqualTo("No se pudo confirmar si se envió por WhatsApp el mensaje de la reserva BT-"
                + reserva + ". Compruébelo con el cliente.");

        // 2. Texto exacto para otro motivo (ej. DESTINO_NO_VALIDO)
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            MensajeWhatsApp msgObj = entityManager.find(MensajeWhatsApp.class, msgDesconocido);
            avisos.avisar(msgObj, MotivoFallo.DESTINO_NO_VALIDO);
            return null;
        }));

        String textoDestino = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT mensaje FROM notificacion WHERE usuario_id = ? ORDER BY id DESC LIMIT 1",
                String.class, titular.getId()));
        assertThat(textoDestino).isEqualTo("No se pudo enviar por WhatsApp el mensaje de la reserva BT-"
                + reserva + ": " + MotivoFallo.DESTINO_NO_VALIDO.descripcion() + ". Contacte al cliente por otro medio.");
    }

    @Test
    void registros_enAvisoDuplicado_noContienenDatosPersonalesNiToken(CapturedOutput salida) {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = insertarMensajeSaliente(a, conv, EstadoMensaje.ENVIADO, "wamid.ORIGINAL", TipoMensaje.TEXTO, OptionalLong.empty());

        // Llega aviso duplicado con un wamid distinto para el mismo mensaje por reconciliación
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(estadoEntrante(a, "wamid.SEGUNDO_DISTINTO", "sent", WA_CLIENTE,
                    "bt:m:" + msg + ":1", null));
            return null;
        }));

        String registros = salida.getAll();
        assertThat(registros).contains("segundo identificador")
                .doesNotContain(WA_CLIENTE)
                .doesNotContain("token-ficticio-prueba");
    }
}
