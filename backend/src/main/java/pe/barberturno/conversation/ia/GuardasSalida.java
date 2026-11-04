package pe.barberturno.conversation.ia;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Red de seguridad sobre el texto del modelo antes de enviarlo (17-v2-12 §5; V2-RN-11/12). Determinista, sin
 * estado ni acceso a datos. Implementación: Gemini (GI).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class GuardasSalida {
    /** Constructor sin dependencias, usado por Spring y por las pruebas. */
    public GuardasSalida() { }

    /**
     * Revisa el texto con las reglas de 17-v2-12 §5, en este orden: vacío, longitud, datos prohibidos,
     * afirmaciones de operación y horarios.
     * @param texto texto del modelo
     * @param franjas franjas ofrecidas en este turno (las únicas horas y fechas admitidas, como combinación)
     * @param ahora fecha y hora actuales en Lima, para resolver «hoy», «mañana» o «el viernes»
     * @return texto aceptado, o el motivo de la sustitución
     */
    public ResultadoGuardas revisar(String texto, List<FranjaOfrecida> franjas, ZonedDateTime ahora) {
        throw new UnsupportedOperationException("Pendiente del bloque GI.");
    }
}
