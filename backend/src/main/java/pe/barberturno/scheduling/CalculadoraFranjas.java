package pe.barberturno.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.reservations.ReglasTemporales;

/**
 * Calcula y valida franjas con el mismo predicado puro de arquitectura §8.2.
 * El llamador filtra las ocupaciones por estado y aplica excluirReservaId.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class CalculadoraFranjas {
    private final ReglasTemporales reglas = new ReglasTemporales();

    /** Crea la calculadora sin repositorios ni reloj propio. */
    public CalculadoraFranjas() { }

    /**
     * Intervalo local de jornada dentro de un día de Lima.
     * @param inicio hora de inicio
     * @param fin hora de fin exclusivo
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record IntervaloJornada(LocalTime inicio, LocalTime fin) {
        /**
         * Valida el intervalo local.
         * @param inicio hora de inicio
         * @param fin hora de fin exclusivo
         * @throws NullPointerException si falta un extremo
         * @throws IllegalArgumentException si inicio no precede a fin
         */
        public IntervaloJornada {
            Objects.requireNonNull(inicio, "inicio");
            Objects.requireNonNull(fin, "fin");
            if (!inicio.isBefore(fin)) throw new IllegalArgumentException("Intervalo de jornada inválido.");
        }
    }

    /**
     * Intervalo absoluto semiabierto para una ocupación o una franja disponible.
     * @param inicio instante de inicio
     * @param fin instante de fin exclusivo
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record Franja(Instant inicio, Instant fin) {
        /**
         * Valida los extremos de la franja.
         * @param inicio instante de inicio
         * @param fin instante de fin exclusivo
         * @throws NullPointerException si falta un extremo
         * @throws IllegalArgumentException si inicio no precede a fin
         */
        public Franja {
            Objects.requireNonNull(inicio, "inicio");
            Objects.requireNonNull(fin, "fin");
            if (!inicio.isBefore(fin)) throw new IllegalArgumentException("Franja inválida.");
        }
    }

    /**
     * Recorre la rejilla desde el comienzo de cada intervalo de jornada.
     * @param fecha día de Lima
     * @param jornadas intervalos del día ya seleccionados por el llamador
     * @param ocupaciones bloqueos y reservas que ocupan, con exclusión ya aplicada
     * @param duracionMin duración positiva del servicio
     * @param ahora instante aportado por el reloj del servidor
     * @param limiteHorizonte máximo inclusivo del inicio
     * @param rejillaMin paso positivo de la rejilla en minutos
     * @return franjas inmutables, sin duplicados, ordenadas por inicio
     * @throws NullPointerException si falta una entrada obligatoria
     * @throws IllegalArgumentException si duración o rejilla no son positivas
     */
    public List<Franja> calcular(LocalDate fecha, List<IntervaloJornada> jornadas,
                                List<Franja> ocupaciones, int duracionMin, Instant ahora,
                                Instant limiteHorizonte, int rejillaMin) {
        validarEntradas(fecha, jornadas, ocupaciones, duracionMin, ahora, limiteHorizonte, rejillaMin);
        List<Franja> disponibles = new ArrayList<>();
        for (IntervaloJornada jornada : jornadas) {
            Instant finJornada = convertir(fecha, jornada.fin());
            for (Instant inicio = convertir(fecha, jornada.inicio());
                 !reglas.calcularFin(inicio, duracionMin).isAfter(finJornada);
                 inicio = inicio.plus(rejillaMin, ChronoUnit.MINUTES)) {
                Franja candidata = new Franja(inicio, reglas.calcularFin(inicio, duracionMin));
                if (cumpleReglas(fecha, jornadas, ocupaciones, candidata, duracionMin, ahora, limiteHorizonte, rejillaMin)) {
                    disponibles.add(candidata);
                }
            }
        }
        return disponibles.stream().distinct().sorted(Comparator.comparing(Franja::inicio)).toList();
    }

    /**
     * Usa el mismo predicado del cálculo para validar una única candidata.
     * @param fecha día de Lima
     * @param jornadas intervalos del día ya seleccionados
     * @param ocupaciones bloqueos y reservas filtrados, con exclusión aplicada
     * @param candidata intervalo solicitado
     * @param duracionMin duración positiva acordada del servicio
     * @param ahora instante de evaluación
     * @param limiteHorizonte máximo inclusivo del inicio
     * @param rejillaMin paso positivo desde el inicio de cada jornada
     * @return true si la candidata pertenece a la disponibilidad calculada
     * @throws NullPointerException si falta una entrada obligatoria
     * @throws IllegalArgumentException si duración o rejilla no son positivas
     */
    public boolean esFranjaValida(LocalDate fecha, List<IntervaloJornada> jornadas,
                                 List<Franja> ocupaciones, Franja candidata, int duracionMin,
                                 Instant ahora, Instant limiteHorizonte, int rejillaMin) {
        validarEntradas(fecha, jornadas, ocupaciones, duracionMin, ahora, limiteHorizonte, rejillaMin);
        Objects.requireNonNull(candidata, "candidata");
        return cumpleReglas(fecha, jornadas, ocupaciones, candidata, duracionMin, ahora, limiteHorizonte, rejillaMin);
    }

    private boolean cumpleReglas(LocalDate fecha, List<IntervaloJornada> jornadas,
                                 List<Franja> ocupaciones, Franja candidata, int duracionMin,
                                 Instant ahora, Instant limiteHorizonte, int rejillaMin) {
        if (!candidata.fin().equals(reglas.calcularFin(candidata.inicio(), duracionMin))
                || !candidata.inicio().isAfter(ahora) || candidata.inicio().isAfter(limiteHorizonte)) return false;
        boolean enJornada = jornadas.stream().anyMatch(jornada -> {
            Instant inicio = convertir(fecha, jornada.inicio());
            Duration desplazamiento = Duration.between(inicio, candidata.inicio());
            return !candidata.inicio().isBefore(inicio) && !candidata.fin().isAfter(convertir(fecha, jornada.fin()))
                    && desplazamiento.getNano() == 0 && desplazamiento.getSeconds() % (rejillaMin * 60L) == 0;
        });
        return enJornada && ocupaciones.stream().noneMatch(ocupacion -> reglas.seSolapan(
                candidata.inicio(), candidata.fin(), ocupacion.inicio(), ocupacion.fin()));
    }

    private Instant convertir(LocalDate fecha, LocalTime hora) {
        return fecha.atTime(hora).atZone(TiempoNegocio.ZONA).toInstant();
    }

    private void validarEntradas(LocalDate fecha, List<IntervaloJornada> jornadas,
                                  List<Franja> ocupaciones, int duracionMin, Instant ahora,
                                  Instant limiteHorizonte, int rejillaMin) {
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(jornadas, "jornadas");
        Objects.requireNonNull(ocupaciones, "ocupaciones");
        Objects.requireNonNull(ahora, "ahora");
        Objects.requireNonNull(limiteHorizonte, "limiteHorizonte");
        if (duracionMin <= 0 || rejillaMin <= 0) throw new IllegalArgumentException("Duración y rejilla deben ser positivas.");
    }
}
