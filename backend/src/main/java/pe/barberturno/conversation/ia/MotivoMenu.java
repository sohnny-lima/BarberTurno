package pe.barberturno.conversation.ia;

/**
 * Motivo por el que el turno termina con el menú principal y un texto fijo del servidor (17-v2-12 §13.3).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum MotivoMenu {
    /** Sin efecto ni decisión válida del modelo. */
    NEUTRO,
    /** El turno falló (proveedor, límite o tiempo agotado). */
    FALLO,
    /** El modelo preparó dos operaciones distintas: se pide elegir una sola. */
    VARIAS_PROPUESTAS,
    /** La propuesta dejó de estar disponible al crearla. */
    NO_DISPONIBLE
}
