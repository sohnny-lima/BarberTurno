package pe.barberturno.conversation.ia.evaluacion;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Anotaciones explícitas complementarias al conjunto BORRADOR, sin modificar sus 120 conversaciones. */
final class FixturesEvaluacion {
    record Ventana(String desde, String hasta) {
        void validar() {
            if (!OffsetDateTime.parse(desde).toInstant().isBefore(OffsetDateTime.parse(hasta).toInstant()))
                throw new IllegalArgumentException("La ventana exige desde < hasta.");
        }
        boolean contiene(Instant inicio) {
            return inicio != null && !inicio.isBefore(OffsetDateTime.parse(desde).toInstant())
                    && inicio.isBefore(OffsetDateTime.parse(hasta).toInstant());
        }
    }
    record Fixture(String id, Instant reloj, String operacion, List<Instant> citasPrevias,
            Optional<Ventana> ventana, Optional<List<Ventana>> ventanas, boolean inicioLibre) {
        List<Ventana> intervalos() { return ventanas.orElseGet(() -> ventana.stream().toList()); }
    }
    /** Catálogo que siembra {@code EscenarioEvaluacion}: los nombres anotados en el conjunto deben estar aquí. */
    static final List<String> SERVICIOS = List.of("Corte clásico", "Barba");
    /** Profesionales que siembra {@code EscenarioEvaluacion}. */
    static final List<String> PROFESIONALES = List.of("Carlos", "Miguel");
    private static final java.time.format.DateTimeFormatter INICIO = java.time.format.DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private final Map<String, Fixture> fixtures;
    private FixturesEvaluacion(Map<String, Fixture> fixtures) { this.fixtures = Map.copyOf(fixtures); }

    static FixturesEvaluacion cargar() {
        try (var in = FixturesEvaluacion.class.getResourceAsStream("/evaluacion/fixtures-v2-13.yaml")) {
            if (in == null) throw new IllegalStateException("Faltan fixtures explícitos.");
            return cargar(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ex) { throw new IllegalStateException("No se pudieron cargar fixtures.", ex); }
    }

    static FixturesEvaluacion cargar(String yaml) {
        var opciones = new LoaderOptions(); opciones.setAllowDuplicateKeys(false);
        List<?> filas = new Yaml(new SafeConstructor(opciones)).load(yaml);
        Map<String, Fixture> resultado = new LinkedHashMap<>();
        for (Object fila : filas) {
            Map<?, ?> m = (Map<?, ?>) fila;
            String id = m.get("id").toString();
            var citas = ((List<?>) m.get("citasPrevias")).stream().map(v -> Instant.parse(v.toString())).toList();
            Optional<Ventana> ventana = Optional.empty();
            if (m.containsKey("ventana")) ventana = Optional.of(leerVentana(m.get("ventana"), id));
            Optional<List<Ventana>> ventanas = Optional.empty();
            if (m.containsKey("ventanas")) {
                if (!(m.get("ventanas") instanceof List<?> lista))
                    throw new IllegalStateException("Ventanas debe ser una lista: " + id);
                ventanas = Optional.of(lista.stream().map(v -> leerVentana(v, id)).toList());
            }
            if (m.containsKey("inicioLibre") && !(m.get("inicioLibre") instanceof Boolean))
                throw new IllegalStateException("inicioLibre debe ser booleano: " + id);
            Fixture fixture = new Fixture(id, Instant.parse(m.get("reloj").toString()), m.get("operacion").toString(), citas,
                    ventana, ventanas, Boolean.TRUE.equals(m.get("inicioLibre")));
            if (!Set.of("CREAR", "CANCELAR", "REPROGRAMAR", "NINGUNA").contains(fixture.operacion())
                    || resultado.putIfAbsent(id, fixture) != null) throw new IllegalStateException("Fixture ambiguo: " + id);
        }
        return new FixturesEvaluacion(resultado);
    }

    private static Ventana leerVentana(Object valor, String id) {
        if (!(valor instanceof Map<?, ?> v) || v.size() != 2
                || !(v.get("desde") instanceof String desde) || !(v.get("hasta") instanceof String hasta))
            throw new IllegalStateException("Ventana sin desde/hasta ISO-8601: " + id);
        return new Ventana(desde, hasta);
    }

    Optional<Fixture> buscar(String id) { return Optional.ofNullable(fixtures.get(id)); }

    Fixture exigir(Correctores.CasoEvaluacion caso) {
        Fixture f = fixtures.get(caso.id());
        if (f == null || caso.resultadoEsperado().tipo() == Correctores.TipoEsperado.OPERACION && f.operacion().equals("NINGUNA")
                || Set.of("CANCELAR", "REPROGRAMAR").contains(f.operacion()) && f.citasPrevias().isEmpty())
            throw new IllegalStateException("Caso sin fixture u objetivo explícito: " + caso.id());
        // g100-2: el objetivo se resuelve por completo antes de abrir Spring y de gastar saldo.
        var e = caso.resultadoEsperado();
        if (e.tipo() == Correctores.TipoEsperado.OPERACION) {
            boolean intervalosAnotados = f.ventana().isPresent() || f.ventanas().isPresent();
            if (f.ventana().isPresent() && f.ventanas().isPresent()
                    || intervalosAnotados && (f.inicioLibre() || e.inicio().isPresent())
                    || f.inicioLibre() && e.inicio().isPresent())
                throw new IllegalStateException("Objetivo temporal ambiguo: " + caso.id());
            if (f.ventanas().filter(List::isEmpty).isPresent())
                throw new IllegalStateException("Ventanas exige al menos un intervalo: " + caso.id());
            // Servicio/profesional omitidos son comodines; el destino necesita una anotación temporal explícita.
            if (e.servicio().filter(n -> !SERVICIOS.contains(n)).isPresent()
                    || e.profesional().filter(n -> !PROFESIONALES.contains(n)).isPresent())
                throw new IllegalStateException("Objetivo fuera del catálogo: " + caso.id());
            try {
                f.intervalos().forEach(Ventana::validar);
                var ordenados = f.intervalos().stream().sorted(Comparator.comparing(v -> OffsetDateTime.parse(v.desde()).toInstant())).toList();
                for (int i = 1; i < ordenados.size(); i++) {
                    if (OffsetDateTime.parse(ordenados.get(i - 1).hasta()).toInstant()
                            .isAfter(OffsetDateTime.parse(ordenados.get(i).desde()).toInstant()))
                        throw new IllegalArgumentException("Las ventanas no admiten solapes.");
                }
                e.inicio().ifPresent(v -> java.time.LocalDateTime.parse(v, INICIO));
            } catch (java.time.DateTimeException | IllegalArgumentException ex) {
                throw new IllegalStateException("Inicio o ventana con formato o intervalo inválido: " + caso.id(), ex);
            }
        }
        return f;
    }
}
