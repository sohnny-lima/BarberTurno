package pe.barberturno.conversation.ia;

import java.time.Instant;
import java.util.Objects;
import pe.barberturno.conversation.CodigoSeleccion;

/**
 * Franja libre devuelta por {@code buscar_disponibilidad} en un turno (17-v2-12 §4.3 y §5). El servidor la muestra
 * como fila de una lista cuyo identificador es la selección determinista de V2-10, así que al pulsarla el cliente
 * sigue el camino sin IA.
 * @param alias alias del turno ({@code f1}…), lo único que ve el modelo
 * @param inicio inicio
 * @param fin fin
 * @param profesional nombre visible del profesional
 * @param seleccion selección de V2-10 ({@code Franja} o {@code FranjaCambio}) para el botón de la fila
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record FranjaOfrecida(String alias, Instant inicio, Instant fin, String profesional, CodigoSeleccion seleccion) {
    /**
     * Valida.
     * @param alias alias del turno
     * @param inicio inicio
     * @param fin fin
     * @param profesional nombre visible
     * @param seleccion selección determinista
     * @throws NullPointerException si falta algún valor
     * @throws IllegalArgumentException si el fin no es posterior al inicio
     */
    public FranjaOfrecida {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        Objects.requireNonNull(profesional, "profesional");
        Objects.requireNonNull(seleccion, "seleccion");
        if (!fin.isAfter(inicio)) throw new IllegalArgumentException("Franja vacía.");
    }
}
