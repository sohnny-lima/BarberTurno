package pe.barberturno.platform.dto;

import pe.barberturno.platform.RolNegocio;

/**
 * Información mínima ante quien presenta la capacidad secreta, sin revelar el destinatario.
 * @param negocio nombre público del negocio
 * @param rol permisos que se acreditarán
 * @param correoEnmascarado destinatario oculto para orientar el inicio de sesión
 * @param estado estado de presentación: PENDIENTE, CADUCADA, REVOCADA o USADA (V2-DA-46)
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public record InvitacionPublicaDto(String negocio, RolNegocio rol, String correoEnmascarado, String estado) { }
