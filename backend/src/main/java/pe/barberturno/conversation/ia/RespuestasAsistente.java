package pe.barberturno.conversation.ia;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Composición determinista de lo que recibe el cliente al cerrar un turno de IA (17-v2-12 §13.3; V2-DA-86): menús y
 * textos fijos de V2-10 con datos del servidor, con los mismos códigos de selección que el flujo guiado. Ningún
 * parámetro procede del texto del modelo.
 * <p><strong>Esqueleto del contrato:</strong> el cuerpo lo implementa Gemini en el bloque RD-1.</p>
 * @author Sohnny Walter Lima Infanzón
 * @version 0.1
 */
@Component
public class RespuestasAsistente {

    /** Constructor usado por Spring y por las pruebas. */
    public RespuestasAsistente() { }

    /**
     * Mensaje para una decisión validada sin efecto.
     * @param decision decisión validada y degradada
     * @param datos datos del servidor bajo RLS
     * @return mensaje para el cliente
     * @throws UnsupportedOperationException mientras sea un esqueleto
     */
    public MensajeAsistente responder(DecisionRespuesta decision, DatosRespuesta datos) {
        throw new UnsupportedOperationException("Pendiente del bloque RD-1.");
    }

    /**
     * Lista de franjas del turno con un texto fijo del servidor.
     * @param franjas franjas del turno, en orden (como máximo se muestran 10)
     * @param hoy fecha actual en Lima
     * @return mensaje interactivo
     * @throws UnsupportedOperationException mientras sea un esqueleto
     */
    public MensajeAsistente franjas(List<FranjaOfrecida> franjas, LocalDate hoy) {
        throw new UnsupportedOperationException("Pendiente del bloque RD-1.");
    }

    /**
     * Menú principal con el texto fijo del motivo.
     * @param motivo motivo del cierre
     * @return mensaje interactivo
     * @throws UnsupportedOperationException mientras sea un esqueleto
     */
    public MensajeAsistente menu(MotivoMenu motivo) {
        throw new UnsupportedOperationException("Pendiente del bloque RD-1.");
    }
}
