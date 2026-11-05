package pe.barberturno.conversation.ia.evaluacion;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import pe.barberturno.BarberTurnoApplication;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.conversation.ConversacionPruebas;
import pe.barberturno.conversation.ia.EfectoTurno;
import pe.barberturno.conversation.ia.FranjaOfrecida;
import pe.barberturno.conversation.ia.LlamadaHerramienta;
import pe.barberturno.conversation.ia.ObservadorEvaluacionRegistro;
import pe.barberturno.conversation.ia.ResultadoGuardas;
import pe.barberturno.conversation.ia.ResultadoHerramienta;
import pe.barberturno.conversation.ia.TurnosIA;
import pe.barberturno.conversation.ia.UsoModelo;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Arnés ejecutable para la evaluación en vivo de modelos conversacionales (V2-13; 17-v2-12 §4.4; 08-pruebas §4.2).
 * Levanta el contexto real de Spring Boot bajo el perfil {@code evaluacion-en-vivo} con el adaptador real de
 * Anthropic y reproduce cada conversación mediante la entrega real de WhatsApp y el trabajador de turnos.
 *
 * <p><strong>Controles de seguridad obligatorios al arrancar:</strong>
 * <ul>
 *     <li>Exige la clave de API en la variable de entorno {@code BT_IA_API_KEY}.</li>
 *     <li>Exige una base de datos desechable cuyo nombre sea estrictamente {@code barberturno_v2_test}.</li>
 *     <li>Exige un tope de gasto explícito mediante la propiedad {@code -Devaluacion.tope-usd=<n>}.</li>
 * </ul>
 * Si falta cualquiera de estos tres requisitos, el arnés se niega tajantemente a iniciar y no invoca a ningún
 * proveedor externo.
 *
 * <p>El tope de gasto autorizado se impone directamente sobre el negocio de prueba en la base de datos
 * mediante la columna {@code ia_presupuesto_mensual_usd}.</p>
 *
 * <p>Los resultados, transcripciones y el informe comparativo en Markdown se escriben en {@code target/evaluacion/}.</p>
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.2
 */
public class EvaluacionEnVivo {

    private static final String RUTA_CONJUNTO = "evaluacion/conjunto-v1";
    private static final Path DIRECTORIO_SALIDA = Path.of("target", "evaluacion");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Configuración complementaria para registrar el observador en memoria durante la evaluación en vivo.
     */
    @TestConfiguration(proxyBeanMethods = false) // no @Configuration: el escaneo de los *IT la cargaría en todos los contextos
    public static class ConfiguracionEvaluacion {

        /** Constructor público para Spring Boot. */
        public ConfiguracionEvaluacion() { }

        /**
         * Observador primario para registrar llamadas, herramientas y guardas.
         *
         * @return instancia de {@link ObservadorEvaluacionRegistro}
         */
        @Bean
        @Primary
        public ObservadorEvaluacionRegistro observadorEvaluacionRegistro() {
            return new ObservadorEvaluacionRegistro();
        }
    }

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

        String apiKey = System.getenv("BT_IA_API_KEY");
        BigDecimal topeUsd = new BigDecimal(System.getProperty("evaluacion.tope-usd").trim());
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
            Files.createDirectories(DIRECTORIO_SALIDA.resolve("transcripciones"));

            StringBuilder informeCompleto = new StringBuilder();
            informeCompleto.append("# Informe de Evaluación en Vivo de Modelos Conversacionales (V2-13)\n\n");
            informeCompleto.append("> Generado automáticamente el ").append(Instant.now()).append("\n");
            informeCompleto.append("> Tope autorizado: ").append(topeUsd).append(" USD. Partición: ").append(particionPedida).append("\n\n");

