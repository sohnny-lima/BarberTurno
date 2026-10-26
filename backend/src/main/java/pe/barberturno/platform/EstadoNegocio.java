package pe.barberturno.platform;

/**
 * Disponibilidad administrativa del negocio según V2-DA-38.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public enum EstadoNegocio {
    /** Admite las operaciones autorizadas de negocio. */
    ACTIVO,
    /** Oculta rutas públicas y limita al personal a lecturas. */
    SUSPENDIDO
}
