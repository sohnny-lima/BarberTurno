package pe.barberturno.whatsapp.twilio;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link BuzonRespuestasTwilio} (CT-10 y CT-12, 20-v2-twilio-sandbox.md §10).
 * Valida la exclusividad de espera por persona, el tope de cinco mensajes en orden de aceptación,
 * el cumplimiento de los plazos monotónicos de espera inicial y silencio entre respuestas,
 * el cierre atómico y su resistencia ante cierres de reservas obsoletas, la entrega inmediata de
 * {@code tomar}, y el rechazo de mensajes entregados sin espera o tras su cierre.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
class BuzonRespuestasTwilioTest {

    private static final String WA_ID = "51999887766";
    private static final String WA_ID_OTRO = "51999112233";

    private TwilioPropiedades propiedades;
    private BuzonRespuestasTwilio buzon;
    private ExecutorService ejecutor;

    @BeforeEach
    void preparar() {
        // Plazos cortos para pruebas unitarias: espera inicial de 1 s y silencio de 200 ms
        propiedades = new TwilioPropiedades("AC0123456789abcdef0123456789abcdef", "token-ficticio",
                "+14155238886", "https://pruebas.barberturno.test", "barberturno",
                Duration.ofSeconds(3), "https://api.twilio.com", true,
                Duration.ofSeconds(1), Duration.ofMillis(200));
        buzon = new BuzonRespuestasTwilio(propiedades);
        ejecutor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void limpiar() {
        ejecutor.shutdownNow();
    }

    @Test
    void abrir_unaSolaEsperaPorWaId_segundoAbrirDevuelveVacio() {
        Optional<BuzonRespuestasTwilio.Espera> primera = buzon.abrir(WA_ID);
        Optional<BuzonRespuestasTwilio.Espera> segunda = buzon.abrir(WA_ID);
        Optional<BuzonRespuestasTwilio.Espera> otraPersona = buzon.abrir(WA_ID_OTRO);

        assertThat(primera).isPresent();
        assertThat(segunda).as("segundo abrir para la misma persona debe devolver vacío").isEmpty();
        assertThat(otraPersona).as("otra persona distinta sí puede abrir su espera").isPresent();
    }

    @Test
    void entregar_sinEsperaAbierta_devuelveFalse() {
        boolean entregado = buzon.entregar("wa_inexistente", "hola");

        assertThat(entregado).isFalse();
    }

    @Test
    void entregar_conservaOrdenYMaximoCincoMensajes_elSextoDevuelveFalse() {
        var espera = buzon.abrir(WA_ID).orElseThrow();

        assertThat(buzon.entregar(WA_ID, "Mensaje 1")).isTrue();
        assertThat(buzon.entregar(WA_ID, "Mensaje 2")).isTrue();
        assertThat(buzon.entregar(WA_ID, "Mensaje 3")).isTrue();
        assertThat(buzon.entregar(WA_ID, "Mensaje 4")).isTrue();
        assertThat(buzon.entregar(WA_ID, "Mensaje 5")).isTrue();
        assertThat(buzon.entregar(WA_ID, "Mensaje 6")).as("el sexto mensaje excede el tope de 5").isFalse();

        List<String> mensajes = buzon.esperar(espera);

        assertThat(mensajes)
                .as("debe contener exactamente los 5 primeros mensajes en el orden de entrega")
                .containsExactly("Mensaje 1", "Mensaje 2", "Mensaje 3", "Mensaje 4", "Mensaje 5");
    }

    @Test
    void esperar_respetaEsperaInicial_cuandoNoLleganMensajes() {
        var espera = buzon.abrir(WA_ID).orElseThrow();

        long inicioNano = System.nanoTime();
        List<String> mensajes = buzon.esperar(espera);
        long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNano);

        assertThat(mensajes).isEmpty();
        // Con espera de 1 s, debe haber esperado aproximadamente 1000 ms (al menos 850 ms)
        assertThat(duracionMs)
                .as("debe agotar el plazo de espera inicial de 1000 ms")
                .isGreaterThanOrEqualTo(850L);
    }

