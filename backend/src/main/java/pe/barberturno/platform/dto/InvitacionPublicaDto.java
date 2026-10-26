package pe.barberturno.platform.dto;

import pe.barberturno.platform.RolNegocio;

/**
 * Información pública mínima de una capacidad vigente.
 * @param negocio nombre público del negocio
 * @param rol permisos que se acreditarán
 * @param correoEnmascarado destinatario oculto para orientar el inicio de sesión
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record InvitacionPublicaDto(String negocio, RolNegocio rol, String correoEnmascarado) { }
