package pe.barberturno.conversation.ia;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * Respuestas que compone el servidor en un turno de IA (17-v2-12 §4.4 y §5): la lista de franjas de varios días y
 * los textos fijos. Respeta los límites de Meta de V2-10. Implementación: Gemini (GI).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class ComposicionTurnoIA {
    /** Constructor sin dependencias, usado por Spring y por las pruebas. */
    public ComposicionTurnoIA() { }

    /**
     * Lista interactiva de franjas agrupadas por día (fecha completa en Lima en el título de cada sección), con
     * como máximo 10 filas; el id de cada fila es la selección de V2-10 de la franja.
     * @param cuerpo texto del mensaje (el del modelo si pasó las guardas, o uno neutro)
     * @param franjas franjas del turno, en orden; si hay más de 10, solo las 10 primeras
     * @param hoy fecha de hoy en Lima
     * @return objeto interactivo de lista
     * @throws IllegalArgumentException si no hay franjas
     */
    public ObjectNode franjas(String cuerpo, List<FranjaOfrecida> franjas, LocalDate hoy) {
        throw new UnsupportedOperationException("Pendiente del bloque GI.");
    }

    /**
     * Texto neutro que acompaña a la composición cuando las guardas descartan el del modelo.
     * @return texto
     */
    public String neutro() {
        throw new UnsupportedOperationException("Pendiente del bloque GI.");
    }

    /**
     * El modelo preparó dos operaciones distintas: se pide al cliente que diga cuál quiere.
     * @return texto
     */
    public String aclararPropuestas() {
        throw new UnsupportedOperationException("Pendiente del bloque GI.");
    }

    /**
     * El turno falló: se ofrecen los menús.
     * @return texto
     */
    public String fallo() {
        throw new UnsupportedOperationException("Pendiente del bloque GI.");
    }
}
