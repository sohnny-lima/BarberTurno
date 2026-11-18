package pe.barberturno.conversation.ia;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Respuesta de una llamada al modelo (17-v2-12 §4.1).
 * @param texto texto para el cliente, si lo hubo (pasa por las guardas antes de enviarse)
 * @param llamadas herramientas pedidas, en orden
 * @param continuacion contenido opaco que se devolverá en la llamada siguiente
 * @param uso tokens y duración de la llamada
 * @param parada motivo de parada del proveedor (p. ej. {@code end_turn}, {@code tool_use})
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record RespuestaModelo(Optional<String> texto, List<LlamadaHerramienta> llamadas,
        ContinuacionProveedor continuacion, UsoModelo uso, String parada) {
    /**
     * Valida y copia.
     * @param texto texto, si lo hubo
     * @param llamadas herramientas pedidas
     * @param continuacion contenido opaco
     * @param uso tokens y duración
     * @param parada motivo de parada
     * @throws NullPointerException si falta algún valor
     */
    public RespuestaModelo {
        Objects.requireNonNull(texto, "texto");
        llamadas = List.copyOf(llamadas);
        Objects.requireNonNull(continuacion, "continuacion");
        Objects.requireNonNull(uso, "uso");
        Objects.requireNonNull(parada, "parada");
    }
}
