package pe.barberturno.conversation.ia;

/**
 * Puerto hacia el modelo de lenguaje (V2-DA-12; 17-v2-12 §4.1). El modelo interpreta y redacta; el servidor
 * consulta, valida, propone y ejecuta. Adaptadores: el falso programable de las pruebas y la evaluación, y el de
 * Anthropic (Codex). Una llamada por invocación: el bucle de herramientas lo lleva el turno.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ModeloConversacional {
    /**
     * Envía la conversación del turno y devuelve la respuesta del modelo (texto, llamadas a herramientas o ambos).
     * No registra contenido, argumentos ni la clave.
     * @param peticion instrucciones, contexto, mensajes del turno y herramientas disponibles
     * @return respuesta del modelo con su uso de tokens
     * @throws FalloModelo si el modelo no está configurado, no responde a tiempo, rechaza la petición o devuelve
     * una salida no utilizable; lleva el uso facturable si se conoce
     */
    RespuestaModelo responder(PeticionModelo peticion);
}
