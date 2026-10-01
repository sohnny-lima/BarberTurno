package pe.barberturno.common.config;

import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros externos de reserva; las políticas puras reciben sus valores por parámetro.
 * @param anticipacionCambioCliente mínimo no negativo para cambios del cliente
 * @param horizonteDias días positivos de horizonte
 * @param rejillaMin paso positivo de disponibilidad
 * @param maxActivasPorCliente máximo positivo de reservas futuras
 * @param confirmacionManual activa el estado inicial PENDIENTE para autoservicio
 * @param toleranciaInicioMin minutos no negativos antes del inicio de atención
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Validated
@ConfigurationProperties("barberturno.reservas")
public record ParametrosReserva(
        @NotNull Duration anticipacionCambioCliente,
        @Min(1) int horizonteDias,
        @Min(1) int rejillaMin,
        @Min(1) int maxActivasPorCliente,
        boolean confirmacionManual,
        @Min(0) int toleranciaInicioMin) {

    /** @return true si la anticipación presente es no negativa; NotNull valida la ausencia */
    @AssertTrue(message = "La anticipación del cliente no puede ser negativa.")
    public boolean isAnticipacionValida() {
        return anticipacionCambioCliente == null || !anticipacionCambioCliente.isNegative();
    }
}
