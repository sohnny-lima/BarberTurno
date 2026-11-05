package pe.barberturno.conversation.ia.evaluacion;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import pe.barberturno.BarberTurnoApplication;
import pe.barberturno.conversation.ia.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ensayo explícito fuera de CI. Valida destino antes del refresh, impone configuración y reserva
 * el máximo global antes de cada llamada. Nunca ejecutar sin P2-03 y presupuesto aprobado.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public final class EvaluacionEnVivo {
    private static final String RUTA_CONJUNTO = "evaluacion/conjunto-v1";
    private static final Path DIRECTORIO_SALIDA = Path.of("target", "evaluacion");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    static final Instant RELOJ = Instant.parse("2026-11-02T15:00:00Z");

    private EvaluacionEnVivo() { }

    @TestConfiguration(proxyBeanMethods = false)
    public static class ConfiguracionEvaluacion {
        @Bean @Primary
        pe.barberturno.support.RelojAjustable relojEvaluacion() { var reloj = new pe.barberturno.support.RelojAjustable(); reloj.fijar(RELOJ); return reloj; }

        @Bean @Primary
        ObservadorEvaluacionRegistro observadorEvaluacionRegistro() { return new ObservadorEvaluacionRegistro(); }

        @Bean
        static MonitorModelo monitorModelo(ObjectProvider<IaPropiedades> propiedades, SeguridadEvaluacion.Saldo saldo) {
            return new MonitorModelo(propiedades, saldo, true);
        }
    }

    /** El escaneo del arranque real no incorpora componentes ni configuraciones del classpath de pruebas. */
    static final class ExcluirPruebas extends org.springframework.boot.context.TypeExcludeFilter {
        @Override public boolean equals(Object otro) { return otro != null && otro.getClass() == getClass(); }
        @Override public int hashCode() { return getClass().hashCode(); }
        @Override
        public boolean match(org.springframework.core.type.classreading.MetadataReader lector,
                org.springframework.core.type.classreading.MetadataReaderFactory fabrica) throws java.io.IOException {
            var origen = BarberTurnoApplication.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            String recurso = lector.getResource().getURL().toExternalForm();
            return !recurso.startsWith(origen) && !recurso.startsWith("jar:" + origen + "!");
        }
    }

    static final class MonitorModelo implements BeanPostProcessor {
        private final ObjectProvider<IaPropiedades> propiedades;
        private final SeguridadEvaluacion.Saldo saldo;
        private final boolean exigirProveedorReal;
        private final List<String> peticiones = new ArrayList<>();
        private String modelo;
        private String esfuerzo;
        MonitorModelo(ObjectProvider<IaPropiedades> propiedades, SeguridadEvaluacion.Saldo saldo) {
            this(propiedades, saldo, false);
        }
        MonitorModelo(ObjectProvider<IaPropiedades> propiedades, SeguridadEvaluacion.Saldo saldo, boolean exigirProveedorReal) {
            this.propiedades = propiedades; this.saldo = saldo;
            this.exigirProveedorReal = exigirProveedorReal;
        }
        @Override public Object postProcessAfterInitialization(Object bean, String nombre) {
            if (!(bean instanceof ModeloConversacional delegado)) return bean;
            if (exigirProveedorReal && !delegado.getClass().getName().equals("pe.barberturno.conversation.ia.ModeloAnthropic"))
                throw new IllegalStateException("El ensayo en vivo exige ModeloAnthropic; se detectó otro adaptador.");
            return (ModeloConversacional) peticion -> {
                var p = propiedades.getObject();
                SeguridadEvaluacion.validarModelo(p, modelo, esfuerzo);
                String texto = ModeloConversacionalFalso.extraerPeticionCompleta(peticion);
                peticiones.add(texto);
                // Registrar la fuga para el corrector, y evitar enviarla a un proveedor externo.
                if (SeguridadEvaluacion.contieneCanario(texto)) throw new IllegalStateException("Canario detectado antes del proveedor.");
                saldo.reservar(peticion, p);
                var respuesta = delegado.responder(peticion);
                if (!p.modelo().equals(respuesta.uso().modelo()))
                    throw new IllegalStateException("El proveedor devolvió un modelo distinto al solicitado.");
                return respuesta;
            };
        }
        List<String> desde(int indice) { return List.copyOf(peticiones.subList(indice, peticiones.size())); }
        int cantidad() { return peticiones.size(); }
        void configurar(String modelo, String esfuerzo) {
            SeguridadEvaluacion.validarModelo(propiedades.getObject(), modelo, esfuerzo);
            this.modelo = modelo; this.esfuerzo = esfuerzo;
        }
    }

    /**
     * Ejecuta únicamente el ensayo explícito, nunca como prueba ni parte de verify.
     * @param args argumentos de Spring que forman parte del entorno efectivo validado antes del refresh
     * @throws Exception si falta un prerrequisito o no se pueden persistir las transcripciones
     */
    public static void main(String[] args) throws Exception {
        verificarPrerrequisitos();
        var saldo = new SeguridadEvaluacion.Saldo(SeguridadEvaluacion.validarTope(System.getProperty("evaluacion.tope-usd")));
        String particion = System.getProperty("evaluacion.particion", "PRUEBA");
        String esfuerzo = System.getProperty("evaluacion.esfuerzo", "low");
        var casos = cargarConjuntoCasos(particion);
        // Valida los fixtures de TODOS los casos antes de abrir Spring o escribir datos.
        var fixtures = FixturesEvaluacion.cargar();
        for (var caso : casos) fixtures.exigir(caso);
        if (casos.isEmpty()) throw new IllegalStateException("La partición no contiene casos.");
        Files.createDirectories(DIRECTORIO_SALIDA.resolve("transcripciones"));
        StringBuilder informe = new StringBuilder("# Evaluación en vivo\n\nConjunto BORRADOR; no acredita selección del modelo.\n\n");
        informe.append("Esfuerzo impuesto y verificado en IaPropiedades: ").append(esfuerzo).append(".\n\n");
        for (String modelo : System.getProperty("evaluacion.modelos", "claude-haiku-5-5").split(",")) {
            var resultado = ejecutarModelo(modelo.trim(), esfuerzo, particion, casos, fixtures, saldo, args);
            informe.append(resultado.aMarkdown()).append("\n");
        }
        informe.append("Máximo global reservado (incluye errores y todos los modelos): ")
                .append(BigDecimal.valueOf(saldo.reservado(), 6)).append(" USD.\n");
        Files.writeString(DIRECTORIO_SALIDA.resolve("informe.md"), informe, StandardCharsets.UTF_8);
    }

    /**
     * Rechaza clave y presupuesto ausentes sin crear Spring; el destino se valida sobre su entorno efectivo.
     * @throws IllegalStateException si no se dispone de clave y tope explícitos
     */
    public static void verificarPrerrequisitos() {
        verificarPrerrequisitos(System.getenv("BT_IA_API_KEY"), System.getProperty("evaluacion.tope-usd"));
    }

    static void verificarPrerrequisitos(String clave, String tope) {
        exigirClave(clave);
        SeguridadEvaluacion.validarTope(tope);
    }

    static void exigirClave(String clave) {
        if (clave == null || clave.isBlank()) throw new IllegalStateException("Falta BT_IA_API_KEY; ensayo rechazado antes de abrir Spring.");
    }

    static void configurarEntorno(ConfigurableEnvironment env, String modelo, String esfuerzo, String clave) {
        exigirClave(clave);
        if (!List.of("low", "medium", "high").contains(esfuerzo) || !modelo.matches("claude-[a-z0-9-]+"))
            throw new IllegalStateException("Modelo o esfuerzo inválido.");
        // Prioridad superior a YAML, argumentos y variables; el destino se toma del entorno real ya resuelto.
        Map<String, Object> fijas = new HashMap<>();
        fijas.put("barberturno.ia.habilitada", true);
        fijas.put("barberturno.ia.clave", clave);
        fijas.put("barberturno.ia.modelo", modelo);
        fijas.put("barberturno.ia.esfuerzo", esfuerzo);
        for (String p : List.of("barberturno.ia.trabajador.habilitado", "barberturno.whatsapp.procesador.habilitado",
                "barberturno.whatsapp.salida.habilitado", "barberturno.whatsapp.bandeja.habilitado",
                "barberturno.whatsapp.conversacion.retencion.habilitada")) fijas.put(p, false);
        fijas.put("barberturno.ia.trabajador.lote", 1);
        fijas.put("barberturno.whatsapp.salida.graph.token", "");
        fijas.put("barberturno.admin.correo", ""); fijas.put("barberturno.admin.password", "");
        fijas.put("server.port", 0);
        fijas.put("spring.datasource.hikari.maximum-pool-size", 4);
        fijas.put("spring.jpa.hibernate.ddl-auto", "validate");
        fijas.put("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource");
        fijas.put("spring.datasource.hikari.connection-init-sql", "SET lock_timeout = '5s'");
        env.getPropertySources().addFirst(new MapPropertySource("ensayo-obligatorio", fijas));
        SeguridadEvaluacion.validarDestino(env);
    }

    private static Correctores.InformeModelo ejecutarModelo(String modelo, String esfuerzo, String particion,
            List<Correctores.CasoEvaluacion> casos, FixturesEvaluacion fixtures, SeguridadEvaluacion.Saldo saldo, String[] args) throws Exception {
        SpringApplication app = new SpringApplication(BarberTurnoApplication.class, ConfiguracionEvaluacion.class);
        app.setAdditionalProfiles("test", "evaluacion-en-vivo");
        app.addInitializers(ctx -> {
            configurarEntorno(ctx.getEnvironment(), modelo, esfuerzo, System.getenv("BT_IA_API_KEY"));
            SeguridadEvaluacion.comprobarConexion(ctx.getEnvironment());
            ctx.getBeanFactory().registerSingleton("saldoEnsayo", saldo);
            ctx.getBeanFactory().registerSingleton("excluirComponentesDePrueba", new ExcluirPruebas());
        });
        try (var ctx = app.run(args)) {
            var p = ctx.getBean(IaPropiedades.class);
            SeguridadEvaluacion.validarModelo(p, modelo, esfuerzo);
            var monitor = ctx.getBean(MonitorModelo.class);
            monitor.configurar(modelo, esfuerzo);
            var escenario = new EscenarioEvaluacion(ctx, fixtures);
            escenario.exigirColaAislada();
            List<Correctores.TranscripcionCaso> transcripciones = new ArrayList<>();
            for (var caso : casos) {
                var transcripcion = escenario.ejecutar(caso, p.modelo(), monitor);
                transcripciones.add(transcripcion);
                Path carpeta = DIRECTORIO_SALIDA.resolve("transcripciones").resolve(modelo);
                Files.createDirectories(carpeta);
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(carpeta.resolve(caso.id() + ".json").toFile(), transcripcion);
            }
            return Correctores.evaluar(p.modelo(), particion, transcripciones);
        }
    }
    /**
     * Lee el conjunto congelado como BORRADOR sin modificarlo ni deducir objetivos por el nombre del caso.
     * @param particionFiltro AJUSTE, PRUEBA o TODAS
     * @return casos de la partición, cuya correspondencia con fixtures se valida antes del contexto
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
                throw new IllegalStateException("Falta un archivo del conjunto: " + rutaRecurso);
            }
            String contenido = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parsearCasosYaml(contenido);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar íntegramente el archivo del conjunto: " + rutaRecurso, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Correctores.CasoEvaluacion> parsearCasosYaml(String yamlStr) {
        List<Correctores.CasoEvaluacion> casos = new ArrayList<>();
        LoaderOptions opciones = new LoaderOptions(); opciones.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(opciones));
        Object obj = yaml.load(yamlStr);
        if (!(obj instanceof Map<?, ?> mapaRaw)) throw new IllegalArgumentException("El conjunto debe ser un mapa YAML.");
        Map<String, Object> mapa = (Map<String, Object>) mapaRaw;

        String categoria = (String) mapa.getOrDefault("categoria", "general");
        List<?> casosRaw = (List<?>) mapa.get("conversaciones");
        if (casosRaw == null || casosRaw.isEmpty()) throw new IllegalArgumentException("Archivo de conversaciones vacío.");

        for (Object item : casosRaw) {
            if (!(item instanceof Map<?, ?> mRaw)) throw new IllegalArgumentException("Conversación YAML inválida.");
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
            throw new IllegalArgumentException("Cada conversación exige un resultado esperado explícito.");
        }
        String tipoStr = Objects.requireNonNull((String) espMap.get("tipo"), "Tipo esperado obligatorio");
        Correctores.TipoEsperado tipo = Correctores.TipoEsperado.valueOf(tipoStr.trim());
        Optional<String> servicio = Optional.ofNullable((String) espMap.get("servicio"));
        Optional<String> profesional = Optional.ofNullable((String) espMap.get("profesional"));
        Optional<String> inicio = Optional.ofNullable((String) espMap.get("inicio"));
        Optional<String> motivo = Optional.ofNullable((String) espMap.get("motivoDerivacion"));
        return new Correctores.ResultadoEsperado(tipo, servicio, profesional, inicio, motivo);
    }


}
