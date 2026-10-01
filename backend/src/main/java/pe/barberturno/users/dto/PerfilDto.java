package pe.barberturno.users.dto;

import pe.barberturno.users.Rol;

/** Datos del perfil propio, sin información de acceso sensible. */
public record PerfilDto(Long id, String nombre, String correo, Rol rol, String telefono) { }