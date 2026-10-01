package pe.barberturno.reservations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Reglas temporales puras del anexo APF2, aplicadas a instantes absolutos.
 * El llamador comprueba identidad, propiedad y estado de la reserva.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class ReglasTemporales {
    /** Crea las reglas sin estado ni dependencias de infraestructura. */
    public ReglasTemporales() { }

    /**
     * Calcula el fin exclusivo según la duración acordada del servicio.
     * @param inicio instante de inicio
     * @param minutos duración positiva en minutos
     * @return instante de finalización
     * @throws NullPointerException si inicio es nulo
     * @throws IllegalArgumentException si la duración no es positiva
     */
    public Instant calcularFin(Instant inicio, int minutos) {
        Objects.requireNonNull(inicio, "inicio");
        if (minutos <= 0) throw new IllegalArgumentException("La duración debe ser positiva.");
        return inicio.plus(minutos, ChronoUnit.MINUTES);
    }

    /**
     * Comprueba el solapamiento de intervalos semiabiertos; permite contigüidad.
     * @param inicioA inicio del primer intervalo
     * @param finA fin exclusivo del primer intervalo
     * @param inicioB inicio del segundo intervalo
     * @param finB fin exclusivo del segundo intervalo
     * @return true si comparten algún instante
     * @throws NullPointerException si falta un extremo
     * @throws IllegalArgumentException si algún intervalo no es positivo
     */
    public boolean seSolapan(Instant inicioA, Instant finA, Instant inicioB, Instant finB) {
        validarIntervalo(inicioA, finA);
        validarIntervalo(inicioB, finB);
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }

    /**
     * Evalúa RN-07 y RN-08 para cancelar o reprogramar.
     * @param ahora instante de evaluación aportado por el servidor
     * @param inicio inicio de la reserva
     * @param esAdmin rol administrador comprobado por el llamador
     * @param motivo motivo administrativo, opcional para el cliente
     * @param anticipacionCliente mínimo no negativo para el cliente
     * @return true si el cliente cumple el mínimo o el administrador está antes
     *         del inicio y aporta al menos cinco caracteres no blancos
     * @throws NullPointerException si falta un instante o la anticipación
     * @throws IllegalArgumentException si la anticipación es negativa
     */
    public boolean puedeModificar(Instant ahora, Instant inicio, boolean esAdmin,
                                   String motivo, Duration anticipacionCliente) {
        Objects.requireNonNull(ahora, "ahora");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(anticipacionCliente, "anticipacionCliente");
        if (anticipacionCliente.isNegative()) throw new IllegalArgumentException("La anticipación no puede ser negativa.");
        if (esAdmin) {
            return ahora.isBefore(inicio) && motivo != null
                    && motivo.codePoints().filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c)).count() >= 5;
        }
        return Duration.between(ahora, inicio).compareTo(anticipacionCliente) >= 0;
    }

    private void validarIntervalo(Instant inicio, Instant fin) {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (!inicio.isBefore(fin)) throw new IllegalArgumentException("Intervalo inválido.");
    }
}
