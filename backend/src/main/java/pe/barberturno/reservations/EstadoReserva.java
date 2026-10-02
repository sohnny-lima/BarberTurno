package pe.barberturno.reservations;

import java.util.Set;

/**
 * Máquina de estados de RN-10 y ocupación de franjas de RN-14.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
public enum EstadoReserva {
    /**
     * Reserva que ocupa franja y requiere confirmación RN-21.
     */
    PENDIENTE,
    /**
     * Reserva aceptada que ocupa franja hasta atención o cancelación.
     */
    CONFIRMADA,
    /**
     * Atención iniciada por barbero asignado o administrador RN-11.
     */
    EN_ATENCION,
    /**
     * Atención terminada, estado terminal con ocupación histórica.
     */
    COMPLETADA,
    /**
     * Estado terminal que libera la franja RN-14.
     */
    CANCELADA,
    /**
     * Inasistencia registrada desde el inicio de reserva RN-12.
     */
    NO_ASISTIO
    ;

    /**
     * Destinos inmutables RN-10; los estados terminales no ofrecen ninguna transición.
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
     * Comprueba una arista RN-10; un destino nulo se considera transición inválida.
     * @param destino estado solicitado, posiblemente nulo
     * @return true únicamente para una transición de RN-10
     */
    public boolean puedePasarA(EstadoReserva destino) {
        return destino != null && transicionesPermitidas().contains(destino);
    }

    /**
     * Ocupación común al cálculo y exclusiones GiST; solo CANCELADA libera el intervalo (RN-14).
     * @return true para todos los estados salvo CANCELADA
     */
    public boolean ocupaFranja() { return this != CANCELADA; }

    /**
     * Identifica COMPLETADA, CANCELADA y NO_ASISTIO para impedir cambios posteriores según RN-10.
     * @return true para COMPLETADA, CANCELADA y NO_ASISTIO
     */
    public boolean esTerminal() {
        return this == COMPLETADA || this == CANCELADA || this == NO_ASISTIO;
    }
}
