package pe.barberturno.conversation;

/**
 * Por qué una confirmación no se puede ejecutar. Ningún motivo revela si existe una operación ajena.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public enum MotivoNoVigente {
    /** Pasaron los 10 minutos de vigencia; la operación queda {@code CADUCADA}. */
    CADUCADA,
    /** Otra propuesta posterior, o la vuelta al menú, la anuló. */
    SUSTITUIDA,
    /** Ya terminó como {@code RECHAZADA} o {@code CADUCADA}. */
    USADA,
    /** No existe en el negocio, es de otra conversación o ficha, o el nonce no coincide. */
    NO_ENCONTRADA
}
