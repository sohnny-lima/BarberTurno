package pe.barberturno.conversation.ia;

import java.util.List;
import java.util.Objects;

/**
 * Una llamada al modelo (17-v2-12 §4.1). El orden de los bloques es estable para la caché del proveedor:
 * herramientas e instrucciones fijas primero; el contexto variable y los mensajes después.
 * @param instrucciones texto fijo del sistema ({@link InstruccionesAsistente}), cacheable
 * @param contexto contexto mínimo del turno (17-v2-12 §7), ya saneado
 * @param mensajes mensajes del turno en orden, empezando por el del cliente
 * @param herramientas herramientas que el modelo puede pedir
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record PeticionModelo(String instrucciones, String contexto, List<MensajeModelo> mensajes,
        List<HerramientaIA> herramientas) {
    /**
     * Valida y copia las listas.
     * @param instrucciones texto fijo del sistema
     * @param contexto contexto del turno
     * @param mensajes mensajes del turno
     * @param herramientas herramientas disponibles
     * @throws NullPointerException si falta algún valor
     */
    public PeticionModelo {
        Objects.requireNonNull(instrucciones, "instrucciones");
        Objects.requireNonNull(contexto, "contexto");
        mensajes = List.copyOf(mensajes);
        herramientas = List.copyOf(herramientas);
    }
}
