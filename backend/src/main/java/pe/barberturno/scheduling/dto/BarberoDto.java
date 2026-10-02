package pe.barberturno.scheduling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import pe.barberturno.scheduling.Barbero;

/**
 * Proyección RF-05; obtiene el nombre del usuario y omite contactos salvo para ADMIN.
 * @param id identificador del perfil
 * @param nombre nombre visible del usuario vinculado
 * @param especialidad descripción profesional
 * @param activo habilitación de nuevas reservas
 * @param correo correo solo para ADMIN; nulo y omitido para los demás actores
 * @param telefono teléfono solo para ADMIN; nulo y omitido cuando no corresponde
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record BarberoDto(Long id, String nombre, String especialidad, boolean activo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String correo,
        @JsonInclude(JsonInclude.Include.NON_NULL) String telefono) {
    /**
     * Selecciona campos según el permiso de contactos concedido por el controlador.
     * @param barbero perfil persistido con su usuario disponible
     * @param admin permiso de lectura de datos de contacto
     * @return proyección sin credenciales ni fechas internas
     */
    public static BarberoDto desde(Barbero barbero, boolean admin) {
        var usuario = barbero.getUsuario();
        return new BarberoDto(barbero.getId(), usuario.getNombre(), barbero.getEspecialidad(),
                barbero.isActivo(), admin ? usuario.getCorreo() : null, admin ? usuario.getTelefono() : null);
    }
}
