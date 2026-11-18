package pe.barberturno.platform;

import pe.barberturno.users.Rol;

/**
 * Permisos de personal obtenidos exclusivamente de una membresía activa del negocio.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public enum RolNegocio {
    /** Titular del negocio, protegido por V2-RN-06. */
    PROPIETARIO,
    /** Gestión operativa limitada por R2-08. */
    ENCARGADO,
    /** Atención de la agenda asignada. */
    BARBERO;

    /**
     * Rol de v1 con el que se autoriza este rol de negocio (V2-DA-25): PROPIETARIO y ENCARGADO equivalen a ADMIN.
     * @return equivalencia de autorización de v1, sin conceder permisos de plataforma
     */
    public Rol rolEfectivo() { return this == BARBERO ? Rol.BARBERO : Rol.ADMIN; }
}
