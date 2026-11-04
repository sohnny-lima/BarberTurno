package pe.barberturno.whatsapp;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Recuperación de los presupuestos de V2-DA-70 con transacciones reales y peticiones al Graph falso.
 * Inyecta caídas antes del HTTP y rollback del cierre sin crear otro contexto de Spring.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class PresupuestoEnvioWhatsAppIT extends WhatsAppSalidaPruebaBase {
    @Autowired private SalidaWhatsApp salida;
    @Autowired private AvisoFalloWhatsApp avisos;
    @Autowired private SalidaWhatsAppPropiedades opciones;

    private long encolar() {
        long conversacion = crearConversacion(a, WA_CLIENTE);
        return ejecutor.ejecutar(a, () -> new TransactionTemplate(gestor).execute(tx ->
                salida.encolarTexto(conversacion, "Aviso ficticio", OrigenMensaje.SISTEMA, OptionalLong.empty())));
    }

    private EnviadorMensajesWhatsApp nuevoEnviador() {
        return enviador(new ColaSalidaWhatsApp(entityManager), new ClienteGraphWhatsAppRest(opciones), opciones);
    }

    private EnviadorMensajesWhatsApp enviador(ColaSalidaWhatsApp cola, ClienteGraphWhatsApp cliente,
            SalidaWhatsAppPropiedades propiedades) {
        return new EnviadorMensajesWhatsApp(cola, canales, directorio, membresias, ejecutor, cliente,
                avisos, propiedades, reloj, mapper, gestor);
    }

    private ColaSalidaWhatsApp perderPrimerCierre() {
        var cola = spy(new ColaSalidaWhatsApp(entityManager));
        var pendiente = new AtomicBoolean(true);
        doAnswer(invocacion -> {
            Object cambio = invocacion.callRealMethod();
            if (pendiente.getAndSet(false)) throw new IllegalStateException("Caída ficticia al cerrar");
            return cambio;
        }).when(cola).cambiar(any(), any(), any(), isNull(), isNull(), anyInt(), isNull(), any());
        return cola;
    }

    private void comprobarSinMasSolicitudes(long mensaje, int solicitudes, String motivo) {
        reloj.adelantar(Duration.ofMinutes(10));
        nuevoEnviador().enviarLote();
        nuevoEnviador().enviarLote();
        assertThat(servidorGraph.peticiones()).hasSize(solicitudes);
        assertThat(filaMensaje(a, mensaje).get("estado")).isEqualTo("FALLIDO");
        assertThat(filaMensaje(a, mensaje).get("error_codigo")).isEqualTo(motivo);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aceptacion_aLaPrimeraOEnElAdicional_noReenviaNiAfirmaEntrega(boolean trasDesconocido) {
        long mensaje = encolar();
        if (trasDesconocido) {
            servidorGraph.encolarRespuesta(200, "{}");
            nuevoEnviador().enviarLote();
            reloj.adelantar(opciones.esperaConciliacion());
        }
        servidorGraph.encolarExito("wamid.ACEPTADO");
        nuevoEnviador().enviarLote();
        reloj.adelantar(Duration.ofMinutes(10));
        nuevoEnviador().enviarLote();
        int solicitudes = trasDesconocido ? 2 : 1;
        assertThat(servidorGraph.peticiones()).hasSize(solicitudes);
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(solicitudes);
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(solicitudes);
        assertThat(filaMensaje(a, mensaje).get("estado")).isEqualTo("ENVIADO");
        assertThat(filaMensaje(a, mensaje).get("wamid")).isEqualTo("wamid.ACEPTADO");
    }

    @Test
    void cierrePerdidoTrasHttp_reinicioRespetaEsperaYUnSoloAdicionalAunqueSea429() {
        long mensaje = encolar();
        servidorGraph.encolarExito("wamid.CIERRE_PERDIDO");
        enviador(perderPrimerCierre(), new ClienteGraphWhatsAppRest(opciones), opciones).enviarLote();

        var fila = filaMensaje(a, mensaje);
        assertThat(servidorGraph.peticiones()).hasSize(1);
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
        assertThat(fila.get("wamid")).isNull();
        assertThat(fila.get("intentos")).isEqualTo(1);
        assertThat(fila.get("inciertos")).isEqualTo(1);
        assertThat(((Timestamp) fila.get("proximo_intento")).toInstant())
                .isEqualTo(reloj.instant().plus(opciones.esperaConciliacion()));

        reloj.adelantar(opciones.reclamo());
        nuevoEnviador().enviarLote();
        assertThat(servidorGraph.peticiones()).hasSize(1);
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(1);
        reloj.adelantar(opciones.esperaConciliacion().minus(opciones.reclamo()).minusSeconds(1));
        nuevoEnviador().enviarLote();
        assertThat(servidorGraph.peticiones()).hasSize(1);

        reloj.adelantar(Duration.ofSeconds(1));
        servidorGraph.encolarErrorMeta(429, 130429, "Límite ficticio");
        nuevoEnviador().enviarLote();
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(2);
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
        comprobarSinMasSolicitudes(mensaje, 2, "RESULTADO_DESCONOCIDO");
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 429, 500, 400})
    void desconocido_reinicioYAdicionalNoAceptado_soloDosSolicitudes(int respuesta) {
        long mensaje = encolar();
        servidorGraph.encolarRespuesta(200, "{}");
        nuevoEnviador().enviarLote();
        assertThat(servidorGraph.peticiones()).hasSize(1);
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(1);

        reloj.adelantar(opciones.esperaConciliacion());
        if (respuesta == 400) servidorGraph.encolarErrorMeta(400, 131026, "Destino ficticio inválido");
        else servidorGraph.encolarRespuesta(respuesta, "{}");
        nuevoEnviador().enviarLote();
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
        comprobarSinMasSolicitudes(mensaje, 2, respuesta == 400 ? "DESTINO_NO_VALIDO" : "RESULTADO_DESCONOCIDO");
    }

    @Test
    void adicionalNoEnviado_noDevuelvePermiso() {
        long mensaje = encolar();
        servidorGraph.encolarRespuesta(200, "{}");
        nuevoEnviador().enviarLote();
        reloj.adelantar(opciones.esperaConciliacion());
        enviador(new ColaSalidaWhatsApp(entityManager), (numero, cuerpo) -> new ResultadoEnvio.NoEnviado(), opciones)
                .enviarLote();
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(2);
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
        comprobarSinMasSolicitudes(mensaje, 1, "RESULTADO_DESCONOCIDO");
    }

    @Test
    void dosCierresPerdidos_recuperacionNoHaceUnaTerceraSolicitud() {
        long mensaje = encolar();
        servidorGraph.encolarExito("wamid.PERDIDO_UNO");
        enviador(perderPrimerCierre(), new ClienteGraphWhatsAppRest(opciones), opciones).enviarLote();
        reloj.adelantar(opciones.esperaConciliacion());
        servidorGraph.encolarExito("wamid.PERDIDO_DOS");
        enviador(perderPrimerCierre(), new ClienteGraphWhatsAppRest(opciones), opciones).enviarLote();
        assertThat(filaMensaje(a, mensaje).get("estado")).isEqualTo("PENDIENTE");
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
        comprobarSinMasSolicitudes(mensaje, 2, "RESULTADO_DESCONOCIDO");
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(2);
    }

    @Test
    void caidaAntesDelHttp_preparacionConfirmadaConsumeAmbosPresupuestosSinSolicitudes() {
        long mensaje = encolar();
        ClienteGraphWhatsApp caerAntesDeEnviar = (numero, cuerpo) -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(filaMensaje(a, mensaje).get("inciertos"))
                    .isEqualTo(filaMensaje(a, mensaje).get("intentos"));
            throw new IllegalStateException("Caída ficticia antes del transporte");
        };
        enviador(new ColaSalidaWhatsApp(entityManager), caerAntesDeEnviar, opciones).enviarLote();
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(1);
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(1);
        assertThat(servidorGraph.peticiones()).isEmpty();
        reloj.adelantar(opciones.esperaConciliacion());
        enviador(new ColaSalidaWhatsApp(entityManager), caerAntesDeEnviar, opciones).enviarLote();
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(2);
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
        comprobarSinMasSolicitudes(mensaje, 0, "RESULTADO_DESCONOCIDO");
    }

    @Test
    void primer429_retiraPresuncionYConservaElAdicionalParaUnDesconocidoPosterior() {
        long mensaje = encolar();
        servidorGraph.encolarErrorMeta(429, 130429, "Límite ficticio");
        nuevoEnviador().enviarLote();
        assertThat(servidorGraph.peticiones()).hasSize(1);
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(0);
        reloj.adelantar(opciones.esperaBase());
        servidorGraph.encolarRespuesta(200, "{}");
        nuevoEnviador().enviarLote();
        assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(1);
        reloj.adelantar(opciones.esperaConciliacion());
        servidorGraph.encolarRespuesta(200, "{}");
        nuevoEnviador().enviarLote();
        comprobarSinMasSolicitudes(mensaje, 3, "RESULTADO_DESCONOCIDO");
    }

    @Test
    void quintoIntentoConCierrePerdido_recuperaSinReservarNiEnviarUnSexto() {
        long mensaje = encolar();
        for (int i = 0; i < 4; i++) {
            servidorGraph.encolarErrorMeta(429, 130429, "Límite ficticio");
            nuevoEnviador().enviarLote();
            reloj.adelantar(Duration.ofMinutes(5));
        }
        servidorGraph.encolarExito("wamid.QUINTO_PERDIDO");
        enviador(perderPrimerCierre(), new ClienteGraphWhatsAppRest(opciones), opciones).enviarLote();
        comprobarSinMasSolicitudes(mensaje, 5, "REINTENTOS_AGOTADOS");
        assertThat(filaMensaje(a, mensaje).get("intentos")).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrencia_reclamoVencidoYCierreAntiguo_noRestablecenElPresupuesto(boolean antiguoPrimero) throws Exception {
        long mensaje = encolar();
        var llegoPrimero = new CountDownLatch(1);
        var liberarPrimero = new CountDownLatch(1);
        var llegoSegundo = new CountDownLatch(1);
        var liberarSegundo = new CountDownLatch(1);
        var graph = new SalidaWhatsAppPropiedades.Graph(opciones.graph().urlBase(), opciones.graph().version(),
                opciones.graph().token(), Duration.ofSeconds(1), Duration.ofSeconds(15));
        var lentas = new SalidaWhatsAppPropiedades(false, opciones.intervalo(), opciones.lote(), opciones.maxIntentos(),
                opciones.esperaBase(), opciones.esperaMaxima(), opciones.esperaConciliacion(), opciones.reclamo(),
                opciones.antiguedadMaxima(), opciones.margenVentana(), graph);
        servidorGraph.encolarInterceptando(() -> esperar(llegoPrimero, liberarPrimero), "wamid.ANTIGUO");
        servidorGraph.encolarInterceptando(() -> esperar(llegoSegundo, liberarSegundo), "wamid.VIGENTE");
        try (var trabajadores = Executors.newFixedThreadPool(2)) {
            try {
                var primero = trabajadores.submit(() -> enviador(new ColaSalidaWhatsApp(entityManager),
                        new ClienteGraphWhatsAppRest(lentas), lentas).enviarLote());
                assertThat(llegoPrimero.await(10, TimeUnit.SECONDS)).isTrue();
                reloj.adelantar(opciones.esperaConciliacion());
                var segundo = trabajadores.submit(() -> enviador(new ColaSalidaWhatsApp(entityManager),
                        new ClienteGraphWhatsAppRest(lentas), lentas).enviarLote());
                assertThat(llegoSegundo.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
                if (antiguoPrimero) {
                    liberarPrimero.countDown();
                    primero.get(10, TimeUnit.SECONDS);
                    assertThat(filaMensaje(a, mensaje).get("estado")).isEqualTo("PENDIENTE");
                    assertThat(filaMensaje(a, mensaje).get("wamid")).isNull();
                    assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
                    liberarSegundo.countDown();
                    segundo.get(10, TimeUnit.SECONDS);
                } else {
                    liberarSegundo.countDown();
                    segundo.get(10, TimeUnit.SECONDS);
                    liberarPrimero.countDown();
                    primero.get(10, TimeUnit.SECONDS);
                }
                assertThat(filaMensaje(a, mensaje).get("wamid")).isEqualTo("wamid.VIGENTE");
                assertThat(filaMensaje(a, mensaje).get("inciertos")).isEqualTo(2);
                reloj.adelantar(Duration.ofMinutes(10));
                nuevoEnviador().enviarLote();
                assertThat(servidorGraph.peticiones()).hasSize(2);
                assertThat(servidorGraph.peticiones().stream().map(p ->
                        p.cuerpoJson().get("biz_opaque_callback_data").asString()))
                        .containsExactly("bt:m:" + mensaje + ":1", "bt:m:" + mensaje + ":2");
            } finally {
                liberarPrimero.countDown();
                liberarSegundo.countDown();
                trabajadores.shutdownNow();
            }
        }
    }

    private static void esperar(CountDownLatch llegada, CountDownLatch liberar) {
        llegada.countDown();
        try {
            if (!liberar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó el HTTP ficticio");
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP ficticio interrumpido", interrumpido);
        }
    }
}
