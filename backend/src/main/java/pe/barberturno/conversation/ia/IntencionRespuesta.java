package pe.barberturno.conversation.ia;

/**
 * Intención con la que el modelo cierra un turno sin efecto (17-v2-12 §13.1; V2-DA-86). El modelo solo elige el
 * valor; el mensaje lo compone el servidor con {@link RespuestasAsistente}, así que ninguna intención puede afirmar
 * una reserva, un horario ni un precio que el servidor no haya acreditado.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum IntencionRespuesta {
    /** Falta un dato para avanzar; exige {@link DatoFaltante}. */
    PEDIR_DATO,
    /** Mostrar el catálogo de servicios con los precios del servidor. */
    MOSTRAR_SERVICIOS,
    /** Mostrar los profesionales del servicio elegido. */
    MOSTRAR_PROFESIONALES,
    /** Mostrar las citas futuras de la ficha de la conversación. */
    MOSTRAR_CITAS,
    /** La última búsqueda válida del turno no devolvió franjas. */
    SIN_DISPONIBILIDAD,
    /** La petición no trata de citas de la barbería. */
    FUERA_DE_ALCANCE,
    /** Saludo o mensaje sin petición concreta. */
    SALUDO
}
