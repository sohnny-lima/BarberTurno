package pe.barberturno.platform.dto;

import pe.barberturno.platform.EstadoNegocio;

/**
 * Proyección global sin datos operativos ni personales del negocio.
 * @param id identificador de plataforma
 * @param slug ruta pública canónica
 * @param nombre denominación pública
 * @param estado disponibilidad administrativa
 * @param miembrosActivos número de membresías activas con cuenta habilitada
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record NegocioPlataformaDto(long id, String slug, String nombre, EstadoNegocio estado,
        long miembrosActivos) { }
