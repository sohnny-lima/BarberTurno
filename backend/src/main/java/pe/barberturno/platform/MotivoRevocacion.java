package pe.barberturno.platform;

/**
 * Causa local de pérdida de acceso; determina la vía autorizada de restablecimiento (V2-DA-37).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public enum MotivoRevocacion {
    /** Baja del perfil profesional, reversible al reactivarlo. */
    PERFIL_DESACTIVADO,
    /** Salida voluntaria del negocio. */
    SALIDA,
    /** Retirada del acceso por la gestión del negocio. */
    DECISION_GESTOR,
    /** Retirada del acceso por la administración de plataforma. */
    PLATAFORMA
}
