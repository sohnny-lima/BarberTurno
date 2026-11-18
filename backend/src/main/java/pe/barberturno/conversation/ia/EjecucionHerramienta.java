package pe.barberturno.conversation.ia;

import java.util.Optional;

/**
 * Separa el resultado sin identidad que ve el modelo del efecto validado que aplicará el servidor.
 * @param paraModelo resultado asociado al tool_use_id
 * @param efecto intención inmutable, sin escritura de reserva ni operación pendiente
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record EjecucionHerramienta(ResultadoHerramienta paraModelo, Optional<EfectoTurno> efecto) { }
