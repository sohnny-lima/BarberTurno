package pe.barberturno.scheduling.dto;

/**
 * Resultado RF-05 que permite gestionar citas futuras sin cancelarlas automáticamente.
 * @param barbero perfil resultante con datos autorizados para ADMIN
 * @param reservasFuturasVigentes reservas con inicio posterior al Clock y estado que ocupa franja RN-14
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ResultadoEstadoBarberoDto(BarberoDto barbero, long reservasFuturasVigentes) { }
