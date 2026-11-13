package pe.barberturno.platform.dto;

/**
 * Parámetros públicos mínimos para presentar reservas sin exponer configuración interna (V2-DA-44).
 * @param slug dirección pública del negocio
 * @param nombre nombre mostrado al visitante
 * @param horizonteDias días disponibles para consultar y reservar
 * @param anticipacionCambioClienteMin anticipación exigida al cliente para cambios
 * @param limiteReservasActivas máximo de reservas vigentes por ficha
 * @param confirmacionManual indica si las reservas requieren confirmación del personal
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record NegocioPublicoDto(String slug, String nombre, int horizonteDias,
        int anticipacionCambioClienteMin, int limiteReservasActivas, boolean confirmacionManual) { }
