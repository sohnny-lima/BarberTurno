package pe.barberturno.conversation.ia;

/**
 * Límites de uso y costes de la IA (17-v2-12 §6; V2-RN-14). Implementación: Codex (XI), que también reserva y
 * liquida cada llamada.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface LimitesIA {
    /**
     * Comprueba si el texto libre de esta conversación puede ir a la IA. Exige la transacción de entrega con la
     * conversación bloqueada. Solo escribe el aviso diario {@code LIMITE_IA} al propietario cuando el presupuesto
     * mensual ya está agotado (g095-2), una vez al día y en esa misma transacción.
     * @param conversacionId conversación del negocio actual
     * @return disponibilidad
     */
    DisponibilidadIA disponibilidad(long conversacionId);
}
