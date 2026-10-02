package pe.barberturno.scheduling.dto;

import java.time.OffsetDateTime;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.scheduling.Bloqueo;

/**
 * Proyección RF-06 de indisponibilidad con instantes en Lima y sin datos internos del actor.
 * @param id identidad persistente del bloqueo
 * @param barberoId perfil cuya disponibilidad se bloquea
 * @param inicio límite inclusivo con desfase de Lima
 * @param fin límite exclusivo con desfase de Lima
 * @param motivo explicación pública de la indisponibilidad
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record BloqueoDto(Long id, Long barberoId, OffsetDateTime inicio, OffsetDateTime fin, String motivo) {
    /**
     * Convierte instantes persistidos al desfase del negocio mientras las relaciones están disponibles.
     * @param bloqueo entidad persistida con su perfil disponible
     * @return únicamente los cinco campos del contrato de bloqueos
     */
    public static BloqueoDto desde(Bloqueo bloqueo) {
        return new BloqueoDto(bloqueo.getId(), bloqueo.getBarbero().getId(),
                TiempoNegocio.aLima(bloqueo.getInicio()), TiempoNegocio.aLima(bloqueo.getFin()), bloqueo.getMotivo());
    }
}
