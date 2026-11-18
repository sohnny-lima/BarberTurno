package pe.barberturno.conversation.ia;

/**
 * Contenido del asistente tal como lo devolvió el proveedor (bloques en su orden, pensamiento incluido), que debe
 * volver intacto en la llamada siguiente del mismo turno (17-v2-12 §4.1; consulta 127-4). Es opaco para el
 * turno: solo el adaptador que lo creó lo entiende. Vive solo durante el turno; nunca se registra ni se persiste.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public interface ContinuacionProveedor {
    /** Continuación vacía: la del adaptador falso o la de un proveedor sin estado que devolver. */
    ContinuacionProveedor VACIA = new ContinuacionProveedor() { };
}
