package pe.barberturno.conversation;

/**
 * Profesional activo del negocio tal como se ofrece en la conversación.
 * @param id identidad del perfil profesional ({@code barbero.id})
 * @param nombre nombre visible del perfil ({@code barbero.nombre_visible})
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ProfesionalOpcion(long id, String nombre) { }
