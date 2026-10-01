package pe.barberturno.reservations;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import pe.barberturno.users.Rol;

/**
 * Política pura de estados, actores y ventanas de RN-07, RN-08, RN-11 y RN-12.
 * La propiedad del cliente y la identidad del barbero las verifica el llamador.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class PoliticaTransiciones {
    /** Crea la política sin estado. */
    public PoliticaTransiciones() { }

    /**
     * Resultado que permite al servicio distinguir el motivo de rechazo.
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public enum Resultado {
        PERMITIDA, TRANSICION_INVALIDA, PROHIBIDO, FUERA_DE_VENTANA;

        /** @return true si no hay rechazo */
        public boolean permitida() { return this == PERMITIDA; }
    }

    /**
     * Evalúa primero la transición, luego el actor y finalmente la ventana.
     * Un CLIENTE debe ser propietario; esta función no recibe identificadores.
     * @param rol rol verificado por el servidor
     * @param esBarberoAsignado identidad del barbero comprobada por el servidor
     * @param actual estado vigente
     * @param destino estado solicitado
     * @param ahora instante de evaluación
     * @param inicio inicio de la reserva
     * @param tolerancia anticipo no negativo para iniciar la atención
     * @param anticipacionCliente mínimo para cancelar como cliente
     * @param motivo motivo de cancelación administrativa, opcional en otras acciones
     * @return autorización o motivo para mapear al catálogo de errores
     * @throws NullPointerException si falta rol, estado, instante o duración
     * @throws IllegalArgumentException si alguna duración es negativa
     */
    public Resultado evaluar(Rol rol, boolean esBarberoAsignado, EstadoReserva actual,
                             EstadoReserva destino, Instant ahora, Instant inicio,
                             Duration tolerancia, Duration anticipacionCliente, String motivo) {
        Objects.requireNonNull(rol, "rol");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(destino, "destino");
        Objects.requireNonNull(ahora, "ahora");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(tolerancia, "tolerancia");
        Objects.requireNonNull(anticipacionCliente, "anticipacionCliente");
        if (tolerancia.isNegative() || anticipacionCliente.isNegative()) {
            throw new IllegalArgumentException("Las duraciones no pueden ser negativas.");
        }
        if (!actual.puedePasarA(destino)) return Resultado.TRANSICION_INVALIDA;
        if (destino == EstadoReserva.CANCELADA) {
            if (rol == Rol.BARBERO) return Resultado.PROHIBIDO;
            return new ReglasTemporales().puedeModificar(ahora, inicio, rol == Rol.ADMIN, motivo, anticipacionCliente)
                    ? Resultado.PERMITIDA : Resultado.FUERA_DE_VENTANA;
        }
        if (rol != Rol.ADMIN && !(rol == Rol.BARBERO && esBarberoAsignado)) return Resultado.PROHIBIDO;
        if (destino == EstadoReserva.EN_ATENCION && ahora.isBefore(inicio.minus(tolerancia))) return Resultado.FUERA_DE_VENTANA;
        if (destino == EstadoReserva.NO_ASISTIO && ahora.isBefore(inicio)) return Resultado.FUERA_DE_VENTANA;
        return Resultado.PERMITIDA;
    }
}
