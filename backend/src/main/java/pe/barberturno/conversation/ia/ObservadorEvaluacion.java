package pe.barberturno.conversation.ia;

import java.util.List;
import java.util.Optional;

/**
 * Observa un turno para la evaluación de conversaciones (V2-13; 17-v2-12 §4.4). En producción la implementación
 * no hace nada; la evaluación registra el texto del modelo <strong>antes</strong> de las guardas, las franjas, las
 * llamadas, el uso y el resultado. Nunca registra en los registros de la aplicación.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ObservadorEvaluacion {
    /**
     * Una llamada al modelo terminó.
     * @param turnoId turno
     * @param respuesta respuesta del modelo (texto previo a las guardas incluido)
     */
    default void llamada(long turnoId, RespuestaModelo respuesta) { }

    /**
     * Una herramienta se ejecutó.
     * @param turnoId turno
     * @param llamada llamada del modelo
     * @param resultado resultado devuelto al modelo
     * @param franjas franjas ofrecidas por esta llamada
     */
    default void herramienta(long turnoId, LlamadaHerramienta llamada, ResultadoHerramienta resultado,
            List<FranjaOfrecida> franjas) { }

    /**
     * El turno terminó.
     * @param turnoId turno
     * @param estado estado final ({@code HECHO}, {@code DESCARTADO} o {@code FALLIDO})
     * @param guardas resultado de las guardas, si hubo texto
     * @param efecto efecto aplicado, si lo hubo
     */
    default void fin(long turnoId, String estado, Optional<ResultadoGuardas> guardas, Optional<EfectoTurno> efecto) { }
}
