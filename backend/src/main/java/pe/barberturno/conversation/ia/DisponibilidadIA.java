package pe.barberturno.conversation.ia;

/**
 * Si un texto libre puede ir a la IA (17-v2-12 §6).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum DisponibilidadIA {
    /** Configurada, habilitada y dentro de los límites. */
    DISPONIBLE,
    /** Sin clave o modelo, deshabilitada en la configuración o en el negocio. */
    DESACTIVADA,
    /** Demasiados mensajes entrantes en el último minuto en esta conversación. */
    LIMITE_MINUTO,
    /** Llamadas al modelo del día agotadas en esta conversación. */
    LIMITE_DIA,
    /** Presupuesto mensual del negocio agotado. */
    PRESUPUESTO
}
