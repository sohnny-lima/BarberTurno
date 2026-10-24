package pe.barberturno.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Paso global de disponibilidad, compatible con las duraciones del DDL de v1.
 * @param rejillaMin minutos positivos entre candidatas
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Validated
@ConfigurationProperties("barberturno.reservas")
public record RejillaReserva(@Min(1) int rejillaMin) { }
