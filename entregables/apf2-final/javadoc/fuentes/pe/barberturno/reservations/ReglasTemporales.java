package pe.barberturno.reservations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Reglas temporales puras del anexo APF2: RN-01, RN-07 y RN-08 sobre instantes absolutos.
 * El llamador comprueba identidad, propiedad y estado de la reserva.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class ReglasTemporales {
    /**
     * Crea las reglas sin estado ni dependencias de infraestructura.
     */
    public ReglasTemporales() { }

    /**
     * Calcula el fin exclusivo RN-01 con la duración acordada RN-13, sin consultar el catálogo actual.
     * @param inicio instante absoluto no nulo de inicio inclusivo
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
     * Comprueba RN-01 con intervalos {@code [inicio, fin)} ; dos citas contiguas no se solapan.
     * @param inicioA instante absoluto no nulo de inicio inclusivo del primer intervalo
     * @param finA instante absoluto no nulo de fin exclusivo del primer intervalo
     * @param inicioB instante absoluto no nulo de inicio inclusivo del segundo intervalo
     * @param finB instante absoluto no nulo de fin exclusivo del segundo intervalo
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
     * Evalúa RN-07 y RN-08 para cancelar o reprogramar: el límite exacto de dos horas del cliente está
     * permitido. El ADMIN debe estar estrictamente antes del inicio y aportar cinco caracteres no blancos; no
     * admite modificar justo al inicio. La autorización por actor y estado corresponde a
     * {@link PoliticaTransiciones} .
     * @param ahora instante absoluto no nulo aportado por el Clock del servidor
     * @param inicio instante absoluto no nulo de inicio de la reserva
     * @param esAdmin rol administrador comprobado por el llamador
     * @param motivo justificación; puede ser nula para cliente y es obligatoria para ADMIN
     * @param anticipacionCliente duración no nula y no negativa; por defecto dos horas para RN-07
     * @return true si el cliente cumple el mínimo o el administrador está antes del inicio y aporta al menos
     * cinco caracteres no blancos
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