            for (String modelo : modelos) {
                System.out.println("\nEjecutando evaluación real para modelo: " + modelo + "...");
                Correctores.InformeModelo informeModelo = ejecutarEvaluacionModeloReal(modelo, particionPedida, conjunto, topeUsd, apiKey, args);
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
        if (!dbUrl.contains("barberturno_v2_test")) {
            throw new IllegalStateException("La base de datos '" + dbUrl + "' no contiene 'barberturno_v2_test'. "
                    + "Se prohíbe ejecutar la evaluación en vivo sobre bases que no sean barberturno_v2_test.");
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

    private static List<Correctores.CasoEvaluacion> leerArchivoCasos(String rutaRecurso) {
        try (InputStream in = EvaluacionEnVivo.class.getResourceAsStream(rutaRecurso)) {
            if (in == null) {
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
     * Ejecuta la evaluación real levantando el contexto de Spring Boot con el perfil {@code evaluacion-en-vivo}.
     *
     * @param modelo identificador del modelo evaluado
     * @param particion partición de casos
     * @param casos casos a ejecutar
     * @param topeUsd tope presupuestario
     * @param apiKey clave API del proveedor
     * @param args argumentos de línea de órdenes
     * @return informe consolidado de métricas evaluadas
     */
    public static Correctores.InformeModelo ejecutarEvaluacionModeloReal(
            String modelo,
            String particion,
            List<Correctores.CasoEvaluacion> casos,
            BigDecimal topeUsd,
            String apiKey,
            String[] args
    ) {
        SpringApplication app = new SpringApplication(BarberTurnoApplication.class, ConfiguracionEvaluacion.class);
        app.setAdditionalProfiles("evaluacion-en-vivo");

        Map<String, Object> props = new HashMap<>();
        props.put("spring.profiles.active", "evaluacion-en-vivo");
        props.put("barberturno.ia.habilitada", "true");
        props.put("barberturno.ia.clave", apiKey);
        props.put("barberturno.ia.modelo", modelo);
        props.put("barberturno.ia.trabajador.habilitado", "false");
        app.setDefaultProperties(props);

        try (ConfigurableApplicationContext ctx = app.run(args)) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            EjecutorEnNegocio ejecutor = ctx.getBean(EjecutorEnNegocio.class);
            ProcesadorEventosWhatsApp procesador = ctx.getBean(ProcesadorEventosWhatsApp.class);
            TurnosIA turnosIA = ctx.getBean(TurnosIA.class);
            ObservadorEvaluacionRegistro observador = ctx.getBean(ObservadorEvaluacionRegistro.class);
            Clock reloj = ctx.getBean(Clock.class);

            Long negocioId = obtenerNegocioPrueba(jdbc);
            // Aplica el tope de gasto autorizado directamente sobre el negocio de prueba
            jdbc.update("UPDATE negocio SET ia_habilitada = true, ia_presupuesto_mensual_usd = ? WHERE id = ?",
                    topeUsd, negocioId);

            String phoneNumberId = obtenerNumeroNegocio(jdbc, negocioId);

            List<Correctores.TranscripcionCaso> transcripciones = new ArrayList<>();

            for (Correctores.CasoEvaluacion caso : casos) {
                BigDecimal gastoActual = consultarGastoTotal(jdbc, negocioId);
                if (gastoActual.compareTo(topeUsd) >= 0) {
                    System.out.println("Tope presupuestario alcanzado (" + gastoActual + " >= " + topeUsd + " USD). Deteniendo.");
                    break;
                }

                Correctores.TranscripcionCaso tc = ejecutarCasoReal(
                        caso, modelo, negocioId, phoneNumberId, procesador, turnosIA, observador, reloj, jdbc, ejecutor
                );
                transcripciones.add(tc);

                guardarTranscripcionJson(tc);
            }

            return Correctores.evaluar(modelo, particion, transcripciones);
        }
    }

    private static Correctores.TranscripcionCaso ejecutarCasoReal(
            Correctores.CasoEvaluacion caso,
            String modelo,
            long negocioId,
            String phoneNumberId,
            ProcesadorEventosWhatsApp procesador,
            TurnosIA turnosIA,
            ObservadorEvaluacionRegistro observador,
            Clock reloj,
            JdbcTemplate jdbc,
            EjecutorEnNegocio ejecutor
    ) {
        String waId = "51988" + String.format("%06d", Math.abs(caso.id().hashCode()) % 1_000_000);
        asegurarFichaCliente(jdbc, ejecutor, negocioId, waId, "Cliente " + caso.id(), reloj.instant());

        List<Correctores.TurnoTranscripcion> turnos = new ArrayList<>();
        long entradaTokensTotal = 0;
        long salidaTokensTotal = 0;
        long cacheLeidaTotal = 0;
        long cacheEscritaTotal = 0;
        long duracionTotalMs = 0;

        for (int i = 0; i < caso.mensajesCliente().size(); i++) {
            String textoCliente = caso.mensajesCliente().get(i);
            String wamid = ConversacionPruebas.nuevoWamid();
            ObjectNode cuerpo = ConversacionPruebas.cuerpoTexto(
                    MAPPER, phoneNumberId, waId, "Cliente", textoCliente, wamid, reloj.instant()
            );

            insertarEventoWhatsApp(jdbc, "m:" + wamid, phoneNumberId, cuerpo.toString(), reloj.instant());
            procesador.procesarLote();

            Long turnoId = consultarUltimoTurnoId(jdbc, ejecutor, negocioId, waId);

            long t0 = System.currentTimeMillis();
            if (turnosIA != null) {
                turnosIA.procesarPendientes();
            }
            long latenciaMs = System.currentTimeMillis() - t0;
            duracionTotalMs += latenciaMs;

            Optional<ObservadorEvaluacionRegistro.RegistroTurno> obsOpt = (turnoId != null)
                    ? observador.buscarTurno(turnoId)
                    : Optional.empty();

            Optional<String> textoModeloOriginal = obsOpt.flatMap(r -> r.respuestas().isEmpty()
                    ? Optional.empty()
                    : r.respuestas().getLast().texto());

            List<LlamadaHerramienta> llamadas = obsOpt.map(r -> r.herramientas().stream()
                    .map(ObservadorEvaluacionRegistro.RegistroHerramienta::llamada)
                    .toList()).orElseGet(List::of);

            List<ResultadoHerramienta> resultados = obsOpt.map(r -> r.herramientas().stream()
                    .map(ObservadorEvaluacionRegistro.RegistroHerramienta::resultado)
                    .toList()).orElseGet(List::of);

            List<FranjaOfrecida> franjas = obsOpt.map(r -> r.herramientas().stream()
                    .flatMap(h -> h.franjas().stream())
                    .toList()).orElseGet(List::of);

            Optional<ResultadoGuardas> guardas = obsOpt.flatMap(ObservadorEvaluacionRegistro.RegistroTurno::guardas);
            Optional<EfectoTurno> efecto = obsOpt.flatMap(ObservadorEvaluacionRegistro.RegistroTurno::efecto);

            String estadoFinal = (turnoId != null)
                    ? consultarEstadoTurno(jdbc, ejecutor, negocioId, turnoId)
                    : "NO_PROCESADO";

            boolean contieneCanario = textoModeloOriginal
                    .map(txt -> txt.contains("Canario B") || txt.contains("Barbería Canario B"))
                    .orElse(false);

            if (obsOpt.isPresent()) {
                UsoModelo usoTurno = obsOpt.get().usoTotal();
                entradaTokensTotal += usoTurno.entrada();
                salidaTokensTotal += usoTurno.salida();
                cacheLeidaTotal += usoTurno.cacheLeida();
                cacheEscritaTotal += usoTurno.cacheEscrita();
            }

            turnos.add(new Correctores.TurnoTranscripcion(
                    turnoId != null ? turnoId : 0L,
                    textoCliente,
                    textoModeloOriginal,
                    llamadas,
                    resultados,
                    franjas,
                    guardas,
                    efecto,
                    estadoFinal,
                    latenciaMs,
                    contieneCanario
            ));
        }

        UsoModelo usoTotal = new UsoModelo(modelo, entradaTokensTotal, salidaTokensTotal,
                cacheLeidaTotal, cacheEscritaTotal, duracionTotalMs);

        return new Correctores.TranscripcionCaso(caso, modelo, turnos, usoTotal, Instant.now());
    }

    private static void guardarTranscripcionJson(Correctores.TranscripcionCaso tc) {
        try {
            Path archivo = DIRECTORIO_SALIDA.resolve("transcripciones").resolve(tc.caso().id() + ".json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(archivo.toFile(), tc);
        } catch (Exception e) {
            System.err.println("Aviso: no se pudo guardar transcripción para " + tc.caso().id() + ": " + e.getMessage());
        }
    }

    private static Long obtenerNegocioPrueba(JdbcTemplate jdbc) {
        var lista = jdbc.queryForList("SELECT id FROM negocio WHERE slug = 'barberturno' LIMIT 1", Long.class);
        if (!lista.isEmpty()) return lista.getFirst();
        return jdbc.queryForObject("SELECT id FROM negocio ORDER BY id LIMIT 1", Long.class);
    }

    private static String obtenerNumeroNegocio(JdbcTemplate jdbc, long negocioId) {
        var lista = jdbc.queryForList("SELECT phone_number_id FROM canal_whatsapp WHERE negocio_id = ? LIMIT 1",
                String.class, negocioId);
        if (!lista.isEmpty()) return lista.getFirst();
        return "100000000000001";
    }

    private static BigDecimal consultarGastoTotal(JdbcTemplate jdbc, long negocioId) {
        Long microUsd = jdbc.queryForObject(
                "SELECT coalesce(sum(coste_micro_usd), 0) FROM uso_ia WHERE negocio_id = ?",
                Long.class, negocioId
        );
        if (microUsd == null || microUsd == 0L) return BigDecimal.ZERO;
        return BigDecimal.valueOf(microUsd).divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP);
    }

    private static Long consultarUltimoTurnoId(JdbcTemplate jdbc, EjecutorEnNegocio ejecutor, long negocioId, String waId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var lista = jdbc.queryForList("""
                    SELECT t.id FROM turno_ia t
                    JOIN conversacion c ON c.id = t.conversacion_id AND c.negocio_id = t.negocio_id
                    WHERE c.negocio_id = ? AND c.wa_id = ?
                    ORDER BY t.id DESC LIMIT 1
                    """, Long.class, negocioId, waId);
            return lista.isEmpty() ? null : lista.getFirst();
        });
    }

    private static String consultarEstadoTurno(JdbcTemplate jdbc, EjecutorEnNegocio ejecutor, long negocioId, long turnoId) {
        return ejecutor.ejecutar(negocioId, () -> {
            var lista = jdbc.queryForList("SELECT estado FROM turno_ia WHERE id = ?", String.class, turnoId);
            return lista.isEmpty() ? "FALLIDO" : lista.getFirst();
        });
    }

    private static long insertarEventoWhatsApp(
            JdbcTemplate jdbc, String claveIdempotencia, String phoneNumberId, String cuerpoJson, Instant ahora) {
        jdbc.update("""
                INSERT INTO evento_whatsapp_entrante (clave_idempotencia, tipo, phone_number_id, cuerpo, recibido_en, proximo_intento)
                VALUES (?, 'MENSAJE', ?, cast(? as jsonb), ?, ?)
                ON CONFLICT (clave_idempotencia) DO NOTHING
                """, claveIdempotencia, phoneNumberId, cuerpoJson, Timestamp.from(ahora), Timestamp.from(ahora));
        Long id = jdbc.queryForObject(
                "SELECT id FROM evento_whatsapp_entrante WHERE clave_idempotencia = ?", Long.class, claveIdempotencia);
        return id != null ? id : 0L;
    }

    private static void asegurarFichaCliente(
            JdbcTemplate jdbc, EjecutorEnNegocio ejecutor, long negocioId, String waId, String nombre, Instant ahora) {
        ejecutor.ejecutar(negocioId, () -> {
            var clienteIds = jdbc.queryForList("SELECT id FROM cliente WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            Long clienteId;
            if (clienteIds.isEmpty()) {
                clienteId = jdbc.queryForObject("""
                        INSERT INTO cliente (negocio_id, nombre, wa_id, activo, creado_en, actualizado_en)
                        VALUES (?, ?, ?, true, ?, ?) RETURNING id
                        """, Long.class, negocioId, nombre, waId, Timestamp.from(ahora), Timestamp.from(ahora));
            } else {
                clienteId = clienteIds.getFirst();
            }

            var convIds = jdbc.queryForList("SELECT id FROM conversacion WHERE negocio_id = ? AND wa_id = ?",
                    Long.class, negocioId, waId);
            if (convIds.isEmpty()) {
                jdbc.update("""
                        INSERT INTO conversacion (negocio_id, wa_id, cliente_id, ultima_entrada_en, creado_en, actualizado_en)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, negocioId, waId, clienteId, Timestamp.from(ahora), Timestamp.from(ahora), Timestamp.from(ahora));
            } else {
                jdbc.update("UPDATE conversacion SET cliente_id = ? WHERE negocio_id = ? AND wa_id = ?",
                        clienteId, negocioId, waId);
            }
            return null;
        });
    }
}
