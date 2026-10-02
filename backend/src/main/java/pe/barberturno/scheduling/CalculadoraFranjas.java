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
 * Núcleo puro de RF-07: calcula y valida franjas RN-05 con el mismo predicado de arquitectura §8.2 y los solapes
 * de {@link ReglasTemporales} .
 * El llamador filtra las ocupaciones por estado y aplica excluirReservaId.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public final class CalculadoraFranjas {
    private final ReglasTemporales reglas = new ReglasTemporales();

    /**
     * Crea la calculadora sin repositorios ni reloj propio.
     */
    public CalculadoraFranjas() { }

    /**
     * Intervalo local de jornada dentro de un día de Lima.
     * @param inicio hora inclusiva no nula en America/Lima
     * @param fin hora exclusiva no nula en America/Lima; posterior al inicio
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record IntervaloJornada(LocalTime inicio, LocalTime fin) {
        /**
         * Valida el intervalo local.
         * @param inicio hora inclusiva no nula en America/Lima
         * @param fin hora exclusiva no nula en America/Lima; posterior al inicio
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
     * @param inicio instante absoluto inclusivo no nulo
     * @param fin instante absoluto exclusivo no nulo; posterior al inicio
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record Franja(Instant inicio, Instant fin) {
        /**
         * Valida los extremos de la franja.
         * @param inicio instante absoluto inclusivo no nulo
         * @param fin instante absoluto exclusivo no nulo; posterior al inicio
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
     * Recorre la rejilla desde cada inicio de jornada en Lima (RN-05); admite solo comienzos posteriores a ahora
     * y hasta el horizonte inclusivo, con duración completa y sin solapes.
     * @param fecha fecha no nula del negocio en America/Lima
     * @param jornadas lista no nula con intervalos no nulos del día seleccionado; admite vacía
     * @param ocupaciones lista no nula con intervalos no nulos de bloqueos y reservas que ocupan; exclusión ya
     * aplicada
     * @param duracionMin duración positiva en minutos del servicio o referencia acordada
     * @param ahora instante absoluto no nulo del Clock del servidor
     * @param limiteHorizonte instante absoluto no nulo de inicio máximo inclusivo
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
     * Aplica exactamente RN-05 del cálculo a una candidata, para que consulta y escritura acepten las mismas
     * franjas.
     * @param fecha fecha no nula del negocio en America/Lima
     * @param jornadas lista no nula con intervalos no nulos del día seleccionado; admite vacía
     * @param ocupaciones lista no nula con intervalos no nulos de bloqueos y reservas que ocupan; exclusión ya
     * aplicada
     * @param candidata intervalo absoluto solicitado, no nulo
     * @param duracionMin duración positiva en minutos del servicio o referencia acordada
     * @param ahora instante absoluto no nulo del Clock del servidor
     * @param limiteHorizonte instante absoluto no nulo de inicio máximo inclusivo
     * @param rejillaMin paso positivo en minutos desde el inicio de cada jornada
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
