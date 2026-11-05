package pe.barberturno.conversation.ia.evaluacion;

import java.time.Instant;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Anotaciones explícitas complementarias al conjunto BORRADOR, sin modificar sus 120 conversaciones. */
final class FixturesEvaluacion {
    record Fixture(String id, Instant reloj, String operacion, List<Instant> citasPrevias) { }
    /** Catálogo que siembra {@code EscenarioEvaluacion}: los nombres anotados en el conjunto deben estar aquí. */
    static final List<String> SERVICIOS = List.of("Corte clásico", "Barba");
    /** Profesionales que siembra {@code EscenarioEvaluacion}. */
    static final List<String> PROFESIONALES = List.of("Carlos", "Miguel");
    private static final java.time.format.DateTimeFormatter INICIO = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Map<String, Fixture> fixtures;
    private FixturesEvaluacion(Map<String, Fixture> fixtures) { this.fixtures = Map.copyOf(fixtures); }

    static FixturesEvaluacion cargar() {
        try (var in = FixturesEvaluacion.class.getResourceAsStream("/evaluacion/fixtures-v2-13.yaml")) {
            if (in == null) throw new IllegalStateException("Faltan fixtures explícitos.");
            var opciones = new LoaderOptions(); opciones.setAllowDuplicateKeys(false);
            List<?> filas = new Yaml(new SafeConstructor(opciones)).load(in);
            Map<String, Fixture> resultado = new LinkedHashMap<>();
            for (Object fila : filas) {
                Map<?, ?> m = (Map<?, ?>) fila;
                String id = m.get("id").toString();
                var citas = ((List<?>) m.get("citasPrevias")).stream().map(v -> Instant.parse(v.toString())).toList();
                Fixture fixture = new Fixture(id, Instant.parse(m.get("reloj").toString()), m.get("operacion").toString(), citas);
                if (!Set.of("CREAR", "CANCELAR", "REPROGRAMAR", "NINGUNA").contains(fixture.operacion())
                        || resultado.putIfAbsent(id, fixture) != null) throw new IllegalStateException("Fixture ambiguo: " + id);
            }
            return new FixturesEvaluacion(resultado);
        } catch (java.io.IOException ex) { throw new IllegalStateException("No se pudieron cargar fixtures.", ex); }
    }

    Fixture exigir(Correctores.CasoEvaluacion caso) {
        Fixture f = fixtures.get(caso.id());
        if (f == null || caso.resultadoEsperado().tipo() == Correctores.TipoEsperado.OPERACION && f.operacion().equals("NINGUNA")
                || Set.of("CANCELAR", "REPROGRAMAR").contains(f.operacion()) && f.citasPrevias().isEmpty())
            throw new IllegalStateException("Caso sin fixture u objetivo explícito: " + caso.id());
        // g100-2: el objetivo se resuelve por completo antes de abrir Spring y de gastar saldo.
        var e = caso.resultadoEsperado();
        if (e.tipo() == Correctores.TipoEsperado.OPERACION) {
            // Un campo sin anotar es un comodín (el cliente no lo fijó); uno anotado debe resolverse sin error en vivo.
            if (e.servicio().filter(n -> !SERVICIOS.contains(n)).isPresent()
                    || e.profesional().filter(n -> !PROFESIONALES.contains(n)).isPresent())
                throw new IllegalStateException("Objetivo fuera del catálogo: " + caso.id());
            try {
                e.inicio().ifPresent(v -> java.time.LocalDateTime.parse(v, INICIO));
            } catch (java.time.format.DateTimeParseException ex) {
                throw new IllegalStateException("Inicio con formato distinto de yyyy-MM-dd HH:mm: " + caso.id(), ex);
            }
        }
        return f;
    }
}
