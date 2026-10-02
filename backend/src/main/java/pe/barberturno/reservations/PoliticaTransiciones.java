package pe.barberturno.reservations;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import pe.barberturno.users.Rol;

/**
 * Política pura de estados, actores y ventanas de RN-07, RN-08 y RN-10 a RN-12.
 * La propiedad del cliente y la identidad del barbero las verifica el llamador.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class PoliticaTransiciones {
    /**
     * Crea la política sin estado.
     */
    public PoliticaTransiciones() { }

    /**
     * Resultado que permite al servicio distinguir el motivo de rechazo.
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public enum Resultado {
    /**
     * Acción autorizada por estado, actor y ventana.
     */
    PERMITIDA,
    /**
     * Destino ausente de la máquina de estados RN-10.
     */
    TRANSICION_INVALIDA,
    /**
     * Actor sin permiso para la acción solicitada.
     */
    PROHIBIDO,
    /**
     * Ventana incumplida o motivo administrativo insuficiente.
     */
    FUERA_DE_VENTANA
    ;

        /**
         * Indica si estado, actor y ventana permiten la acción solicitada.
         * @return true si no hay rechazo
         */
        public boolean permitida() { return this == PERMITIDA; }
    }

    /**
     * Evalúa primero RN-10, después actor RN-11 y ventana RN-12: iniciar desde quince minutos antes y no asistió
     * desde el inicio, con ambos límites permitidos. Cancelar delega RN-07/08 en {@link ReglasTemporales} ;
     * BARBERO nunca cancela. Completar no añade ventana tras EN_ATENCION.
     * Un CLIENTE debe ser propietario; esta función no recibe identificadores.
     * @param rol rol no nulo revalidado por el servidor
     * @param esBarberoAsignado identidad del barbero comprobada por el servidor
     * @param actual estado persistido vigente no nulo
     * @param destino estado solicitado no nulo
     * @param ahora instante absoluto no nulo de evaluación del Clock
     * @param inicio instante absoluto no nulo de inicio de la reserva
     * @param tolerancia duración no nula y no negativa; por defecto quince minutos
     * @param anticipacionCliente duración no nula y no negativa; por defecto dos horas, límite exacto permitido
     * @param motivo justificación administrativa de cinco caracteres no blancos; puede ser nula en otras
     * acciones
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
