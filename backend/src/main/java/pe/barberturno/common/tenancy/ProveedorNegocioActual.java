package pe.barberturno.common.tenancy;

import java.util.OptionalLong;

/**
 * Entrega el negocio al abrir una transacción, sin depender de entidades ni del transporte.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public interface ProveedorNegocioActual {
    /**
     * Resuelve el ámbito de la operación antes de consultar datos de negocio.
     * @return identificador positivo, o vacío para operar sin contexto y fallar cerrado con RLS
     */
    OptionalLong negocioActual();

    /**
     * Indica la reducción solicitada por una petición canónica de negocio (V2-DA-42).
     * No concede ficha ni permisos; los trabajos sin petición conservan el papel habitual.
     * @return true solo si el resolutor validó X-BT-Papel: CLIENTE para esta petición
     */
    default boolean clienteSolicitado() { return false; }
}
