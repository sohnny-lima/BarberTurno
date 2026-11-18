package pe.barberturno.auth.dto;

import pe.barberturno.users.Rol;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.platform.RolPlataforma;

/**
 * Identidad pública de la sesión; omite hash, versión de revocación e intentos fallidos (RNF-12).
 * @param id identificador persistido de la cuenta autenticada
 * @param nombre nombre público vigente
 * @param correo correo normalizado de acceso
 * @param rol rol vigente revalidado
 * @param barberoId id del perfil; nulo si no tiene perfil de atención
 * @param debeCambiarPassword marca que limita la cuenta a auth y lectura del perfil hasta cambiar su credencial
 * @param rolPlataforma papel global de presentación; nulo sin administración de plataforma
 * @param rolNegocio membresía activa de presentación; nula en globales y al solicitar papel cliente
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record UsuarioSesionDto(long id, String nombre, String correo, Rol rol,
        Long barberoId, boolean debeCambiarPassword, RolPlataforma rolPlataforma, RolNegocio rolNegocio) { }
