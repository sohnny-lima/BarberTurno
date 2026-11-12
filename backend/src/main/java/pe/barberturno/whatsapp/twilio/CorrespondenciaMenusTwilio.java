package pe.barberturno.whatsapp.twilio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Conserva el último menú por número y wa_id en esta instancia durante 24 h (V2-DA-100).
 * La elección válida consume el menú de forma atómica; un número fuera de rango no lo consume.
 * Reiniciar pierde las correspondencias y la persona debe escribir «menú».
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class CorrespondenciaMenusTwilio {
    private final ConcurrentHashMap<Persona, Menu> menus = new ConcurrentHashMap<>();
    private final Map<Entrada, Traduccion> traducciones = new HashMap<>();
    private final Clock reloj;

    /**
     * Usa el mismo reloj de negocio para caducidad y recepción.
     * @param reloj reloj inyectado, sustituible en pruebas
     */
    public CorrespondenciaMenusTwilio(Clock reloj) { this.reloj = reloj; }

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
        menus.put(new Persona(numero, waId), new Menu(List.copyOf(opciones), texto, ahora.plus(Duration.ofHours(24))));
    }

    /**
     * Traduce y consume solo una elección numérica de un menú vigente.
     * @param numero dígitos del número del negocio
     * @param waId remitente de la entrada firmada
     * @param texto Body recibido, con posibles espacios exteriores
     * @param messageSid clave de la entrada para que un reintento no consuma un menú posterior
     * @return opción elegida, o vacío si caducó, no existe o el texto no corresponde a una opción
     */
    public synchronized Optional<Seleccion> consumir(String numero, String waId, String texto, String messageSid) {
        return traducir(numero, waId, texto, messageSid).seleccion();
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
        return traducir(numero, waId, texto, messageSid).aviso();
    }

    private Traduccion traducir(String numeroCanal, String waId, String texto, String messageSid) {
        Instant ahora = reloj.instant();
        Traduccion vacia = new Traduccion(Optional.empty(), Optional.empty(), ahora.plus(Duration.ofHours(24)));
        String numero = texto.strip();
        if (!numero.matches("[0-9]+") || numero.matches("0+")) return vacia;
        traducciones.entrySet().removeIf(e -> !ahora.isBefore(e.getValue().caduca()));
        Entrada entrada = new Entrada(new Persona(numeroCanal, waId), messageSid);
        Traduccion anterior = traducciones.get(entrada);
        if (anterior != null) return anterior;
        Menu menu = menus.get(entrada.persona());
        Traduccion resultado = vacia;
        if (menu != null && ahora.isBefore(menu.caduca())) {
            int indice;
            try { indice = Integer.parseInt(numero) - 1; }
            catch (NumberFormatException desbordado) { indice = -1; }
            if (indice >= 0 && indice < menu.opciones().size()) {
                resultado = new Traduccion(Optional.of(menu.opciones().get(indice)), Optional.empty(), vacia.caduca());
                menus.remove(entrada.persona());
            } else {
                resultado = new Traduccion(Optional.empty(), Optional.of(
                        "Esa opción no está en el menú. Responde con un número del 1 al " + menu.opciones().size()
                        + " o escribe «menu» para volver al inicio.\n\n" + menu.texto()), vacia.caduca());
            }
        } else menus.remove(entrada.persona());
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

    private record Menu(List<Seleccion> opciones, String texto, Instant caduca) { }
    private record Persona(String numero, String waId) { }
    private record Entrada(Persona persona, String messageSid) { }
    private record Traduccion(Optional<Seleccion> seleccion, Optional<String> aviso, Instant caduca) { }
}
