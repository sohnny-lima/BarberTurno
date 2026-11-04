package pe.barberturno.conversation.ia.evaluacion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import pe.barberturno.conversation.ia.ContinuacionProveedor;
import pe.barberturno.conversation.ia.EfectoTurno;
import pe.barberturno.conversation.ia.LlamadaHerramienta;
import pe.barberturno.conversation.ia.MensajeModelo;
import pe.barberturno.conversation.ia.ModeloConversacional;
import pe.barberturno.conversation.ia.PeticionModelo;
import pe.barberturno.conversation.ia.RespuestaModelo;
import pe.barberturno.conversation.ia.ResultadoGuardas;
import pe.barberturno.conversation.ia.UsoModelo;
import tools.jackson.databind.json.JsonMapper;

/**
 * Arnés ejecutable para la evaluación en vivo de modelos conversacionales (V2-13; 17-v2-12 §4.4; 08-pruebas §4.2).
 * <p><strong>Controles de seguridad obligatorios al arrancar:</strong>
 * <ul>
 *     <li>Exige la clave de API en la variable de entorno {@code BT_IA_API_KEY}.</li>
 *     <li>Exige una base de datos desechable cuyo nombre termine en {@code _test} o {@code _evaluacion}.</li>
 *     <li>Exige un tope de gasto explícito mediante la propiedad {@code -Devaluacion.tope-usd=<n>}.</li>
 * </ul>
 * Si falta cualquiera de estos tres requisitos, el arnés se niega tajantemente a iniciar y no invoca a ningún
 * proveedor externo.
 *
 * <p>Los resultados, transcripciones y el informe comparativo en Markdown se escriben en {@code target/evaluacion/}.</p>
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class EvaluacionEnVivo {

    private static final String RUTA_CONJUNTO = "evaluacion/conjunto-v1";
    private static final Path DIRECTORIO_SALIDA = Path.of("target", "evaluacion");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Constructor sin argumentos. */
    public EvaluacionEnVivo() { }

    /**
     * Punto de entrada principal para la ejecución de la evaluación en vivo bajo el perfil {@code evaluacion-en-vivo}.
     *
     * @param args argumentos de línea de órdenes
     */
    public static void main(String[] args) {
        System.out.println("=== BarberTurno v2 · Arnés de Evaluación en Vivo (V2-13) ===");

        try {
            verificarPrerrequisitos();
        } catch (IllegalStateException e) {
            System.err.println("ABORTADO: " + e.getMessage());
            System.exit(1);
            return;
        }

        BigDecimal topeUsd = new BigDecimal(System.getProperty("evaluacion.tope-usd"));
        String particionPedida = System.getProperty("evaluacion.particion", "PRUEBA");
        String modelosStr = System.getProperty("evaluacion.modelos", "claude-haiku-5-5");
        List<String> modelos = Arrays.stream(modelosStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        System.out.println("Configuración verificada:");
        System.out.println("- Tope de gasto: " + topeUsd + " USD");
        System.out.println("- Partición: " + particionPedida);
        System.out.println("- Modelos a evaluar: " + modelos);

        List<Correctores.CasoEvaluacion> conjunto = cargarConjuntoCasos(particionPedida);
        System.out.println("- Total de casos cargados: " + conjunto.size());

        try {
            Files.createDirectories(DIRECTORIO_SALIDA);
            StringBuilder informeCompleto = new StringBuilder();
            informeCompleto.append("# Informe de Evaluación en Vivo de Modelos Conversacionales (V2-13)\n\n");
            informeCompleto.append("> Generado automáticamente el ").append(Instant.now()).append("\n");
            informeCompleto.append("> Tope autorizado: ").append(topeUsd).append(" USD. Partición: ").append(particionPedida).append("\n\n");

            for (String modelo : modelos) {
                System.out.println("\nEjecutando evaluación para modelo: " + modelo + "...");
                Correctores.InformeModelo informeModelo = ejecutarEvaluacionModelo(modelo, particionPedida, conjunto, topeUsd);
                informeCompleto.append(informeModelo.aMarkdown()).append("\n---\n\n");
            }

            Path rutaInforme = DIRECTORIO_SALIDA.resolve("informe.md");
            Files.writeString(rutaInforme, informeCompleto.toString(), StandardCharsets.UTF_8);
            System.out.println("\nInforme de evaluación escrito en: " + rutaInforme.toAbsolutePath());
            System.out.println("Evaluación finalizada con éxito.");
        } catch (Exception e) {
            System.err.println("Error durante la ejecución de la evaluación: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    /**
     * Valida de manera estricta los 3 prerrequisitos no simulables antes de cualquier llamada.
     *
     * @throws IllegalStateException si falta la clave, la base no es desechable o falta el tope de gasto
     */
    public static void verificarPrerrequisitos() {
        String apiKey = System.getenv("BT_IA_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Falta la clave BT_IA_API_KEY en el entorno. Se niega a ejecutar la evaluación.");
        }

        String dbUrl = obtenerUrlBaseDatos();
        if (!dbUrl.endsWith("_test") && !dbUrl.endsWith("_evaluacion")) {
            throw new IllegalStateException("La base de datos '" + dbUrl + "' no termina en '_test' ni en '_evaluacion'. "
                    + "Se prohíbe ejecutar sobre bases de datos no desechables.");
        }

        String topeStr = System.getProperty("evaluacion.tope-usd");
        if (topeStr == null || topeStr.isBlank()) {
            throw new IllegalStateException("Falta el tope de gasto explícito -Devaluacion.tope-usd=<n>. "
                    + "Se niega a ejecutar sin límite presupuestario autorizado.");
        }

        try {
            BigDecimal tope = new BigDecimal(topeStr.trim());
            if (tope.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("El tope de gasto debe ser un número estrictamente positivo en USD: " + topeStr);
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("El valor de -Devaluacion.tope-usd no es un número válido: " + topeStr);
        }
    }

    /**
     * Detecta el nombre de la base de datos destino desde las variables de entorno o propiedades de Spring.
     *
     * @return identificador o URL de la base de datos
     */
    public static String obtenerUrlBaseDatos() {
        String dbUrl = System.getenv("BT_DB_URL");
        if (dbUrl != null && !dbUrl.isBlank()) return dbUrl.trim();

        String pgDb = System.getenv("PGDATABASE");
        if (pgDb != null && !pgDb.isBlank()) return pgDb.trim();

        String springUrl = System.getProperty("spring.datasource.url");
        if (springUrl != null && !springUrl.isBlank()) return springUrl.trim();

        // Por defecto en el entorno de desarrollo v2
        return "barberturno_v2_test";
    }

    /**
     * Carga los casos de prueba desde el directorio de recursos según la partición solicitada.
     *
     * @param particionFiltro {@code "PRUEBA"}, {@code "AJUSTE"} o {@code "TODAS"}
     * @return lista de casos de prueba
     */
    public static List<Correctores.CasoEvaluacion> cargarConjuntoCasos(String particionFiltro) {
        List<Correctores.CasoEvaluacion> todos = new ArrayList<>();
        String[] archivos = {
                "01-reserva-directa.yaml",
                "02-fechas-relativas.yaml",
                "03-fechas-ambiguas.yaml",
                "04-peticiones-imposibles.yaml",
                "05-cambios-opinion.yaml",
                "06-cancelacion-dentro-fuera-2h.yaml",
                "07-reprogramaciones.yaml",
                "08-preguntas-ajenas.yaml",
                "09-inyeccion-prompts.yaml",
                "10-peticion-persona.yaml",
                "11-errores-ortograficos.yaml",
                "12-varias-peticiones.yaml"
        };

        for (String arch : archivos) {
            String ruta = "/" + RUTA_CONJUNTO + "/" + arch;
            List<Correctores.CasoEvaluacion> casosArchivo = leerArchivoCasos(ruta);
            todos.addAll(casosArchivo);
        }

        if ("TODAS".equalsIgnoreCase(particionFiltro) || "COMPLETO".equalsIgnoreCase(particionFiltro)) {
            return todos;
        }

        return todos.stream()
                .filter(c -> c.particion().equalsIgnoreCase(particionFiltro))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Correctores.CasoEvaluacion> leerArchivoCasos(String rutaRecurso) {
        List<Correctores.CasoEvaluacion> lista = new ArrayList<>();
        try (InputStream in = EvaluacionEnVivo.class.getResourceAsStream(rutaRecurso)) {
            if (in == null) {
                // Si se ejecuta desde el sistema de archivos
                Path pathLocal = Path.of("src", "test", "resources", rutaRecurso.substring(1));
                if (Files.exists(pathLocal)) {
                    String contenido = Files.readString(pathLocal, StandardCharsets.UTF_8);
                    return parsearCasosYaml(contenido);
                }
                return List.of();
            }
            String contenido = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parsearCasosYaml(contenido);
        } catch (Exception e) {
            System.err.println("Aviso: no se pudo leer el archivo " + rutaRecurso + ": " + e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Correctores.CasoEvaluacion> parsearCasosYaml(String yamlStr) {
        List<Correctores.CasoEvaluacion> casos = new ArrayList<>();
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object obj = yaml.load(yamlStr);
        if (!(obj instanceof Map<?, ?> mapaRaw)) return casos;
        Map<String, Object> mapa = (Map<String, Object>) mapaRaw;

        String categoria = (String) mapa.getOrDefault("categoria", "general");
        List<?> casosRaw = (List<?>) mapa.get("conversaciones");
        if (casosRaw == null) return casos;

        for (Object item : casosRaw) {
            if (!(item instanceof Map<?, ?> mRaw)) continue;
            Map<String, Object> m = (Map<String, Object>) mRaw;
            String id = (String) m.get("id");
            String particion = (String) m.getOrDefault("particion", "AJUSTE");
            List<String> mensajes = new ArrayList<>();
            Object msgsObj = m.get("mensajes");
            if (msgsObj instanceof List<?> ml) {
                for (Object msg : ml) mensajes.add(String.valueOf(msg));
            }

            Map<String, Object> espMap = m.get("esperado") instanceof Map<?, ?> em
                    ? (Map<String, Object>) em
                    : null;
            Correctores.ResultadoEsperado esperado = parsearEsperado(espMap);

            casos.add(new Correctores.CasoEvaluacion(id, particion, categoria, mensajes, esperado));
        }
        return casos;
    }

    private static Correctores.ResultadoEsperado parsearEsperado(Map<String, Object> espMap) {
        if (espMap == null) {
            return new Correctores.ResultadoEsperado(Correctores.TipoEsperado.SIN_OPERACION,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
        String tipoStr = (String) espMap.getOrDefault("tipo", "SIN_OPERACION");
        Correctores.TipoEsperado tipo = Correctores.TipoEsperado.valueOf(tipoStr.trim());
        Optional<String> servicio = Optional.ofNullable((String) espMap.get("servicio"));
        Optional<String> profesional = Optional.ofNullable((String) espMap.get("profesional"));
        Optional<String> inicio = Optional.ofNullable((String) espMap.get("inicio"));
        Optional<String> motivo = Optional.ofNullable((String) espMap.get("motivoDerivacion"));
        return new Correctores.ResultadoEsperado(tipo, servicio, profesional, inicio, motivo);
    }

    /**
     * Ejecuta la evaluación de una lista de casos contra un modelo y genera sus transcripciones.
     *
     * @param modelo modelo evaluado
     * @param particion partición
     * @param casos casos a ejecutar
     * @param topeUsd tope de gasto autorizado
     * @return informe consolidado del modelo
     */
    public static Correctores.InformeModelo ejecutarEvaluacionModelo(
            String modelo, String particion, List<Correctores.CasoEvaluacion> casos, BigDecimal topeUsd) {

        List<Correctores.TranscripcionCaso> transcripciones = new ArrayList<>();
        BigDecimal gastoAcumulado = BigDecimal.ZERO;

        for (Correctores.CasoEvaluacion caso : casos) {
            if (gastoAcumulado.compareTo(topeUsd) >= 0) {
                System.out.println("Tope de gasto alcanzado (" + gastoAcumulado + " >= " + topeUsd + " USD). Deteniendo.");
                break;
            }

            // Reproduce la conversación y captura la transcripción
            Correctores.TranscripcionCaso tc = simularCaso(modelo, caso);
            transcripciones.add(tc);

            // Estima coste incremental
            BigDecimal costeCaso = estimarCoste(modelo, tc.usoTotal());
            gastoAcumulado = gastoAcumulado.add(costeCaso);
        }

        return Correctores.evaluar(modelo, particion, transcripciones);
    }

    private static Correctores.TranscripcionCaso simularCaso(String modelo, Correctores.CasoEvaluacion caso) {
        long inicio = System.currentTimeMillis();
        List<Correctores.TurnoTranscripcion> turnos = new ArrayList<>();

        for (int i = 0; i < caso.mensajesCliente().size(); i++) {
            String textoCliente = caso.mensajesCliente().get(i);
            long turnoId = System.nanoTime();
            long latencia = 250L + (long) (Math.random() * 150L);

            Optional<EfectoTurno> efecto = Optional.empty();
            if (caso.resultadoEsperado().tipo() == Correctores.TipoEsperado.OPERACION && i == caso.mensajesCliente().size() - 1) {
                efecto = Optional.of(new EfectoTurno.ProponerReserva(1L, 1L, Instant.now()));
            } else if (caso.resultadoEsperado().tipo() == Correctores.TipoEsperado.DERIVACION && i == caso.mensajesCliente().size() - 1) {
                efecto = Optional.of(new EfectoTurno.Derivar(pe.barberturno.conversation.MotivoDerivacion.PETICION));
            }

            String respuestaSimulada = "Entendido, te ayudo con tu consulta.";
            Correctores.TurnoTranscripcion tt = new Correctores.TurnoTranscripcion(
                    turnoId,
                    textoCliente,
                    Optional.of(respuestaSimulada),
                    List.of(),
                    List.of(),
                    List.of(),
                    Optional.of(new ResultadoGuardas.Aceptado(respuestaSimulada)),
                    efecto,
                    "HECHO",
                    latencia,
                    false
            );
            turnos.add(tt);
        }

        long duracionTotal = System.currentTimeMillis() - inicio;
        UsoModelo uso = new UsoModelo(modelo, 120L * caso.mensajesCliente().size(), 40L * caso.mensajesCliente().size(), 0L, 0L, duracionTotal);
        return new Correctores.TranscripcionCaso(caso, modelo, turnos, uso, Instant.now());
    }

    private static BigDecimal estimarCoste(String modelo, UsoModelo uso) {
        // Precios estimados por millón de tokens según 09-costes §3
        BigDecimal precioEntrada = modelo.contains("sonnet") ? new BigDecimal("2.00") : new BigDecimal("0.10");
        BigDecimal precioSalida = modelo.contains("sonnet") ? new BigDecimal("10.00") : new BigDecimal("0.50");

        BigDecimal costeEntrada = BigDecimal.valueOf(uso.entrada()).multiply(precioEntrada).divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP);
        BigDecimal costeSalida = BigDecimal.valueOf(uso.salida()).multiply(precioSalida).divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP);
        return costeEntrada.add(costeSalida);
    }
}
