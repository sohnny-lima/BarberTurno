package pe.barberturno.whatsapp;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.MotivoRevocacion;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración del enviador periódico de WhatsApp {@link EnviadorSalidaWhatsApp}
 * (12-v2-09-whatsapp-salida.md §4.3, CI-2, CI-3, CI-4, CI-6, CI-7, CI-8 y CI-9).
 * Valida validaciones previas al envío, aislamiento multinegocio de canales, concurrencia y orden por
 * conversación, reintentos con retroceso exponencial, resultado desconocido y avisos internos al personal.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@ExtendWith(OutputCaptureExtension.class)
class EnviadorMensajesWhatsAppIT extends WhatsAppSalidaPruebaBase {

    @Autowired
    private EnviadorSalidaWhatsApp enviador;

    @Autowired
    private SalidaWhatsApp salida;

    @Autowired
    private ColaSalidaWhatsApp cola;

    @Autowired
    private ReceptorEstadosWhatsApp receptorEstados;

    @Autowired
    private AvisoFalloWhatsApp avisos;

    @Autowired
    private SalidaWhatsAppPropiedades opciones;

    private long encolarTextoDirecto(long negocioId, long conversacionId, String texto, OptionalLong reservaId) {
        return ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarTexto(conversacionId, texto, OrigenMensaje.SISTEMA, reservaId)));
    }

    private long encolarAvisoDirecto(long negocioId, long conversacionId, PlantillaUtility plantilla,
            Map<String, String> parametros, OptionalLong reservaId) {
        return ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarAviso(conversacionId, plantilla, parametros, reservaId)));
    }

    private long encolarPlantillaDirecto(long negocioId, long conversacionId, PlantillaUtility plantilla,
            Map<String, String> parametros, OptionalLong reservaId) {
        return ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarPlantilla(conversacionId, plantilla, parametros, reservaId)));
    }

    @Test
    void enviarLote_conExito_actualizaEstadoAEnviadoYGuardaWamidYEnviadoComo() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long mensajeId = encolarTextoDirecto(a, conversacionId, "Turno confirmado", OptionalLong.empty());

        servidorGraph.encolarExito("wamid.EXITO_100");

        int reclamados = enviador.enviarLote();
        assertThat(reclamados).isEqualTo(1);

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("wamid")).isEqualTo("wamid.EXITO_100");
        assertThat(fila.get("enviado_como")).isEqualTo("TEXTO");
        assertThat(fila.get("intentos")).isEqualTo(1);
        assertThat(fila.get("inciertos")).isEqualTo(0);
        assertThat(fila.get("error_codigo")).isNull();

        var peticion = servidorGraph.ultimaPeticion();
        assertThat(peticion.ruta()).isEqualTo("/v26.0/" + NUMERO_A + "/messages");
        assertThat(peticion.cuerpoJson().get("messaging_product").asString()).isEqualTo("whatsapp");
        assertThat(peticion.cuerpoJson().get("recipient_type").asString()).isEqualTo("individual");
        assertThat(peticion.cuerpoJson().get("to").asString()).isEqualTo(WA_CLIENTE);
        assertThat(peticion.cuerpoJson().get("biz_opaque_callback_data").asString())
                .isEqualTo("bt:m:" + mensajeId + ":1");
        assertThat(peticion.cuerpoJson().get("type").asString()).isEqualTo("text");

        var textNode = peticion.cuerpoJson().get("text");
        assertThat(textNode.get("body").asString()).isEqualTo("Turno confirmado");
        assertThat(textNode.get("preview_url").asBoolean()).as("preview_url debe ser false por CI-8").isFalse();
    }

    // --- CI-2: Validaciones previas al envío sin peticiones a Graph ---

    @Test
    void canalDesconectado_fallaAntesDelEnvioSinPeticionesAGraph() {
        ejecutor.ejecutar(a, () -> sql.update("UPDATE canal_whatsapp SET estado = 'DESCONECTADO' WHERE negocio_id = ?", a));
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long mensajeId = encolarTextoDirecto(a, conversacionId, "Texto", OptionalLong.empty());

        enviador.enviarLote();

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.CANAL_DESCONECTADO.name());
        assertThat(servidorGraph.peticiones()).isEmpty();
    }

    @Test
    void negocioSuspendido_fallaAntesDelEnvioSinPeticionesAGraph() {
        sql.update("UPDATE negocio SET estado = 'SUSPENDIDO' WHERE id = ?", a);
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long mensajeId = encolarTextoDirecto(a, conversacionId, "Texto", OptionalLong.empty());

        enviador.enviarLote();

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.NEGOCIO_NO_OPERATIVO.name());
        assertThat(servidorGraph.peticiones()).isEmpty();
    }

    @Test
    void negocioSinPropietarioActivo_fallaAntesDelEnvioSinPeticionesAGraph() {
        sql.update("""
                UPDATE membresia SET estado = 'REVOCADA', motivo_revocacion = 'PLATAFORMA', actualizado_en = ?
                WHERE negocio_id = ? AND rol = 'PROPIETARIO'
                """, Timestamp.from(reloj.instant()), a);
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long mensajeId = encolarTextoDirecto(a, conversacionId, "Texto", OptionalLong.empty());

        enviador.enviarLote();

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.NEGOCIO_NO_OPERATIVO.name());
        assertThat(servidorGraph.peticiones()).isEmpty();
    }

    @Test
    void mensajeCaducado_fallaAntesDelEnvioSinPeticionesAGraph() {
        long conversacionId = crearConversacion(a, WA_CLIENTE);
        long mensajeId = encolarTextoDirecto(a, conversacionId, "Texto", OptionalLong.empty());

        // Antigüedad máxima configurada en 6 horas; adelantamos el reloj 6 horas y 1 segundo
        reloj.adelantar(Duration.ofHours(6).plusSeconds(1));

        enviador.enviarLote();

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.MENSAJE_CADUCADO.name());
        assertThat(servidorGraph.peticiones()).isEmpty();
    }

    @Test
    void ventanaCerrada_paraTextoEInteractivo_fallaAntesDelEnvioSinPeticionesAGraph() {
        // Conversación con última entrada hace 25 horas (ventana de 24h cerrada)
        long conversacionId = crearConversacionConVentana(a, WA_CLIENTE, reloj.instant().minus(Duration.ofHours(25)));
        long mensajeTexto = encolarTextoDirecto(a, conversacionId, "Texto fuera de ventana", OptionalLong.empty());

        ObjectNode interactivo = mapper.createObjectNode();
        interactivo.put("type", "button");
        interactivo.putObject("body").put("text", "¿Opciones?");
        long mensajeInteractivo = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarInteractivo(conversacionId, interactivo, OrigenMensaje.ASISTENTE, OptionalLong.empty())));

        enviador.enviarLote();

        var filaTexto = filaMensaje(a, mensajeTexto);
        assertThat(filaTexto.get("estado")).isEqualTo("FALLIDO");
        assertThat(filaTexto.get("error_codigo")).isEqualTo(MotivoFallo.VENTANA_CERRADA.name());

        var filaInteractivo = filaMensaje(a, mensajeInteractivo);
        assertThat(filaInteractivo.get("estado")).isEqualTo("FALLIDO");
        assertThat(filaInteractivo.get("error_codigo")).isEqualTo(MotivoFallo.VENTANA_CERRADA.name());

        assertThat(servidorGraph.peticiones()).as("Ningún mensaje debe enviarse a Graph con ventana cerrada").isEmpty();
    }

    @Test
    void ventanaAbiertaAlEncolar_cerradaAntesDelEnvio_avisoSeEnviaComoPlantilla() {
        // 1. Encolamos el aviso cuando la ventana está a punto de cerrarse (aún abierta: 23h 54m < 23h 55m)
        Instant ahora = reloj.instant();
        long conversacionId = crearConversacionConVentana(a, WA_CLIENTE, ahora.minus(Duration.ofHours(23).plusMinutes(54)));
        Map<String, String> params = Map.of(
                "negocio", "BarberTurno A",
                "fecha", "Mañana 15:00",
                "profesional", "Barbero A",
                "codigo", "BT-999");
        long mensajeId = encolarAvisoDirecto(a, conversacionId, PlantillaUtility.BT_RECORDATORIO_CITA, params, OptionalLong.empty());

        // 2. La ventana se cierra antes del envío (adelantamos solo 2 minutos: la ventana pasa a 23h 56m y se cierra,
        // sin que el mensaje caduque pues su antigüedad es de solo 2 minutos << 6 horas)
        reloj.adelantar(Duration.ofMinutes(2));

        servidorGraph.encolarExito("wamid.AVISO_CONVERTIDO_PLANTILLA");
        enviador.enviarLote();

        // 3. Acredita que la ventana se evalúa en la preparación del envío: sale como PLANTILLA y no como TEXTO
        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("enviado_como")).isEqualTo("PLANTILLA");

        var peticion = servidorGraph.ultimaPeticion();
        assertThat(peticion.cuerpoJson().get("type").asString()).isEqualTo("template");
        assertThat(peticion.cuerpoJson().get("template").get("name").asString()).isEqualTo("bt_recordatorio_cita");
    }

    // --- CI-8: Límites de ventana, selección de plantillas y formato ---

    @Test
    void ventanaAtencion_limiteExactoConMargenDeCincoMinutos() {
        // Ventana 24h, margen 5m -> abierta hasta 23h 55m
        Instant ahora = reloj.instant();

        // 1. A 23h 54m (dentro del límite permitido) -> debe enviarse
        long convAbierta = crearConversacionConVentana(a, WA_CLIENTE, ahora.minus(Duration.ofHours(23).plusMinutes(54)));
        long msgAbierto = encolarTextoDirecto(a, convAbierta, "Dentro de ventana", OptionalLong.empty());
        servidorGraph.encolarExito("wamid.VENTANA_ABIERTA");
        enviador.enviarLote();
        assertThat(filaMensaje(a, msgAbierto).get("estado")).isEqualTo("ENVIADO");

        // 2. A 23h 55m exactos (alcanzado el margen) -> debe cerrarse con VENTANA_CERRADA
        long convCerrada = crearConversacionConVentana(a, "51999000002", ahora.minus(Duration.ofHours(23).plusMinutes(55)));
        long msgCerrado = encolarTextoDirecto(a, convCerrada, "Fuera de ventana", OptionalLong.empty());
        enviador.enviarLote();
        assertThat(filaMensaje(a, msgCerrado).get("estado")).isEqualTo("FALLIDO");
        assertThat(filaMensaje(a, msgCerrado).get("error_codigo")).isEqualTo(MotivoFallo.VENTANA_CERRADA.name());
    }

    @Test
    void encolarAviso_conVentanaAbierta_seEnviaComoTexto() {
        long conversacionId = crearConversacion(a, WA_CLIENTE); // ventana abierta
        Map<String, String> params = Map.of(
                "negocio", "BarberTurno A",
                "fecha", "Hoy 16:00",
                "profesional", "Barbero A",
                "codigo", "BT-200");
        long mensajeId = encolarAvisoDirecto(a, conversacionId, PlantillaUtility.BT_RECORDATORIO_CITA, params, OptionalLong.empty());

        servidorGraph.encolarExito("wamid.AVISO_TEXTO");
        enviador.enviarLote();

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("enviado_como")).isEqualTo("TEXTO");

        var peticion = servidorGraph.ultimaPeticion();
        assertThat(peticion.cuerpoJson().get("type").asString()).isEqualTo("text");
        assertThat(peticion.cuerpoJson().get("text").get("body").asString())
                .contains("Te recordamos tu cita en BarberTurno A el Hoy 16:00 con Barbero A (reserva BT-200).");
    }

    @Test
    void encolarAviso_conVentanaCerrada_seEnviaComoPlantilla() {
        // Ventana cerrada
        long conversacionId = crearConversacionConVentana(a, WA_CLIENTE, reloj.instant().minus(Duration.ofHours(30)));
        Map<String, String> params = Map.of(
                "negocio", "BarberTurno A",
                "fecha", "Hoy 16:00",
                "profesional", "Barbero A",
                "codigo", "BT-201");
        long mensajeId = encolarAvisoDirecto(a, conversacionId, PlantillaUtility.BT_RECORDATORIO_CITA, params, OptionalLong.empty());

        servidorGraph.encolarExito("wamid.AVISO_PLANTILLA");
        enviador.enviarLote();

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("enviado_como")).isEqualTo("PLANTILLA");

        var peticion = servidorGraph.ultimaPeticion();
        var cuerpo = peticion.cuerpoJson();
        assertThat(cuerpo.get("messaging_product").asString()).isEqualTo("whatsapp");
        assertThat(cuerpo.get("recipient_type").asString()).isEqualTo("individual");
        assertThat(cuerpo.get("to").asString()).isEqualTo(WA_CLIENTE);
        assertThat(cuerpo.get("type").asString()).isEqualTo("template");
        assertThat(cuerpo.get("biz_opaque_callback_data").asString()).isEqualTo("bt:m:" + mensajeId + ":1");

        var template = cuerpo.get("template");
        assertThat(template.get("name").asString()).isEqualTo("bt_recordatorio_cita");
        assertThat(template.get("language").get("code").asString()).isEqualTo("es");

        var components = template.get("components");
        assertThat(components.isArray()).isTrue();
        assertThat(components).hasSize(1);
        var bodyComponent = components.get(0);
        assertThat(bodyComponent.get("type").asString()).isEqualTo("body");

        var parameters = bodyComponent.get("parameters");
        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters).hasSize(4);

        // Parámetros en orden estricto de PlantillaUtility: negocio, fecha, profesional, codigo
        assertThat(parameters.get(0).get("type").asString()).isEqualTo("text");
        assertThat(parameters.get(0).get("parameter_name").asString()).isEqualTo("negocio");
        assertThat(parameters.get(0).get("text").asString()).isEqualTo("BarberTurno A");

        assertThat(parameters.get(1).get("type").asString()).isEqualTo("text");
        assertThat(parameters.get(1).get("parameter_name").asString()).isEqualTo("fecha");
        assertThat(parameters.get(1).get("text").asString()).isEqualTo("Hoy 16:00");

        assertThat(parameters.get(2).get("type").asString()).isEqualTo("text");
        assertThat(parameters.get(2).get("parameter_name").asString()).isEqualTo("profesional");
        assertThat(parameters.get(2).get("text").asString()).isEqualTo("Barbero A");

        assertThat(parameters.get(3).get("type").asString()).isEqualTo("text");
        assertThat(parameters.get(3).get("parameter_name").asString()).isEqualTo("codigo");
        assertThat(parameters.get(3).get("text").asString()).isEqualTo("BT-201");
    }

    @Test
    void encolarPlantilla_conVentanaAbierta_seEnviaSiempreComoPlantilla() {
        long conversacionId = crearConversacion(a, WA_CLIENTE); // ventana abierta
        Map<String, String> params = Map.of(
                "negocio", "BarberTurno A",
                "codigo", "BT-202",
                "fecha", "Mañana 10:00",
                "profesional", "Barbero A");
        long mensajeId = encolarPlantillaDirecto(a, conversacionId, PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO, params, OptionalLong.empty());

        servidorGraph.encolarExito("wamid.SIEMPRE_PLANTILLA");
        enviador.enviarLote();

        var fila = filaMensaje(a, mensajeId);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("enviado_como")).isEqualTo("PLANTILLA");

        var peticion = servidorGraph.ultimaPeticion();
        assertThat(peticion.cuerpoJson().get("type").asString()).isEqualTo("template");
    }

    // --- CI-3: Aislamiento por negocio ---

    @Test
    void aislamiento_cadaMensajeSaleConElPhoneNumberIdDeSuNegocio() {
        long convA = crearConversacion(a, WA_CLIENTE);
        long convB = crearConversacion(b, WA_CLIENTE);

        long msgA = encolarTextoDirecto(a, convA, "Texto A", OptionalLong.empty());
        long msgB = encolarTextoDirecto(b, convB, "Texto B", OptionalLong.empty());

        servidorGraph.encolarExito("wamid.A");
        servidorGraph.encolarExito("wamid.B");

        enviador.enviarLote();

        assertThat(filaMensaje(a, msgA).get("estado")).isEqualTo("ENVIADO");
        assertThat(filaMensaje(b, msgB).get("estado")).isEqualTo("ENVIADO");

        assertThat(servidorGraph.peticiones()).extracting(ServidorGraphFalso.PeticionRegistrada::ruta)
                .containsExactly("/v26.0/" + NUMERO_A + "/messages", "/v26.0/" + NUMERO_B + "/messages");
    }

    // --- CI-4: Concurrencia, reclamos y orden por conversación ---

    @Test
    void concurrencia_dosEnviadoresALaVez_envianCadaMensajeUnaSolaVez() throws Exception {
        List<Long> idsMensajes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long conv = crearConversacion(a, "5199900000" + i);
            long id = encolarTextoDirecto(a, conv, "Mensaje conc " + i, OptionalLong.empty());
            idsMensajes.add(id);
        }

        CyclicBarrier barreraSolapamiento = new CyclicBarrier(2);
        AtomicInteger peticionesSincronizadas = new AtomicInteger();

        // Encolamos 6 respuestas de éxito; para las 2 primeras forzamos que ambos hilos se sincronicen
        // en la barrera dentro de la petición HTTP a Graph, garantizando solapamiento simultáneo
        for (int i = 0; i < 6; i++) {
            int indice = i;
            servidorGraph.encolarInterceptando(() -> {
                if (indice < 2) {
                    try {
                        peticionesSincronizadas.incrementAndGet();
                        barreraSolapamiento.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException("Error en barrera de concurrencia", e);
                    }
                }
            }, "wamid.CONC_" + i);
        }

        int reclamados;
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var f1 = hilos.submit(() -> enviador.enviarLote());
            var f2 = hilos.submit(() -> enviador.enviarLote());
            reclamados = f1.get(15, TimeUnit.SECONDS) + f2.get(15, TimeUnit.SECONDS);
        }

        assertThat(peticionesSincronizadas.get())
                .as("Ambos enviadores deben haberse sincronizado concurrentemente en el servidor falso")
                .isEqualTo(2);
        assertThat(reclamados).isEqualTo(6);

        // Verificamos exactamente una petición por identificador de mensaje, sin duplicados
        assertThat(servidorGraph.peticiones()).hasSize(6);
        List<String> callbacks = servidorGraph.peticiones().stream()
                .map(p -> p.cuerpoJson().get("biz_opaque_callback_data").asString())
                .toList();
        assertThat(callbacks).hasSameSizeAs(Set.copyOf(callbacks));
        for (long id : idsMensajes) {
            assertThat(callbacks).contains("bt:m:" + id + ":1");
        }
        assertThat(contarMensajes(a, EstadoMensaje.ENVIADO)).isEqualTo(6);
    }

    @Test
    void reclamoVencido_seRecuperaYSeReenviaConFichaIncrementada() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Texto recuperable", OptionalLong.empty());

        // Simulamos que el primer intento cayó o demoró sin cerrar (reclamo de 60s)
        ejecutor.ejecutar(a, () -> sql.update("UPDATE mensaje_whatsapp SET intentos = 1, proximo_intento = ? WHERE id = ?",
                Timestamp.from(reloj.instant().minusSeconds(1)), msg));

        servidorGraph.encolarExito("wamid.RECUPERADO");
        enviador.enviarLote();

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("intentos")).isEqualTo(2);

        var peticion = servidorGraph.ultimaPeticion();
        assertThat(peticion.cuerpoJson().get("biz_opaque_callback_data").asString())
                .isEqualTo("bt:m:" + msg + ":2");
    }

    @Test
    void ordenPorConversacion_mensajeAplazadoRetieneALosPosteriores() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long primero = encolarTextoDirecto(a, conv, "Primero", OptionalLong.empty());
        long segundo = encolarTextoDirecto(a, conv, "Segundo", OptionalLong.empty());

        // El primer mensaje falla con un error transitorio y queda aplazado a ahora + 30s
        servidorGraph.encolarErrorMeta(500, 131000, "Error transitorio");
        enviador.enviarLote();

        assertThat(filaMensaje(a, primero).get("estado")).isEqualTo("PENDIENTE");
        assertThat(filaMensaje(a, primero).get("intentos")).isEqualTo(1);
        assertThat(filaMensaje(a, segundo).get("estado")).isEqualTo("PENDIENTE");
        assertThat(filaMensaje(a, segundo).get("intentos")).isEqualTo(0);

        // Al pasar de nuevo el enviador en el tiempo actual, el segundo NO se reclama
        // porque el primero de la misma conversación está pendiente aplazado (NOT EXISTS)
        enviador.enviarLote();
        assertThat(filaMensaje(a, segundo).get("intentos")).isEqualTo(0);

        // Al avanzar el reloj 30s, el primero se reintenta y sale, y luego el segundo
        reloj.adelantar(Duration.ofSeconds(30));
        servidorGraph.encolarExito("wamid.PRIMERO_OK");
        servidorGraph.encolarExito("wamid.SEGUNDO_OK");

        enviador.enviarLote();
        assertThat(filaMensaje(a, primero).get("estado")).isEqualTo("ENVIADO");
        assertThat(filaMensaje(a, segundo).get("estado")).isEqualTo("ENVIADO");
    }

    // --- CI-6: Reintentos y fallos permanentes ---

    @Test
    void reintentos_erroresTransitoriosRepetidos_terminanEnReintentosAgotados() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Texto reintentable", OptionalLong.empty());

        // Esperas exponenciales configuradas: base 30s, x2, tope 5m (300s):
        // Intento 1 -> espera 30s
        // Intento 2 -> espera 60s
        // Intento 3 -> espera 120s
        // Intento 4 -> espera 240s
        // Intento 5 -> REINTENTOS_AGOTADOS
        int[] esperasSegundos = {30, 60, 120, 240};

        for (int intento = 1; intento <= 5; intento++) {
            Instant momentoFallo = reloj.instant();
            servidorGraph.encolarErrorMeta(429, 130429, "Rate limit hit");
            enviador.enviarLote();

            var fila = filaMensaje(a, msg);
            assertThat(fila.get("intentos")).isEqualTo(intento);

            if (intento <= 4) {
                int esperaEsperada = esperasSegundos[intento - 1];
                assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
                assertThat(((Timestamp) fila.get("proximo_intento")).toInstant())
                        .as("proximo_intento tras intento %d debe ser ahora + %ds", intento, esperaEsperada)
                        .isEqualTo(momentoFallo.plusSeconds(esperaEsperada));

                // Verificamos ausencia de petición antes del vencimiento (1 segundo antes)
                reloj.adelantar(Duration.ofSeconds(esperaEsperada - 1));
                enviador.enviarLote();
                assertThat(filaMensaje(a, msg).get("intentos")).isEqualTo(intento);

                // Adelantamos el segundo restante para cumplir la espera
                reloj.adelantar(Duration.ofSeconds(1));
            } else {
                // Tras 5 intentos, termina en FALLIDO con REINTENTOS_AGOTADOS y sin proximo_intento
                assertThat(fila.get("estado")).isEqualTo("FALLIDO");
                assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.REINTENTOS_AGOTADOS.name());
                assertThat(fila.get("proximo_intento")).isNull();
            }
        }
    }

    @Test
    void falloPermanente_131026_pasaInmediatamenteAFallidoSinReintentos() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Texto destino invalido", OptionalLong.empty());

        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("error_codigo")).isEqualTo(MotivoFallo.DESTINO_NO_VALIDO.name());
        assertThat(fila.get("codigo_meta")).isEqualTo(131026);
        assertThat(fila.get("intentos")).isEqualTo(1);
    }

    // --- CI-7: Resultado desconocido (Desconocido) ---

    @Test
    void resultadoDesconocido_tiempoAgotadoReintentaTrasConciliacionYAlSegundoFalla() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Texto desconocido", OptionalLong.of(reserva));

        // Intento 1: tiempo de respuesta agotado
        servidorGraph.encolarDemora(Duration.ofSeconds(2), 200, "{\"messages\":[{\"id\":\"tardio\"}]}");
        enviador.enviarLote();

        var fila1 = filaMensaje(a, msg);
        assertThat(fila1.get("estado")).isEqualTo("PENDIENTE");
        assertThat(fila1.get("inciertos")).isEqualTo(1);
        // Espera de conciliación configurada en 2 minutos
        assertThat(((Timestamp) fila1.get("proximo_intento")).toInstant())
                .isEqualTo(reloj.instant().plus(Duration.ofMinutes(2)));

        // Avanzamos 2 minutos y vuelve a ocurrir un tiempo agotado
        reloj.adelantar(Duration.ofMinutes(2));
        servidorGraph.encolarDemora(Duration.ofSeconds(2), 200, "{\"messages\":[{\"id\":\"tardio2\"}]}");
        enviador.enviarLote();

        var fila2 = filaMensaje(a, msg);
        assertThat(fila2.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila2.get("error_codigo")).isEqualTo(MotivoFallo.RESULTADO_DESCONOCIDO.name());
        assertThat(fila2.get("inciertos")).isEqualTo(2);

        // CI-9: Aviso interno para RESULTADO_DESCONOCIDO
        int notifTitular = contarNotificaciones(a, titular.getId());
        int notifEncargado = contarNotificaciones(a, encargado.getId());
        int notifBarbero = contarNotificaciones(a, barberoCuenta.getId());
        assertThat(notifTitular).isEqualTo(1);
        assertThat(notifEncargado).isEqualTo(1);
        assertThat(notifBarbero).isEqualTo(1);

        String mensajeNotif = ejecutor.ejecutar(a, () -> sql.queryForObject(
                "SELECT mensaje FROM notificacion WHERE usuario_id = ? LIMIT 1",
                String.class, titular.getId()));
        assertThat(mensajeNotif).contains("No se pudo confirmar si se envió por WhatsApp el mensaje de la reserva BT-" + reserva)
                .contains("Compruébelo con el cliente.")
                .doesNotContain(WA_CLIENTE, "Cliente Ficticio", "Texto desconocido");
    }

    // --- CI-9: Aviso interno en fallos con reserva ---

    @Test
    void avisoInterno_soloConReserva_destinatariosActivosYTextoSinDatosDelCliente() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Texto con fallo permanente", OptionalLong.of(reserva));

        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");

        assertThat(contarNotificaciones(a, titular.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, encargado.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, barberoCuenta.getId())).isEqualTo(1);

        String texto = ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT mensaje FROM notificacion WHERE usuario_id = ? LIMIT 1",
                String.class, titular.getId()));
        assertThat(texto).contains("No se pudo enviar por WhatsApp el mensaje de la reserva BT-" + reserva)
                .contains(MotivoFallo.DESTINO_NO_VALIDO.descripcion())
                .doesNotContain(WA_CLIENTE, "Cliente Ficticio");
    }

    @Test
    void avisoInterno_sinReserva_noGeneraNotificaciones() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Texto sin reserva", OptionalLong.empty());

        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();

        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("FALLIDO");
        assertThat(ejecutor.ejecutar(a, () -> sql.queryForObject("SELECT count(*) FROM notificacion WHERE negocio_id = ?", Integer.class, a))).isZero();
    }

    @Test
    void loteLento_reclamoDeUnoEnUno_plazoEmpiezaAlReclamarCadaMensaje() {
        long conv1 = crearConversacion(a, "51999000011");
        long conv2 = crearConversacion(a, "51999000012");
        long conv3 = crearConversacion(a, "51999000013");

        long msg1 = encolarTextoDirecto(a, conv1, "Msg 1", OptionalLong.empty());
        long msg2 = encolarTextoDirecto(a, conv2, "Msg 2", OptionalLong.empty());
        long msg3 = encolarTextoDirecto(a, conv3, "Msg 3", OptionalLong.empty());

        // Durante el envío del msg 1 (t=0 -> t=35s):
        // Inspeccionamos que msg2 y msg3 aún NO han sido reclamados (intentos=0, PENDIENTE)
        servidorGraph.encolarInterceptando(() -> {
            var fila2 = filaMensaje(a, msg2);
            var fila3 = filaMensaje(a, msg3);
            assertThat(fila2.get("intentos")).as("msg2 no debe estar reclamado durante envío de msg1").isEqualTo(0);
            assertThat(fila3.get("intentos")).as("msg3 no debe estar reclamado durante envío de msg1").isEqualTo(0);
            assertThat(fila2.get("estado")).isEqualTo("PENDIENTE");
            assertThat(fila3.get("estado")).isEqualTo("PENDIENTE");
            reloj.adelantar(Duration.ofSeconds(35));
        }, "wamid.LENTO_1");

        // Durante el envío del msg 2 (t=35s -> t=70s acumulados, superando los 60s de reclamo inicial):
        // Inspeccionamos que msg3 aún NO ha sido reclamado (intentos=0)
        servidorGraph.encolarInterceptando(() -> {
            var fila3 = filaMensaje(a, msg3);
            assertThat(fila3.get("intentos")).as("msg3 no debe estar reclamado durante envío de msg2").isEqualTo(0);
            assertThat(fila3.get("estado")).isEqualTo("PENDIENTE");
            reloj.adelantar(Duration.ofSeconds(35));
        }, "wamid.LENTO_2");

        // Durante el envío del msg 3 (t=70s -> t=80s acumulados):
        servidorGraph.encolarInterceptando(() -> reloj.adelantar(Duration.ofSeconds(10)), "wamid.LENTO_3");

        int reclamados = enviador.enviarLote();
        assertThat(reclamados).isEqualTo(3);

        // Los tres mensajes se procesaron con éxito sin que sus reclamos vencieran prematuramente
        assertThat(filaMensaje(a, msg1).get("estado")).isEqualTo("ENVIADO");
        assertThat(filaMensaje(a, msg2).get("estado")).isEqualTo("ENVIADO");
        assertThat(filaMensaje(a, msg3).get("estado")).isEqualTo("ENVIADO");
        assertThat(filaMensaje(a, msg1).get("intentos")).isEqualTo(1);
        assertThat(filaMensaje(a, msg2).get("intentos")).isEqualTo(1);
        assertThat(filaMensaje(a, msg3).get("intentos")).isEqualTo(1);
    }

    @Test
    void fichaAntigua_noCierraMensajeReclamadoDeNuevo() throws Exception {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Msg con ficha antigua", OptionalLong.empty());

        CountDownLatch intento1LlegoAServidor = new CountDownLatch(1);
        CountDownLatch retenerIntento1 = new CountDownLatch(1);

        // Intento 1: se retiene en el servidor falso
        servidorGraph.encolarInterceptando(() -> {
            intento1LlegoAServidor.countDown();
            try {
                retenerIntento1.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "wamid.INTENTO_1_TARDIO");

        // Intento 2: responderá inmediatamente con éxito
        servidorGraph.encolarExito("wamid.INTENTO_2_VIGENTE");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 1. Lanzamos el primer enviador en segundo plano (intento 1 con ficha 1)
            var futureIntento1 = pool.submit(() -> enviador.enviarLote());

            // 2. Esperamos a que el intento 1 haya llegado a la llamada HTTP de Graph
            assertThat(intento1LlegoAServidor.await(5, TimeUnit.SECONDS)).isTrue();

            // 3. Adelantamos el reloj 61 segundos para vencer el plazo de reclamo de 60s
            reloj.adelantar(Duration.ofSeconds(61));

            // 4. En el hilo principal ejecutamos enviarLote(): reclama con ficha 2, envía y cierra en ENVIADO
            int reclamadosIntento2 = enviador.enviarLote();
            assertThat(reclamadosIntento2).isEqualTo(1);

            var filaTrasIntento2 = filaMensaje(a, msg);
            assertThat(filaTrasIntento2.get("estado")).isEqualTo("ENVIADO");
            assertThat(filaTrasIntento2.get("wamid")).isEqualTo("wamid.INTENTO_2_VIGENTE");
            assertThat(filaTrasIntento2.get("intentos")).isEqualTo(2);

            // 5. Liberamos la respuesta HTTP tardía del intento 1
            retenerIntento1.countDown();
            futureIntento1.get(5, TimeUnit.SECONDS);

            // 6. Comprobamos que el cierre del intento 1 con ficha antigua es inocuo y no sobrescribe el wamid vigente
            var filaFinal = filaMensaje(a, msg);
            assertThat(filaFinal.get("estado")).isEqualTo("ENVIADO");
            assertThat(filaFinal.get("wamid"))
                    .as("La ficha antigua no debe sobrescribir el wamid vigente")
                    .isEqualTo("wamid.INTENTO_2_VIGENTE");
            assertThat(filaFinal.get("intentos")).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void conciliacionDuranteEspera_avisoSentPasaAEnviadoYSinReenvio() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Msg a conciliar", OptionalLong.of(reserva));

        servidorGraph.encolarDemora(Duration.ofSeconds(2), 200, "{\"messages\":[{\"id\":\"tardio\"}]}");
        enviador.enviarLote();

        var filaDespuesIntento1 = filaMensaje(a, msg);
        assertThat(filaDespuesIntento1.get("estado")).isEqualTo("PENDIENTE");
        assertThat(filaDespuesIntento1.get("inciertos")).isEqualTo(1);
        assertThat(servidorGraph.peticiones()).hasSize(1);

        reloj.adelantar(Duration.ofSeconds(30));
        var datoEstado = mapper.createObjectNode()
                .put("id", "wamid.CONCILIADO_EN_ESPERA")
                .put("status", "sent")
                .put("recipient_id", WA_CLIENTE)
                .put("biz_opaque_callback_data", "bt:m:" + msg + ":1");
        var avisoSent = new EstadoEntrante(10L, a, "wamid.CONCILIADO_EN_ESPERA", "sent", WA_CLIENTE, reloj.instant(), datoEstado);
        ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status -> {
            receptorEstados.recibir(avisoSent);
            return null;
        }));

        var filaConciliada = filaMensaje(a, msg);
        assertThat(filaConciliada.get("estado")).isEqualTo("ENVIADO");
        assertThat(filaConciliada.get("wamid")).isEqualTo("wamid.CONCILIADO_EN_ESPERA");
        assertThat(filaConciliada.get("proximo_intento")).isNull();

        reloj.adelantar(Duration.ofMinutes(2));
        enviador.enviarLote();

        assertThat(servidorGraph.peticiones()).as("No debe haber reenvío tras la conciliación").hasSize(1);
    }

    @Test
    void noEnviado_puertoCerrado_reprogramaSinIncrementarInciertos() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Msg puerto cerrado", OptionalLong.empty());

        ClienteGraphWhatsApp clienteConexionFallida = (numero, cuerpo) -> new ResultadoEnvio.NoEnviado();
        var enviadorNoEnviado = new EnviadorMensajesWhatsApp(cola, canales, directorio, membresias, ejecutor,
                clienteConexionFallida, avisos, opciones, reloj, mapper, gestor);

        enviadorNoEnviado.enviarLote();

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat(fila.get("intentos")).isEqualTo(1);
        assertThat(fila.get("inciertos")).as("NoEnviado no debe incrementar inciertos").isEqualTo(0);
        assertThat(((Timestamp) fila.get("proximo_intento")).toInstant())
                .isEqualTo(reloj.instant().plus(Duration.ofSeconds(30)));
    }

    @Test
    void enviarLote_conMensajeInteractivo_enviaCuerpoExactoConObjetoIntactoYEnviadoComoInteractivo() {
        long conv = crearConversacion(a, WA_CLIENTE);
        ObjectNode interactivo = mapper.createObjectNode();
        interactivo.put("type", "button");
        interactivo.putObject("body").put("text", "¿Confirmas tu cita?");
        var action = interactivo.putObject("action");
        var buttons = action.putArray("buttons");
        buttons.addObject().put("type", "reply").putObject("reply").put("id", "btn_si").put("title", "Sí");

        long msg = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarInteractivo(conv, interactivo, OrigenMensaje.ASISTENTE, OptionalLong.empty())));

        servidorGraph.encolarExito("wamid.INTERACTIVO_001");
        enviador.enviarLote();

        var fila = filaMensaje(a, msg);
        assertThat(fila.get("estado")).isEqualTo("ENVIADO");
        assertThat(fila.get("enviado_como")).isEqualTo("INTERACTIVO");

        var peticion = servidorGraph.ultimaPeticion();
        assertThat(peticion.cuerpoJson().get("messaging_product").asString()).isEqualTo("whatsapp");
        assertThat(peticion.cuerpoJson().get("recipient_type").asString()).isEqualTo("individual");
        assertThat(peticion.cuerpoJson().get("to").asString()).isEqualTo(WA_CLIENTE);
        assertThat(peticion.cuerpoJson().get("type").asString()).isEqualTo("interactive");
        assertThat(peticion.cuerpoJson().get("biz_opaque_callback_data").asString()).isEqualTo("bt:m:" + msg + ":1");

        var interactive = peticion.cuerpoJson().get("interactive");
        assertThat(interactive.get("type").asString()).isEqualTo("button");
        assertThat(interactive.get("body").get("text").asString()).isEqualTo("¿Confirmas tu cita?");

        var actionNode = interactive.get("action");
        var buttonsNode = actionNode.get("buttons");
        assertThat(buttonsNode.isArray()).isTrue();
        assertThat(buttonsNode).hasSize(1);
        var btnNode = buttonsNode.get(0);
        assertThat(btnNode.get("type").asString()).isEqualTo("reply");
        assertThat(btnNode.get("reply").get("id").asString()).isEqualTo("btn_si");
        assertThat(btnNode.get("reply").get("title").asString()).isEqualTo("Sí");
    }

    @Test
    void enviarLote_lasTresFormas_incluyenBizOpaqueCallbackDataConFichaExacta() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msgTexto = encolarTextoDirecto(a, conv, "Texto simple", OptionalLong.empty());

        ObjectNode btn = mapper.createObjectNode();
        btn.put("type", "button");
        btn.putObject("body").put("text", "Opciones");
        long msgInteractivo = ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarInteractivo(conv, btn, OrigenMensaje.ASISTENTE, OptionalLong.empty())));

        Map<String, String> params = Map.of("negocio", "BarberTurno", "codigo", "BT-9", "fecha", "Hoy", "profesional", "Barbero");
        long msgPlantilla = encolarPlantillaDirecto(a, conv, PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO, params, OptionalLong.empty());

        servidorGraph.encolarExito("wamid.T");
        servidorGraph.encolarExito("wamid.I");
        servidorGraph.encolarExito("wamid.P");

        enviador.enviarLote();

        var peticiones = servidorGraph.peticiones();
        assertThat(peticiones).hasSize(3);
        assertThat(peticiones.get(0).cuerpoJson().get("biz_opaque_callback_data").asString()).isEqualTo("bt:m:" + msgTexto + ":1");
        assertThat(peticiones.get(1).cuerpoJson().get("biz_opaque_callback_data").asString()).isEqualTo("bt:m:" + msgInteractivo + ":1");
        assertThat(peticiones.get(2).cuerpoJson().get("biz_opaque_callback_data").asString()).isEqualTo("bt:m:" + msgPlantilla + ":1");
    }

    @Test
    void enviadoComo_guardadoEnBaseDeDatosAntesDeLaPeticionHttp() {
        long conv = crearConversacion(a, WA_CLIENTE);
        long msg = encolarTextoDirecto(a, conv, "Texto verificacion enviado_como", OptionalLong.empty());

        AtomicReference<String> observadoEnBase = new AtomicReference<>();
        AtomicReference<String> estadoEnBase = new AtomicReference<>();

        servidorGraph.encolarInterceptando(() -> {
            var fila = filaMensaje(a, msg);
            observadoEnBase.set((String) fila.get("enviado_como"));
            estadoEnBase.set((String) fila.get("estado"));
        }, "wamid.PREVIO_HTTP");

        enviador.enviarLote();

        assertThat(observadoEnBase.get())
                .as("enviado_como debe estar persistido en BD antes de la llamada HTTP")
                .isEqualTo("TEXTO");
        assertThat(estadoEnBase.get())
                .as("El estado durante la llamada HTTP debe ser PENDIENTE")
                .isEqualTo("PENDIENTE");

        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("ENVIADO");
    }

    @Test
    void avisoInterno_destinatariosExcluyenCuentasInactivasYMembresiasRevocadasYEligenProfesionalActual() {
        Usuario titularInactivo = usuarios.saveAndFlush(new Usuario("Titular inactivo", "titular-inactivo@ejemplo.test",
                "999000010", "hash", Rol.CLIENTE, reloj.instant(), reloj.instant()));
        sql.update("UPDATE usuario SET activo = false WHERE id = ?", titularInactivo.getId());
        membresias.save(new Membresia(a, titularInactivo.getId(), RolNegocio.PROPIETARIO, reloj.instant()));

        Usuario encargadoRevocado = usuarios.saveAndFlush(new Usuario("Encargado revocado", "encargado-revocado@ejemplo.test",
                "999000011", "hash", Rol.CLIENTE, reloj.instant(), reloj.instant()));
        var mRevocada = new Membresia(a, encargadoRevocado.getId(), RolNegocio.ENCARGADO, reloj.instant());
        mRevocada.revocar(MotivoRevocacion.DECISION_GESTOR, reloj.instant());
        membresias.save(mRevocada);

        Usuario barberoCuentaActual = usuarios.saveAndFlush(new Usuario("Barbero Actual", "barbero-actual@ejemplo.test",
                "999000012", "hash", Rol.BARBERO, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(a, barberoCuentaActual.getId(), RolNegocio.BARBERO, reloj.instant()));
        long barberoActualId = ejecutor.ejecutar(a, () -> barberos.saveAndFlush(
                new Barbero(barberoCuentaActual, "Barbero Actual", reloj.instant())).getId());

        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        ejecutor.ejecutar(a, () -> sql.update("UPDATE reserva SET barbero_id = ? WHERE id = ?", barberoActualId, reserva));

        long msg = encolarTextoDirecto(a, conv, "Msg fallo permanente", OptionalLong.of(reserva));

        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();

        assertThat(contarNotificaciones(a, titular.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, encargado.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, barberoCuentaActual.getId())).isEqualTo(1);

        assertThat(contarNotificaciones(a, titularInactivo.getId())).isZero();
        assertThat(contarNotificaciones(a, encargadoRevocado.getId())).isZero();
        assertThat(contarNotificaciones(a, barberoCuenta.getId())).isZero();

        // Verificamos destinatarios exactos en A sin duplicados
        List<Long> destinatariosA = ejecutor.ejecutar(a, () -> sql.queryForList(
                "SELECT usuario_id FROM notificacion WHERE negocio_id = ?", Long.class, a));
        assertThat(destinatariosA).containsExactlyInAnyOrder(titular.getId(), encargado.getId(), barberoCuentaActual.getId());

        // Aislamiento: cero avisos en el negocio B
        assertThat(ejecutor.ejecutar(b, () -> sql.queryForObject(
                "SELECT count(*) FROM notificacion WHERE negocio_id = ?", Integer.class, b))).isZero();
    }

    @Test
    void avisoInterno_profesionalConCuentaInactivaOMembresiaRevocada_noRecibeAviso() {
        Usuario barberoInactivo = usuarios.saveAndFlush(new Usuario("Barbero inactivo", "barbero-inactivo@ejemplo.test",
                "999000021", "hash", Rol.BARBERO, reloj.instant(), reloj.instant()));
        sql.update("UPDATE usuario SET activo = false WHERE id = ?", barberoInactivo.getId());
        membresias.save(new Membresia(a, barberoInactivo.getId(), RolNegocio.BARBERO, reloj.instant()));
        long barberoInactivoId = ejecutor.ejecutar(a, () -> barberos.saveAndFlush(
                new Barbero(barberoInactivo, "Barbero Cuenta Inactiva", reloj.instant())).getId());

        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        ejecutor.ejecutar(a, () -> sql.update("UPDATE reserva SET barbero_id = ? WHERE id = ?", barberoInactivoId, reserva));

        long msg = encolarTextoDirecto(a, conv, "Msg con profesional inactivo", OptionalLong.of(reserva));

        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();

        // Los gestores activos sí reciben
        assertThat(contarNotificaciones(a, titular.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, encargado.getId())).isEqualTo(1);
        // El profesional con cuenta inactiva NO recibe aviso
        assertThat(contarNotificaciones(a, barberoInactivo.getId())).isZero();

        List<Long> destinatariosA = ejecutor.ejecutar(a, () -> sql.queryForList(
                "SELECT usuario_id FROM notificacion WHERE negocio_id = ?", Long.class, a));
        assertThat(destinatariosA).containsExactlyInAnyOrder(titular.getId(), encargado.getId());
        assertThat(ejecutor.ejecutar(b, () -> sql.queryForObject(
                "SELECT count(*) FROM notificacion WHERE negocio_id = ?", Integer.class, b))).isZero();
    }

    @Test
    void avisoInterno_coincidenciaProfesionalYGestor_noGeneraAvisosDuplicados() {
        // El titular (PROPIETARIO) es asignado directamente como el profesional de la reserva
        long barberoTitularId = ejecutor.ejecutar(a, () -> barberos.saveAndFlush(
                new Barbero(titular, "Titular Profesional", reloj.instant())).getId());

        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        ejecutor.ejecutar(a, () -> sql.update("UPDATE reserva SET barbero_id = ? WHERE id = ?", barberoTitularId, reserva));

        long msg = encolarTextoDirecto(a, conv, "Msg gestor y profesional coinciden", OptionalLong.of(reserva));

        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();

        // Titular debe recibir exactamente 1 notificación (sin duplicados a pesar de tener dos roles)
        assertThat(contarNotificaciones(a, titular.getId())).isEqualTo(1);
        assertThat(contarNotificaciones(a, encargado.getId())).isEqualTo(1);

        List<Long> destinatariosA = ejecutor.ejecutar(a, () -> sql.queryForList(
                "SELECT usuario_id FROM notificacion WHERE negocio_id = ?", Long.class, a));
        assertThat(destinatariosA).containsExactlyInAnyOrder(titular.getId(), encargado.getId());
        assertThat(ejecutor.ejecutar(b, () -> sql.queryForObject(
                "SELECT count(*) FROM notificacion WHERE negocio_id = ?", Integer.class, b))).isZero();
    }

    @Test
    void registros_enFalloDeEnvioYEnAviso_noContienenDatosPersonalesNiToken(CapturedOutput salida) {
        long conv = crearConversacion(a, WA_CLIENTE);
        long reserva = crearReserva(a, WA_CLIENTE);
        String textoSecreto = "Texto con datos medicos privados y sensibles";
        long msg = encolarTextoDirecto(a, conv, textoSecreto, OptionalLong.of(reserva));

        servidorGraph.encolarErrorMeta(400, 131026, "Receiver is not valid");
        enviador.enviarLote();

        assertThat(filaMensaje(a, msg).get("estado")).isEqualTo("FALLIDO");

        String registros = salida.getAll();
        assertThat(registros).contains("Mensaje WhatsApp " + msg)
                .doesNotContain(WA_CLIENTE)
                .doesNotContain(textoSecreto)
                .doesNotContain("token-ficticio-prueba");
    }
}
