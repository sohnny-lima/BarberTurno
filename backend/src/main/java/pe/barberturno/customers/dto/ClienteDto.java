package pe.barberturno.customers.dto;

import pe.barberturno.customers.Cliente;

/**
 * Datos locales visibles para quienes gestionan el negocio, sin credenciales ni identidad global.
 * @param id identificador de la ficha, no de la cuenta (V2-DA-29)
 * @param nombre nombre local
 * @param telefono contacto local opcional
 * @param activo vigencia del papel de cliente
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record ClienteDto(long id, String nombre, String telefono, boolean activo) {
    /**
     * Proyecta exclusivamente las columnas locales autorizadas.
     * @param cliente ficha visible en el negocio actual
     * @return datos de la ficha sin información de su cuenta
     */
    public static ClienteDto desde(Cliente cliente) {
        return new ClienteDto(cliente.getId(), cliente.getNombre(), cliente.getTelefono(), cliente.isActivo());
    }
}
