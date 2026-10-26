package pe.barberturno.platform.dto;

import pe.barberturno.platform.*;

/**
 * Pertenencia local sin credenciales ni información de otros negocios.
 * @param id identificador de la membresía
 * @param usuarioId cuenta global a la que pertenece
 * @param rol permisos locales
 * @param estado ACTIVA o REVOCADA
 * @param motivoRevocacion causa de la baja, nula mientras esté activa
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record EquipoDto(long id, long usuarioId, RolNegocio rol, String estado, MotivoRevocacion motivoRevocacion) { }