    @Test
    void esperar_respetaSilencio_cuandoLlegaMensajeAntesDelTope() throws Exception {
        var espera = buzon.abrir(WA_ID).orElseThrow();

        // Enviar un mensaje a los 50 ms desde otro hilo
        CountDownLatch mensajeEnviado = new CountDownLatch(1);
        ejecutor.submit(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
                buzon.entregar(WA_ID, "Respuesta rápida");
                mensajeEnviado.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long inicioNano = System.nanoTime();
        List<String> mensajes = buzon.esperar(espera);
        long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNano);

        assertThat(mensajes).containsExactly("Respuesta rápida");
        // Debe despertar tras el silencio de 200 ms después de entregar el mensaje (~250-400 ms),
        // mucho antes del tope inicial de 1000 ms
        assertThat(duracionMs)
                .as("debe despertar tras vencer el silencio de 200 ms y no esperar el segundo entero")
                .isLessThan(800L);
    }

    @Test
    void cerrar_deEsperaAntigua_noEliminaNuevaEsperaDelMismoWaId() {
        var esperaAntigua = buzon.abrir(WA_ID).orElseThrow();
        buzon.cerrar(esperaAntigua);

        var esperaNueva = buzon.abrir(WA_ID).orElseThrow();

        // Cerrar nuevamente la espera antigua no debe retirar la nueva
        buzon.cerrar(esperaAntigua);

        assertThat(buzon.entregar(WA_ID, "Mensaje para la nueva"))
                .as("la nueva espera debe seguir abierta tras cerrar la antigua")
                .isTrue();

        assertThat(buzon.abrir(WA_ID))
                .as("no debe permitirse abrir otra mientras la nueva siga abierta")
                .isEmpty();

        buzon.cerrar(esperaNueva);
    }

    @Test
    void tomar_devuelveLoEntregadoSinEsperarYCierraEspera() {
        var espera = buzon.abrir(WA_ID).orElseThrow();
        buzon.entregar(WA_ID, "Mensaje 1");
        buzon.entregar(WA_ID, "Mensaje 2");

        long inicioNano = System.nanoTime();
        List<String> mensajes = buzon.tomar(espera);
        long duracionMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNano);

        assertThat(mensajes).containsExactly("Mensaje 1", "Mensaje 2");
        assertThat(duracionMs).as("tomar debe responder de inmediato sin esperar silencio").isLessThan(100L);

        // La espera queda cerrada
        assertThat(buzon.entregar(WA_ID, "Mensaje tardío")).isFalse();
    }

    @Test
    void entregar_trasCierreDeEspera_devuelveFalse() {
        var espera = buzon.abrir(WA_ID).orElseThrow();
        buzon.cerrar(espera);

        boolean resultado = buzon.entregar(WA_ID, "Mensaje posterior al cierre");

        assertThat(resultado).isFalse();
    }

    @Test
    void esperar_interrumpido_devuelveMensajesAceptadosYConservaMarcaDeInterrupcion() throws Exception {
        var espera = buzon.abrir(WA_ID).orElseThrow();
        buzon.entregar(WA_ID, "Mensaje antes de interrupción");

        AtomicBoolean interrupcionConservada = new AtomicBoolean(false);
        CountDownLatch hiloIniciado = new CountDownLatch(1);
        CountDownLatch hiloTerminado = new CountDownLatch(1);

        Thread hilo = new Thread(() -> {
            hiloIniciado.countDown();
            List<String> res = buzon.esperar(espera);
            if (Thread.currentThread().isInterrupted() && res.size() == 1) {
                interrupcionConservada.set(true);
            }
            hiloTerminado.countDown();
        });

        hilo.start();
        hiloIniciado.await(1, TimeUnit.SECONDS);
        TimeUnit.MILLISECONDS.sleep(40);
        hilo.interrupt();

        assertThat(hiloTerminado.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupcionConservada.get())
                .as("debe conservar la interrupción del hilo y devolver lo ya aceptado")
                .isTrue();
    }
}
