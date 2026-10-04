package pe.barberturno.users.dto;

import pe.barberturno.users.Rol;

/**
 * Identidad mínima para gestión RF-19; excluye hash, tokens y datos internos de bloqueo.
 * @param id identidad persistente
 * @param nombre nombre visible
 * @param correo correo normalizado
 * @param telefono contacto opcional
 * @param rol permisos de la cuenta
 * @param activo acceso habilitado
 * @param debeCambiarPassword cambio obligatorio pendiente
 * @param barberoId perfil de atención opcional
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record UsuarioAdminDto(long id, String nombre, String correo, String telefono, Rol rol,
        boolean activo, boolean debeCambiarPassword, Long barberoId) { }
