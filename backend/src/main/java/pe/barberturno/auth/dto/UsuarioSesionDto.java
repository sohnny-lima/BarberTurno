package pe.barberturno.auth.dto;

import pe.barberturno.users.Rol;

/** Identidad pública de la sesión, sin datos de bloqueo ni contraseña. */
public record UsuarioSesionDto(long id, String nombre, String correo, Rol rol,
        Long barberoId, boolean debeCambiarPassword) { }