package pe.barberturno.platform.dto;

import pe.barberturno.platform.EstadoNegocio;

/**
 * Alta con enlace efímero; el inventario sigue usando NegocioPlataformaDto.
 * @param id negocio creado
 * @param slug ruta canónica
 * @param nombre denominación pública
 * @param estado disponibilidad administrativa
 * @param miembrosActivos recuento inicial
 * @param enlaceInvitacion capacidad que se muestra solo en esta respuesta
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record NegocioCreadoDto(long id, String slug, String nombre, EstadoNegocio estado, long miembrosActivos, String enlaceInvitacion) {
    /** @return descripción sin datos personales ni capacidades secretas */
    @Override public String toString() { return "NegocioCreadoDto[datos omitidos]"; }
}
