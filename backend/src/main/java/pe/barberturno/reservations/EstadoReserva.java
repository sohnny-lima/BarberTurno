package pe.barberturno.reservations;

import java.util.Set;

/**
 * Máquina de estados de RN-10 y ocupación de franjas de RN-14.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, EN_ATENCION, COMPLETADA, CANCELADA, NO_ASISTIO;

    /**
     * @return destinos de RN-10 en un conjunto inmutable
     */
    public Set<EstadoReserva> transicionesPermitidas() {
        return switch (this) {
            case PENDIENTE -> Set.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA -> Set.of(EN_ATENCION, CANCELADA, NO_ASISTIO);
            case EN_ATENCION -> Set.of(COMPLETADA);
            default -> Set.of();
        };
    }

    /**
     * @param destino estado solicitado, posiblemente nulo
     * @return true únicamente para una transición de RN-10
     */
    public boolean puedePasarA(EstadoReserva destino) {
        return destino != null && transicionesPermitidas().contains(destino);
    }

    /** @return true para todos los estados salvo CANCELADA */
    public boolean ocupaFranja() { return this != CANCELADA; }

    /** @return true para COMPLETADA, CANCELADA y NO_ASISTIO */
    public boolean esTerminal() {
        return this == COMPLETADA || this == CANCELADA || this == NO_ASISTIO;
    }
}
