package pe.barberturno.whatsapp.twilio;

import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Recoge hasta cinco respuestas de la outbox para un webhook por número y persona (V2-DA-104/107).
 * Retiene sintéticos diez minutos sin REST y antepone sus respuestas al próximo webhook (V2-DA-108).
 * Entrega, vencimiento y cierre comparten el monitor de la espera: una entrega aceptada queda
 * en la instantánea devuelta, y una entrega posterior al cierre sigue por REST o se retiene si el destino es sintético.
 * Los plazos se miden con tiempo monotónico desde la apertura, con tope total de doce segundos.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class BuzonRespuestasTwilio {
    private static final int LIMITE_MENSAJES = 5;
    private static final long TOPE_NANOS = TimeUnit.SECONDS.toNanos(12);
    private final ConcurrentHashMap<Persona, Espera> esperas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Persona, List<Retenida>> retenidas = new ConcurrentHashMap<>();
    private final Clock reloj;
    private final long esperaNanos;
    private final long silencioNanos;

    /**
     * Prepara los plazos ya validados al arrancar, independientes del reloj de negocio.
     * @param propiedades espera inicial y silencio máximo entre respuestas
     * @param reloj caducidad de las respuestas retenidas, sustituible en pruebas
     */
    public BuzonRespuestasTwilio(TwilioPropiedades propiedades, Clock reloj) {
        this.reloj = reloj;
        esperaNanos = propiedades.respuestaEspera().toNanos();
        silencioNanos = propiedades.respuestaSilencio().toNanos();
    }

    /**
     * Reserva la espera antes de insertar el evento, sin sustituir la de otro webhook.
     * @param numero dígitos del número del negocio
     * @param waId identidad numérica de la persona autenticada por Twilio
     * @return espera propia, o vacío si otro webhook de esa persona conserva la suya
     */
    public synchronized Optional<Espera> abrir(String numero, String waId) {
        Persona persona = new Persona(numero, waId);
        if (esperas.containsKey(persona)) return Optional.empty();
        Espera espera = new Espera(persona);
        purgar();
        List<Retenida> pendientes = retenidas.remove(persona);
        if (pendientes != null) {
            pendientes.forEach(r -> {
                espera.mensajes.add(r.texto());
                r.alEntregar().run();
            });
            espera.ultimoNano = System.nanoTime();
        }
        esperas.put(persona, espera);
        return Optional.of(espera);
    }

    /**
     * Añade texto en orden de aceptación sin esperar el intervalo REST ni al hilo del webhook.
     * @param numero dígitos del número del negocio
     * @param waId destinatario de la outbox
     * @param texto mensaje convertido y validado por el cliente de Twilio
     * @return true solo si la espera abierta admite el mensaje dentro del plazo y del cupo
     */
    public synchronized boolean entregar(String numero, String waId, String texto) {
        Espera espera = esperas.get(new Persona(numero, waId));
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
            esperas.remove(espera.persona, espera);
            espera.notifyAll();
        }
    }

    /**
     * Conserva una respuesta sintética sin REST; si se abrió una espera entre tanto, la entrega allí.
     * Preserva las cinco primeras y descarta las desbordadas sin registrar su contenido.
     * @param numero dígitos del número del negocio
     * @param waId destinatario sintético
     * @param texto respuesta validada
     * @param alEntregar instalación del menú solo cuando el texto se incorpora a un TwiML
     * @return verdadero si se entregó o retuvo; falso si se descartó por desbordamiento
     */
    public synchronized boolean retener(String numero, String waId, String texto, Runnable alEntregar) {
        if (entregar(numero, waId, texto)) {
            alEntregar.run();
            return true;
        }
        purgar();
        var lista = retenidas.computeIfAbsent(new Persona(numero, waId), clave -> new ArrayList<>());
        if (lista.size() >= LIMITE_MENSAJES) return false;
        lista.add(new Retenida(texto, reloj.instant().plus(Duration.ofMinutes(10)), alEntregar));
        return true;
    }

    /** Elimina texto caducado incluso si la persona nunca vuelve a escribir; no realiza envíos. */
    @Scheduled(fixedDelay = 60000)
    public synchronized void purgarCaducadas() {
        purgar();
    }

    private void purgar() {
        Instant ahora = reloj.instant();
        retenidas.entrySet().removeIf(e -> {
            e.getValue().removeIf(r -> !ahora.isBefore(r.caduca()));
            return e.getValue().isEmpty();
        });
    }

    private record Persona(String numero, String waId) { }
    private record Retenida(String texto, Instant caduca, Runnable alEntregar) { }

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
        private final Persona persona;
        private final long inicioNano = System.nanoTime();
        private final List<String> mensajes = new ArrayList<>(LIMITE_MENSAJES);
        private long ultimoNano;
        private boolean abierta = true;

        private Espera(Persona persona) { this.persona = persona; }
    }
}
