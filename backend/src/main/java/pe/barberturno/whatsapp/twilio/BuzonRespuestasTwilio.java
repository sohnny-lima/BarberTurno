package pe.barberturno.whatsapp.twilio;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Recoge hasta cinco respuestas de la outbox para un webhook por persona (V2-DA-104).
 * Entrega, vencimiento y cierre comparten el monitor de la espera: una entrega aceptada queda
 * en la instantánea devuelta, y una entrega posterior al cierre debe seguir por REST.
 * Los plazos se miden con tiempo monotónico desde la apertura, con tope total de doce segundos.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class BuzonRespuestasTwilio {
    private static final int LIMITE_MENSAJES = 5;
    private static final long TOPE_NANOS = TimeUnit.SECONDS.toNanos(12);
    private final ConcurrentHashMap<String, Espera> esperas = new ConcurrentHashMap<>();
    private final long esperaNanos;
    private final long silencioNanos;

    /**
     * Prepara los plazos ya validados al arrancar, independientes del reloj de negocio.
     * @param propiedades espera inicial y silencio máximo entre respuestas
     */
    public BuzonRespuestasTwilio(TwilioPropiedades propiedades) {
        esperaNanos = propiedades.respuestaEspera().toNanos();
        silencioNanos = propiedades.respuestaSilencio().toNanos();
    }

    /**
     * Reserva la espera antes de insertar el evento, sin sustituir la de otro webhook.
     * @param waId identidad numérica de la persona autenticada por Twilio
     * @return espera propia, o vacío si otro webhook de esa persona conserva la suya
     */
    public Optional<Espera> abrir(String waId) {
        Espera espera = new Espera(waId);
        return esperas.putIfAbsent(waId, espera) == null ? Optional.of(espera) : Optional.empty();
    }

    /**
     * Añade texto en orden de aceptación sin esperar el intervalo REST ni al hilo del webhook.
     * @param waId destinatario de la outbox
     * @param texto mensaje convertido y validado por el cliente de Twilio
     * @return true solo si la espera abierta admite el mensaje dentro del plazo y del cupo
     */
    public boolean entregar(String waId, String texto) {
        Espera espera = esperas.get(waId);
        if (espera == null) return false;
        synchronized (espera) {
            if (!espera.abierta || espera.mensajes.size() >= LIMITE_MENSAJES) return false;
            long ahora = System.nanoTime();
            if (restante(espera, ahora) <= 0) {
                cerrar(espera);
                return false;
            }
            espera.mensajes.add(texto);
            espera.ultimoNano = ahora;
            espera.notifyAll();
            return true;
        }
    }

    /**
     * Espera el primer mensaje y los siguientes hasta el silencio, el tope total o el cupo.
     * Cierra atómicamente antes de devolver una copia en orden; al interrumpirse conserva
     * la interrupción y devuelve los mensajes ya aceptados sin seguir esperando.
     * @param espera reserva obtenida por el webhook, que mantiene su propiedad hasta cerrarla
     * @return textos aceptados, en orden de entrega, como lista inmutable
     */
    public List<String> esperar(Espera espera) {
        synchronized (espera) {
            try {
                while (espera.abierta && espera.mensajes.size() < LIMITE_MENSAJES) {
                    long restante = restante(espera, System.nanoTime());
                    if (restante <= 0) break;
                    try { TimeUnit.NANOSECONDS.timedWait(espera, restante); }
                    catch (InterruptedException interrumpido) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return List.copyOf(espera.mensajes);
            } finally {
                cerrar(espera);
            }
        }
    }

    /**
     * Cierra sin esperar y devuelve lo ya aceptado: un webhook sin eventos nuevos no debe descartar
     * respuestas que el enviador ya dio por entregadas durante la ventana previa a la recepción.
     * @param espera reserva propia del webhook
     * @return textos aceptados hasta el cierre, en orden de entrega, como lista inmutable
     */
    public List<String> tomar(Espera espera) {
        synchronized (espera) {
            try { return List.copyOf(espera.mensajes); }
            finally { cerrar(espera); }
        }
    }

    /**
     * Libera solo la reserva propia; repetir el cierre no elimina una espera posterior.
     * @param espera reserva del webhook, incluso si no hubo eventos nuevos o falló la recepción
     */
    public void cerrar(Espera espera) {
        synchronized (espera) {
            espera.abierta = false;
            esperas.remove(espera.waId, espera);
            espera.notifyAll();
        }
    }

    private long restante(Espera espera, long ahora) {
        long total = TOPE_NANOS - (ahora - espera.inicioNano);
        long fase = espera.mensajes.isEmpty() ? esperaNanos - (ahora - espera.inicioNano)
                : silencioNanos - (ahora - espera.ultimoNano);
        return Math.min(total, fase);
    }

    /**
     * Reserva opaca de un webhook; sus datos solo se consultan bajo su monitor y no se registran.
     * @author Sohnny Walter Lima Infanzón
     * @version 2.0
     */
    public static final class Espera {
        private final String waId;
        private final long inicioNano = System.nanoTime();
        private final List<String> mensajes = new ArrayList<>(LIMITE_MENSAJES);
        private long ultimoNano;
        private boolean abierta = true;

        private Espera(String waId) { this.waId = waId; }
    }
}
