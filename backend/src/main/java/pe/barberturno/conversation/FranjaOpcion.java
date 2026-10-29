package pe.barberturno.conversation;

import java.time.Instant;

/**
 * Franja libre devuelta por {@code DisponibilidadService} para un profesional concreto.
 * @param barberoId profesional de la franja (nunca «cualquiera»)
 * @param profesional nombre visible del profesional
 * @param inicio inicio de la franja
 * @param fin fin de la franja según la duración del servicio
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record FranjaOpcion(long barberoId, String profesional, Instant inicio, Instant fin) { }
