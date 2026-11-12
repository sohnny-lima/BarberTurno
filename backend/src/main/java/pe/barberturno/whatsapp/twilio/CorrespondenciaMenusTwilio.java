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
 * Conserva el último menú por wa_id en esta instancia durante 24 h (V2-DA-100).
 * La elección válida consume el menú de forma atómica; un número fuera de rango no lo consume.
 * Reiniciar pierde las correspondencias y la persona debe escribir «menú».
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Component
@Profile("twilio")
public class CorrespondenciaMenusTwilio {
    private final ConcurrentHashMap<String, Menu> menus = new ConcurrentHashMap<>();
    private final Map<Entrada, Traduccion> traducciones = new HashMap<>();
    private final Clock reloj;

    /**
     * Usa el mismo reloj de negocio para caducidad y recepción.
     * @param reloj reloj inyectado, sustituible en pruebas
     */
    public CorrespondenciaMenusTwilio(Clock reloj) { this.reloj = reloj; }

    /**
     * Sustituye el menú de una persona; un texto posterior no invoca este método.
     * @param waId destinatario, solo dígitos
     * @param opciones correspondencia ordenada desde la opción uno
     */
    public void guardar(String waId, List<Seleccion> opciones) {
        Instant ahora = reloj.instant();
        menus.entrySet().removeIf(entrada -> !ahora.isBefore(entrada.getValue().caduca()));
        menus.put(waId, new Menu(List.copyOf(opciones), ahora.plus(Duration.ofHours(24))));
    }

    /**
     * Traduce y consume solo una elección numérica de un menú vigente.
     * @param waId remitente de la entrada firmada
     * @param texto Body recibido, con posibles espacios exteriores
     * @param messageSid clave de la entrada para que un reintento no consuma un menú posterior
     * @return opción elegida, o vacío si caducó, no existe o el texto no corresponde a una opción
     */
    public synchronized Optional<Seleccion> consumir(String waId, String texto, String messageSid) {
        String numero = texto.strip();
        if (!numero.matches("[0-9]+")) return Optional.empty();
        Instant ahora = reloj.instant();
        traducciones.entrySet().removeIf(entrada -> !ahora.isBefore(entrada.getValue().caduca()));
        Entrada entrada = new Entrada(waId, messageSid);
        Traduccion anterior = traducciones.get(entrada);
        if (anterior != null) return anterior.seleccion();
        int indice;
        try { indice = Integer.parseInt(numero) - 1; }
        catch (NumberFormatException fueraDeRango) { return Optional.empty(); }
        Seleccion[] elegida = new Seleccion[1];
        menus.computeIfPresent(waId, (clave, menu) -> {
            if (!reloj.instant().isBefore(menu.caduca())) return null;
            if (indice < 0 || indice >= menu.opciones().size()) return menu;
            elegida[0] = menu.opciones().get(indice);
            return null;
        });
        Optional<Seleccion> seleccion = Optional.ofNullable(elegida[0]);
        traducciones.put(entrada, new Traduccion(seleccion, ahora.plus(Duration.ofHours(24))));
        return seleccion;
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

    private record Menu(List<Seleccion> opciones, Instant caduca) { }
    private record Entrada(String waId, String messageSid) { }
    private record Traduccion(Optional<Seleccion> seleccion, Instant caduca) { }
}
