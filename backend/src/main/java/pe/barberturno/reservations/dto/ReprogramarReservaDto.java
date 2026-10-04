package pe.barberturno.reservations.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

/**
 * Solicitud RF-09; conserva servicio, precio y duración de referencia RN-09/13.
 * @param inicio nuevo inicio inclusivo con desfase obligatorio
 * @param barberoId destino opcional positivo; si falta se conserva el barbero actual
 * @param version versión leída por el actor, obligatoria y no negativa
 * @param motivo justificación opcional del cliente; ADMIN debe cumplir RN-08
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record ReprogramarReservaDto(@NotNull OffsetDateTime inicio, @Positive Long barberoId,
        @NotNull @PositiveOrZero Integer version, @Size(max = 300) String motivo) { }
