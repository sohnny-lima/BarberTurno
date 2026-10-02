package pe.barberturno.users.dto;

import pe.barberturno.users.Rol;

/**
 * Datos del perfil propio RF-03, sin hash de contraseña ni información de bloqueos de acceso.
 * @param id identificador persistido no nulo de la cuenta propia
 * @param nombre nombre vigente no nulo
 * @param correo correo normalizado no editable
 * @param rol rol vigente
 * @param telefono teléfono de nueve dígitos; puede ser nulo para el personal, obligatorio para CLIENTE
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record PerfilDto(Long id, String nombre, String correo, Rol rol, String telefono) { }