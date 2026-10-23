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
}
