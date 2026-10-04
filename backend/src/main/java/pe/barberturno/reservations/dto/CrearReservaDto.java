package pe.barberturno.reservations.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

/**
 * Solicitud RF-08 con instante absoluto; clienteId queda reservado a RF-18.
 * @param servicioId servicio solicitado, identidad positiva
 * @param barberoId perfil solicitado, identidad positiva
 * @param inicio inicio inclusivo con desfase obligatorio
 * @param clienteId propietario asistido opcional; CLIENTE no puede enviarlo
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public record CrearReservaDto(@NotNull @Positive Long servicioId,
        @NotNull @Positive Long barberoId, @NotNull OffsetDateTime inicio,
        @Positive Long clienteId) { }
