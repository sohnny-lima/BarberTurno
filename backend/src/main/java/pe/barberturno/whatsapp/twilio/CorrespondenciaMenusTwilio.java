package pe.barberturno.whatsapp.twilio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Conserva el último menú por número y wa_id en esta instancia durante 24 h (V2-DA-100).
 * La elección válida fija atómicamente la opción y espera su respuesta; conserva el menú para
 * poder repetirlo ante mensajes seguidos sin aplicar una selección a otro paso.
 * Reiniciar pierde las correspondencias y la persona debe escribir «menú».
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class CorrespondenciaMenusTwilio {
    private static final String AVISO_SEGUIDOS = "Recibí varios mensajes seguidos; para evitar errores, elige de nuevo:";
    private static final Duration CADUCIDAD_PENDIENTE = Duration.ofSeconds(20);
    private final ConcurrentHashMap<Persona, Menu> menus = new ConcurrentHashMap<>();
    private final Map<Entrada, Traduccion> traducciones = new HashMap<>();
    private final Map<Persona, Instant> pendientes = new HashMap<>();
    private final Clock reloj;
    private final long reaccionNanos;
    private final LongSupplier nanos;

    /**
     * Usa el reloj de negocio para caducidad y tiempo monotónico para la reacción tras publicar.
     * @param reloj reloj inyectado, sustituible en pruebas
     * @param propiedades mínimo de reacción validado al arrancar
     */
    @Autowired
    public CorrespondenciaMenusTwilio(Clock reloj, TwilioPropiedades propiedades) {
        this(reloj, propiedades, System::nanoTime);
    }

    CorrespondenciaMenusTwilio(Clock reloj, TwilioPropiedades propiedades, LongSupplier nanos) {
        this.reloj = reloj;
        this.reaccionNanos = propiedades.reaccionMinima().toNanos();
        this.nanos = nanos;
    }

    long ahoraNano() { return nanos.getAsLong(); }

    /**
     * Libera la espera de una entrada cuando se entrega una respuesta de texto sin opciones.
     * No altera el menú ni la decisión idempotente por MessageSid.
     * @param numero canal autenticado del negocio
     * @param waId destinatario de la respuesta entregada
     */
    public synchronized void entregadoSinOpciones(String numero, String waId) {
        pendientes.remove(new Persona(numero, waId));
    }

    /**
     * Sustituye el menú de una persona; un texto posterior no invoca este método.
     * @param numero dígitos del número del negocio
     * @param texto texto completo que recibió la persona
     * @param waId destinatario, solo dígitos
     * @param opciones correspondencia ordenada desde la opción uno
     */
    public synchronized void guardar(String numero, String waId, List<Seleccion> opciones, String texto) {
        Instant ahora = reloj.instant();
        menus.entrySet().removeIf(entrada -> !ahora.isBefore(entrada.getValue().caduca()));
        Persona persona = new Persona(numero, waId);
        menus.put(persona, new Menu(List.copyOf(opciones), texto, ahora.plus(Duration.ofHours(24)), ahoraNano()));
        pendientes.remove(persona);
    }

    /**
     * Traduce una elección numérica del menú vigente y espera la respuesta antes de admitir otra.
     * @param numero dígitos del número del negocio
     * @param waId remitente de la entrada firmada
     * @param texto Body recibido, con posibles espacios exteriores
     * @param messageSid clave de la entrada para que un reintento no consuma un menú posterior
     * @return opción elegida, o vacío si caducó, no existe o el texto no corresponde a una opción
     */
    public synchronized Optional<Seleccion> consumir(String numero, String waId, String texto, String messageSid) {
        return traducir(numero, waId, texto, messageSid, false, ahoraNano()).seleccion();
    }

    /**
     * Reutiliza por MessageSid el aviso inmediato sin consumir el menú ni generar un evento.
     * @param numero dígitos del número del negocio
     * @param waId remitente autenticado
     * @param texto cuerpo del formulario firmado
     * @param messageSid identificador idempotente de la entrada
     * @return aviso con el menú íntegro, o vacío si la entrada sigue el flujo normal
     */
    public synchronized Optional<String> respuestaInvalida(String numero, String waId, String texto, String messageSid) {
        return traducir(numero, waId, texto, messageSid, false, ahoraNano()).aviso();
    }

    /**
     * Fija la elección al momento de recepción, antes de que otro hilo publique un menú posterior.
     * Un selector mientras se espera la respuesta anterior solo obtiene un aviso, sin evento ni efecto.
     * Los reintentos conservan la decisión original por MessageSid, incluso después de otro menú.
     * @param numero canal autenticado
     * @param waId identidad del remitente en ese canal
     * @param texto número escrito, texto libre o identificador de botón
     * @param messageSid clave idempotente de Twilio
     * @param boton verdadero si el valor procede de ButtonPayload
     * @param recibidoNano instante monotónico capturado al empezar el webhook
     * @return selección y aviso excluyentes, o ambos vacíos para texto libre
     */
    public synchronized Decision decidir(String numero, String waId, String texto, String messageSid,
            boolean boton, long recibidoNano) {
        var traduccion = traducir(numero, waId, texto, messageSid, boton, recibidoNano);
        return new Decision(traduccion.seleccion(), traduccion.aviso());
    }

    private Traduccion traducir(String numeroCanal, String waId, String texto, String messageSid,
            boolean boton, long recibidoNano) {
        Instant ahora = reloj.instant();
        Traduccion vacia = new Traduccion(Optional.empty(), Optional.empty(), ahora.plus(Duration.ofHours(24)));
        String numero = texto.strip();
        traducciones.entrySet().removeIf(e -> !ahora.isBefore(e.getValue().caduca()));
        pendientes.entrySet().removeIf(e -> !ahora.isBefore(e.getValue()));
        Entrada entrada = new Entrada(new Persona(numeroCanal, waId), messageSid);
        Traduccion anterior = traducciones.get(entrada);
        if (anterior != null) return anterior;
        boolean selector = boton || numero.matches("[0-9]+") && !numero.matches("0+");
        if (!selector) {
            pendientes.put(entrada.persona(), ahora.plus(CADUCIDAD_PENDIENTE));
            traducciones.put(entrada, vacia);
            return vacia;
        }
        Menu menu = menus.get(entrada.persona());
        Traduccion resultado = vacia;
        if (pendientes.containsKey(entrada.persona())
                || menu != null && recibidoNano - menu.publicadoNano() < reaccionNanos) {
            String actual = menu != null && ahora.isBefore(menu.caduca()) ? menu.texto()
                    : "Escribe «menu» para mostrar el menú principal.";
            resultado = new Traduccion(Optional.empty(), Optional.of(AVISO_SEGUIDOS + "\n\n" + actual), vacia.caduca());
            traducciones.put(entrada, resultado);
            return resultado;
        }
        if (menu != null && ahora.isBefore(menu.caduca())) {
            int indice;
            try { indice = boton ? -1 : Integer.parseInt(numero) - 1; }
            catch (NumberFormatException desbordado) { indice = -1; }
            if (boton) {
                for (int i = 0; i < menu.opciones().size(); i++) {
                    if (menu.opciones().get(i).id().equals(numero)) { indice = i; break; }
                }
            }
            if (indice >= 0 && indice < menu.opciones().size()) {
                resultado = new Traduccion(Optional.of(menu.opciones().get(indice)), Optional.empty(), vacia.caduca());
                pendientes.put(entrada.persona(), ahora.plus(CADUCIDAD_PENDIENTE));
            } else {
                resultado = new Traduccion(Optional.empty(), Optional.of(
                        (menu.opciones().size() == 1 ? "Solo hay una opción en este menú. Responde con 1"
                                : "Esa opción no está en el menú. Responde con un número del 1 al " + menu.opciones().size())
                        + " o escribe «menu» para volver al inicio.\n\n" + menu.texto()), vacia.caduca());
            }
        } else {
            menus.remove(entrada.persona());
            if (boton) resultado = new Traduccion(Optional.empty(), Optional.of(
                    "Ese botón ya no está disponible. Escribe «menu» para mostrar el menú principal."), vacia.caduca());
        }
        traducciones.put(entrada, resultado);
        return resultado;
    }

    /**
     * Identificador original de Meta y título visible de una opción numerada.
     * @param tipo list_reply o button_reply, según el interactivo original
     * @param id identificador de selección que interpreta la conversación existente
     * @param titulo título íntegro enviado a la persona
     * @author Sohnny Walter Lima Infanzón
     * @version 2.0
     */
    public record Seleccion(String tipo, String id, String titulo) { }

    /**
     * Decisión inmutable reutilizable entre la validación y la traducción de un mismo webhook.
     * @param seleccion opción exacta del menú publicado al recibir, si se admite
     * @param aviso respuesta sin efectos, si se rechaza la selección
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record Decision(Optional<Seleccion> seleccion, Optional<String> aviso) { }

    private record Menu(List<Seleccion> opciones, String texto, Instant caduca, long publicadoNano) { }
    private record Persona(String numero, String waId) { }
    private record Entrada(Persona persona, String messageSid) { }
    private record Traduccion(Optional<Seleccion> seleccion, Optional<String> aviso, Instant caduca) { }
}
