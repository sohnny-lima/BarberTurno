package pe.barberturno.whatsapp;

import java.util.Map;
import java.util.OptionalLong;
import tools.jackson.databind.JsonNode;

/**
 * Outbox transaccional de WhatsApp (V2-DA-08, V2-DA-57): guarda un mensaje {@code SALIENTE}/{@code PENDIENTE}
 * en la misma transacción que el cambio que lo origina, de modo que, si esa transacción se revierte, el
 * mensaje no existe. No comprueba el canal, el estado del negocio ni la ventana de 24 h: lo hace el
 * enviador, para que un fallo de entrega nunca deshaga la operación (12-v2-09-whatsapp-salida.md §4.1).
 * <p>Todas las operaciones exigen una transacción ya abierta ({@code Propagation.MANDATORY}) con el
 * contexto del negocio, y devuelven el id del mensaje.</p>
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface SalidaWhatsApp {
    /**
     * Encola un texto libre, que solo se enviará si la ventana de atención está abierta al enviarlo.
     * @param conversacionId conversación del negocio actual
     * @param texto de 1 a 4096 caracteres, no en blanco
     * @param origen {@code ASISTENTE}, {@code SISTEMA} o {@code PERSONA}
     * @param reservaId reserva del negocio a la que se refiere, o vacío
     * @return id del mensaje encolado
     * @throws pe.barberturno.common.error.NegocioException NO_ENCONTRADO si la conversación o la reserva no
     * son del negocio; VALIDACION si el texto o el origen no son válidos
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción
     */
    long encolarTexto(long conversacionId, String texto, OrigenMensaje origen, OptionalLong reservaId);

    /**
     * Encola un mensaje interactivo (botones o lista), que solo se enviará con la ventana abierta.
     * @param conversacionId conversación del negocio actual
     * @param interactivo objeto {@code interactive} de la Graph API, con {@code type} {@code button} o
     * {@code list} y {@code body.text}; como máximo 8 KiB serializado
     * @param origen {@code ASISTENTE}, {@code SISTEMA} o {@code PERSONA}
     * @param reservaId reserva del negocio a la que se refiere, o vacío
     * @return id del mensaje encolado
     * @throws pe.barberturno.common.error.NegocioException NO_ENCONTRADO si la conversación o la reserva no
     * son del negocio; VALIDACION si el objeto o el origen no son válidos
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción
     */
    long encolarInteractivo(long conversacionId, JsonNode interactivo, OrigenMensaje origen, OptionalLong reservaId);

    /**
     * Encola una plantilla <em>utility</em> que se envía siempre como plantilla, con la ventana abierta o
     * cerrada; el origen es {@code SISTEMA}.
     * @param conversacionId conversación del negocio actual
     * @param plantilla plantilla del catálogo
     * @param parametros valores por nombre de parámetro, validados con {@link PlantillaUtility#validar(Map)}
     * @param reservaId reserva del negocio a la que se refiere, o vacío
     * @return id del mensaje encolado
     * @throws pe.barberturno.common.error.NegocioException NO_ENCONTRADO si la conversación o la reserva no
     * son del negocio; VALIDACION si los parámetros no son válidos
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción
     */
    long encolarPlantilla(long conversacionId, PlantillaUtility plantilla, Map<String, String> parametros,
            OptionalLong reservaId);

    /**
     * Encola un aviso al cliente que el enviador decide al enviarlo: como texto de servicio si la ventana
     * está abierta y como plantilla si está cerrada; el origen es {@code SISTEMA}.
     * @param conversacionId conversación del negocio actual
     * @param plantilla plantilla del catálogo cuyo texto se usa en ambos casos
     * @param parametros valores por nombre de parámetro, validados con {@link PlantillaUtility#validar(Map)}
     * @param reservaId reserva del negocio a la que se refiere, o vacío
     * @return id del mensaje encolado
     * @throws pe.barberturno.common.error.NegocioException NO_ENCONTRADO si la conversación o la reserva no
     * son del negocio; VALIDACION si los parámetros no son válidos
     * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción
     */
    long encolarAviso(long conversacionId, PlantillaUtility plantilla, Map<String, String> parametros,
            OptionalLong reservaId);

    /**
     * Encola la respuesta de texto libre de un miembro del equipo desde la bandeja (V2-11, 16-v2-11 §3.2):
     * origen {@code PERSONA} y {@code enviado_por}, insertados a la vez. Mismas reglas de ventana y envío que
     * {@link #encolarTexto}, que rechaza el origen {@code PERSONA} sin actor. Implementación: Codex (X11); por
     * defecto no está disponible, para que los dobles de prueba de V2-09/V2-10 sigan compilando.
     * @param conversacionId conversación del negocio actual, en modo {@code HUMANO}
     * @param texto de 1 a 4096 caracteres, no en blanco
     * @param usuarioId miembro autorizado que responde
     * @return id del mensaje encolado
     * @throws pe.barberturno.common.error.NegocioException NO_ENCONTRADO si la conversación no es del negocio;
     * VALIDACION si el texto no es válido
     * @throws UnsupportedOperationException si la implementación no admite respuestas del personal
     */
    default long encolarRespuestaPersonal(long conversacionId, String texto, long usuarioId) {
        throw new UnsupportedOperationException("Respuesta del personal no disponible en esta salida.");
    }
}
