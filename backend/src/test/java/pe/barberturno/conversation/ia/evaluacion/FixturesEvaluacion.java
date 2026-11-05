package pe.barberturno.conversation.ia.evaluacion;

import java.time.Instant;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Anotaciones explícitas complementarias al conjunto BORRADOR, sin modificar sus 120 conversaciones. */
final class FixturesEvaluacion {
    record Fixture(String id, Instant reloj, String operacion, List<Instant> citasPrevias) { }
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
        return f;
    }
}
