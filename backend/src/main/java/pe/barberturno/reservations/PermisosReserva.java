package pe.barberturno.reservations;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import pe.barberturno.common.config.ParametrosReserva;
import pe.barberturno.reservations.dto.ReservaDto.PermisosDto;
import pe.barberturno.users.Rol;

/**
 * Política compartida de acciones: la propiedad puede proceder de cuenta web o ficha verificada.
 * No interpreta una identidad de ficha como cuenta (V2-DA-69).
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public final class PermisosReserva {
    private PermisosReserva() { }

    /**
     * Evalúa DA-15 y RN-07/09/11/12 sin consultar sesión ni persistencia.
     * @param reserva cita consultada
     * @param rol papel efectivo del solicitante
     * @param propietario propiedad ya comprobada por cuenta o ficha
     * @param asignado si es el profesional de la cita
     * @param ahora instante del reloj inyectado
     * @param parametros reglas del negocio
     * @return capacidades para mostrar acciones; el servicio vuelve a validarlas al escribir
     */
    public static PermisosDto calcular(Reserva reserva, Rol rol, boolean propietario, boolean asignado,
            Instant ahora, ParametrosReserva parametros) {
        boolean admin = rol == Rol.ADMIN;
        var politica = new PoliticaTransiciones();
        boolean modificable = (admin || propietario) && new ReglasTemporales().puedeModificar(ahora,
                reserva.getInicio(), admin, admin ? "Motivo requerido" : null,
                parametros.anticipacionCambioCliente());
        boolean cancelar = modificable && politica.evaluar(rol, asignado, reserva.getEstado(),
                EstadoReserva.CANCELADA, ahora, reserva.getInicio(),
                Duration.ofMinutes(parametros.toleranciaInicioMin()), parametros.anticipacionCambioCliente(),
                admin ? "Motivo requerido" : null).permitida();
        var destinos = Arrays.stream(EstadoReserva.values()).filter(e -> e != EstadoReserva.CANCELADA)
                .filter(e -> politica.evaluar(rol, asignado, reserva.getEstado(), e, ahora, reserva.getInicio(),
                        Duration.ofMinutes(parametros.toleranciaInicioMin()), parametros.anticipacionCambioCliente(),
                        null).permitida()).toList();
        return new PermisosDto(modificable && reserva.getEstado() == EstadoReserva.CONFIRMADA, cancelar, destinos);
    }
}
