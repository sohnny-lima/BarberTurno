package pe.barberturno.conversation.ia;

import java.util.Objects;
import java.util.Optional;

/**
 * Decisión final del turno ya validada y degradada por el servidor (17-v2-12 §13.2). No contiene texto del modelo.
 * @param intencion intención validada
 * @param falta dato que falta; presente si y solo si la intención es {@link IntencionRespuesta#PEDIR_DATO}
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record DecisionRespuesta(IntencionRespuesta intencion, Optional<DatoFaltante> falta) {
    /**
     * Valida la coherencia entre intención y dato faltante.
     * @param intencion intención validada
     * @param falta dato que falta
     * @throws NullPointerException si falta algún valor
     * @throws IllegalArgumentException si {@code PEDIR_DATO} no trae dato o si otra intención lo trae
     */
    public DecisionRespuesta {
        Objects.requireNonNull(intencion, "intencion");
        Objects.requireNonNull(falta, "falta");
        if ((intencion == IntencionRespuesta.PEDIR_DATO) != falta.isPresent())
            throw new IllegalArgumentException("Dato faltante incoherente con la intención.");
    }
}
