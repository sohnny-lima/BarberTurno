import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Reglas temporales de BarberTurno para el anexo académico APF2.
 * <p>Trabaja con horas locales de America/Lima y franjas [inicio, fin).
 * No persiste reservas ni sustituye la autorización o la transacción del backend.</p>
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public final class ReservaService {
    /** Crea el servicio de reglas temporales sin estado. */
    public ReservaService() { }

    /**
     * Calcula el final de una reserva usando la duración acordada del servicio.
     * @param inicio fecha y hora local de inicio
     * @param minutos duración positiva expresada en minutos
     * @return fecha y hora local de finalización
     * @throws NullPointerException si inicio es nulo
     * @throws IllegalArgumentException si minutos no es positivo
     */
    public LocalDateTime calcularFin(LocalDateTime inicio, int minutos) {
        Objects.requireNonNull(inicio, "inicio");
        if (minutos <= 0) throw new IllegalArgumentException("La duración debe ser positiva.");
        return inicio.plusMinutes(minutos);
    }

    /**
     * Comprueba si dos intervalos válidos comparten algún instante.
     * <p>Una reserva que empieza al terminar otra no se considera superpuesta.</p>
     * @param inicioA inicio del primer intervalo
     * @param finA fin exclusivo del primer intervalo
     * @param inicioB inicio del segundo intervalo
     * @param finB fin exclusivo del segundo intervalo
     * @return true si los intervalos se solapan; false si son disjuntos o contiguos
     * @throws NullPointerException si alguna fecha es nula
     * @throws IllegalArgumentException si algún fin no es posterior a su inicio
     */
    public boolean seSolapan(LocalDateTime inicioA, LocalDateTime finA,
                             LocalDateTime inicioB, LocalDateTime finB) {
        validarIntervalo(inicioA, finA);
        validarIntervalo(inicioB, finB);
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }

    /**
     * Evalúa la política temporal de reprogramación o cancelación.
     * <p>El cliente requiere al menos dos horas. Una excepción del administrador
     * exige motivo. El llamador debe validar identidad, propiedad y estado.</p>
     * @param ahora instante local de evaluación proporcionado por el servidor
     * @param inicio inicio de la reserva
     * @param administrador true si el servidor ha verificado el rol administrador
     * @param motivo motivo de la excepción; puede ser nulo para un cliente
     * @return true si la política temporal permite el cambio
     * @throws NullPointerException si ahora o inicio son nulos
     */
    public boolean puedeModificar(LocalDateTime ahora, LocalDateTime inicio,
                                   boolean administrador, String motivo) {
        Objects.requireNonNull(ahora, "ahora");
        Objects.requireNonNull(inicio, "inicio");
        if (administrador) return motivo != null && !motivo.isBlank();
        return Duration.between(ahora, inicio).compareTo(Duration.ofHours(2)) >= 0;
    }

    private void validarIntervalo(LocalDateTime inicio, LocalDateTime fin) {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (!inicio.isBefore(fin)) throw new IllegalArgumentException("Intervalo inválido.");
    }
}
