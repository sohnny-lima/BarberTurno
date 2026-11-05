package pe.barberturno.conversation.ia;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Representación y lector seguro de guiones de conversación en formato YAML para pruebas deterministas
 * (17-v2-12 §4.4; CI-11; 08-pruebas §4.1).
 * Utiliza SnakeYAML seguro ({@link SafeConstructor}) y valida estrictamente el esquema: cualquier campo
 * no reconocido provoca un error de validación.
 *
 * @param nombre nombre identificativo del guion
 * @param negocio identificador del negocio ({@code "A"} o {@code "B"})
 * @param reloj instante inicial simulado
 * @param ficha si el cliente posee ficha creada y enlazada al inicio
 * @param iaNegocio si la IA está habilitada a nivel del negocio
 * @param limiteLlamadasDia límite diario local opcional a configurar en {@code negocio.ia_llamadas_dia_conversacion}
 * @param pasos secuencia de pasos a ejecutar
 * @author Sohnny Walter Lima Infanzón
 * @version 1.2
 */
public record GuionConversacion(
        String nombre,
        String negocio,
        Instant reloj,
        boolean ficha,
        boolean iaNegocio,
        Optional<Integer> limiteLlamadasDia,
        List<PasoGuion> pasos
) {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Set<String> CAMPOS_RAIZ = Set.of(
            "nombre", "negocio", "reloj", "ficha", "iaNegocio", "limiteLlamadasDia", "pasos"
    );
    private static final Set<String> CAMPOS_PASO = Set.of(
            "cliente", "modelo", "espera", "avanzarMinutos", "durante"
    );
    private static final Set<String> CAMPOS_CLIENTE = Set.of(
            "texto", "seleccion", "seleccionDelUltimoMensaje", "repetirSeleccion"
    );
    private static final Set<String> CAMPOS_DURANTE = Set.of(
            "texto"
    );
    private static final Set<String> CAMPOS_MODELO = Set.of(
            "texto", "llamadas", "fallo", "parada"
    );
    private static final Set<String> CAMPOS_LLAMADA = Set.of(
            "herramienta", "argumentos"
    );
    private static final Set<String> CAMPOS_ESPERA = Set.of(
            "mensajes", "reservasCreadas", "operacionVigente", "modoConversacion", "estadoTurno",
            "sustitucionGuardas", "llamadasModelo", "salientesNuevos", "peticionNoContiene", "herramientaError"
    );
    private static final Set<String> CAMPOS_MENSAJE = Set.of(
            "tipo", "contiene", "noContiene"
    );

    /**
     * Constructor de compatibilidad sin {@code limiteLlamadasDia}.
     *
     * @param nombre nombre identificativo del guion
     * @param negocio slug o letra del negocio ({@code "A"} o {@code "B"})
     * @param reloj instante inicial simulado
     * @param ficha si el cliente posee ficha creada al inicio
     * @param iaNegocio si la IA está habilitada a nivel del negocio
     * @param pasos secuencia de pasos a ejecutar
     */
    public GuionConversacion(String nombre, String negocio, Instant reloj, boolean ficha, boolean iaNegocio, List<PasoGuion> pasos) {
        this(nombre, negocio, reloj, ficha, iaNegocio, Optional.empty(), pasos);
    }

    /**
     * Valida y copia los pasos del guion.
     *
     * @param nombre nombre identificativo del guion
     * @param negocio slug o letra del negocio ({@code "A"} o {@code "B"})
     * @param reloj instante inicial simulado
     * @param ficha si el cliente posee ficha creada al inicio
     * @param iaNegocio si la IA está habilitada a nivel del negocio
     * @param limiteLlamadasDia límite diario local opcional a configurar en el negocio
     * @param pasos secuencia de pasos a ejecutar
     */
    public GuionConversacion {
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(negocio, "negocio");
        Objects.requireNonNull(reloj, "reloj");
        Objects.requireNonNull(limiteLlamadasDia, "limiteLlamadasDia");
        pasos = List.copyOf(pasos != null ? pasos : List.of());
    }

    /**
     * Entrada del cliente en un paso del guion.
     *
     * @param texto texto libre escrito por el cliente
     * @param seleccion identificador estático de selección o botón pulsado
     * @param seleccionDelUltimoMensaje índice de opción del último interactivo saliente (H-XI-03)
     * @param repetirSeleccion si debe repetirse la selección previamente enviada (H-XI-03)
     */
    public record EntradaCliente(
            Optional<String> texto,
            Optional<String> seleccion,
            Optional<Integer> seleccionDelUltimoMensaje,
            Optional<Boolean> repetirSeleccion
    ) {
        /**
         * Entrada vacía cuando el paso no requiere acción previa del cliente.
         */
        public static final EntradaCliente VACIA = new EntradaCliente(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );

        /**
         * Constructor para compatibilidad con código existente (texto y selección estática).
         *
         * @param texto texto libre opcional
         * @param seleccion identificador de selección opcional
         */
        public EntradaCliente(Optional<String> texto, Optional<String> seleccion) {
            this(texto, seleccion, Optional.empty(), Optional.empty());
        }

        /**
         * Valida los componentes de la entrada.
         *
         * @param texto texto libre
         * @param seleccion selección de botón o lista
         * @param seleccionDelUltimoMensaje índice de opción dinámica
         * @param repetirSeleccion indicador para reenviar la última selección
         */
        public EntradaCliente {
            Objects.requireNonNull(texto, "texto");
            Objects.requireNonNull(seleccion, "seleccion");
            Objects.requireNonNull(seleccionDelUltimoMensaje, "seleccionDelUltimoMensaje");
            Objects.requireNonNull(repetirSeleccion, "repetirSeleccion");
        }
    }

    /**
     * Evento concurrente que ocurre durante la llamada al modelo en este paso (H-XI-05; guion 11).
     *
     * @param texto texto entrante del cliente a enviar mientras el modelo procesa
     */
    public record DurantePaso(Optional<String> texto) {
        /**
         * Valida que el texto opcional no sea nulo.
         *
         * @param texto texto opcional
         */
        public DurantePaso {
            Objects.requireNonNull(texto, "texto");
        }
    }

    /**
     * Llamada a herramienta programada en el guion.
     *
     * @param herramienta herramienta invocada
     * @param argumentosJson argumentos serializados en JSON
     */
    public record LlamadaGuion(HerramientaIA herramienta, String argumentosJson) {
        /**
         * Valida componentes de la llamada.
         *
         * @param herramienta herramienta invocada
         * @param argumentosJson argumentos en JSON
         */
        public LlamadaGuion {
            Objects.requireNonNull(herramienta, "herramienta");
            Objects.requireNonNull(argumentosJson, "argumentosJson");
        }
    }

    /**
     * Respuesta que devolverá el adaptador falso en el paso.
     *
     * @param texto texto opcional devuelto por el modelo
     * @param llamadas lista de llamadas a herramientas
     * @param fallo motivo de fallo programado si corresponde
     * @param parada motivo de parada ({@code "end_turn"}, {@code "tool_use"}, {@code "max_tokens"}, {@code "refusal"})
     */
    public record RespuestaProgramadaGuion(
            Optional<String> texto,
            List<LlamadaGuion> llamadas,
            Optional<MotivoFalloModelo> fallo,
            String parada
    ) {
        /**
         * Valida y copia.
         *
         * @param texto texto
         * @param llamadas llamadas
         * @param fallo fallo opcional
         * @param parada motivo de parada
         */
        public RespuestaProgramadaGuion {
            Objects.requireNonNull(texto, "texto");
            llamadas = List.copyOf(llamadas != null ? llamadas : List.of());
            Objects.requireNonNull(fallo, "fallo");
            Objects.requireNonNull(parada, "parada");
        }
    }

    /**
     * Mensaje saliente esperado tras la ejecución del paso.
     *
     * @param tipo tipo de mensaje ({@code "INTERACTIVO"}, {@code "TEXTO"}), opcional
     * @param contiene cadenas que el mensaje DEBE contener
     * @param noContiene cadenas que el mensaje NO DEBE contener
     */
    public record MensajeEsperado(Optional<String> tipo, List<String> contiene, List<String> noContiene) {
        /**
         * Valida y copia.
         *
         * @param tipo tipo de mensaje
         * @param contiene textos requeridos
         * @param noContiene textos prohibidos
         */
        public MensajeEsperado {
            Objects.requireNonNull(tipo, "tipo");
            contiene = List.copyOf(contiene != null ? contiene : List.of());
            noContiene = List.copyOf(noContiene != null ? noContiene : List.of());
        }
    }

    /**
     * Expectativas tras procesar un paso del guion (CI-11; 08-pruebas §4.1).
     *
     * @param mensajes mensajes nuevos esperados en la bandeja de salida
     * @param reservasCreadas total de reservas activas esperadas en la base
     * @param operacionVigente si debe haber o no una operación pendiente vigente
     * @param modoConversacion modo de la conversación ({@code "ASISTENTE"}, {@code "HUMANO"})
     * @param estadoTurno estado del turno IA si se procesó ({@code "HECHO"}, {@code "DESCARTADO"}, {@code "FALLIDO"})
     * @param sustitucionGuardas motivo de sustitución de guardas esperado, o vacío
     * @param llamadasModelo número exacto de llamadas nuevas al modelo realizadas en este paso
     * @param salientesNuevos número exacto de mensajes salientes nuevos generados en este paso
     * @param peticionNoContiene cadenas que la última petición enviada al modelo NO debe contener
     * @param herramientaError si alguna herramienta ejecutada en este paso devolvió error ({@code is_error})
     */
    public record ExpectativaPaso(
            List<MensajeEsperado> mensajes,
            Optional<Integer> reservasCreadas,
            Optional<Boolean> operacionVigente,
            Optional<String> modoConversacion,
            Optional<String> estadoTurno,
            Optional<String> sustitucionGuardas,
            Optional<Integer> llamadasModelo,
            Optional<Integer> salientesNuevos,
            List<String> peticionNoContiene,
            Optional<Boolean> herramientaError
    ) {
        /**
         * Constructor de compatibilidad con los 6 campos originales.
         *
         * @param mensajes mensajes esperados
         * @param reservasCreadas reservas activas
         * @param operacionVigente presencia de propuesta
         * @param modoConversacion modo de conversación
         * @param estadoTurno estado final del turno
         * @param sustitucionGuardas motivo de guarda
         */
        public ExpectativaPaso(
                List<MensajeEsperado> mensajes,
                Optional<Integer> reservasCreadas,
                Optional<Boolean> operacionVigente,
                Optional<String> modoConversacion,
                Optional<String> estadoTurno,
                Optional<String> sustitucionGuardas
        ) {
            this(mensajes, reservasCreadas, operacionVigente, modoConversacion, estadoTurno, sustitucionGuardas,
                    Optional.empty(), Optional.empty(), List.of(), Optional.empty());
        }

        /**
         * Valida y copia.
         *
         * @param mensajes mensajes esperados
         * @param reservasCreadas conteo de reservas
         * @param operacionVigente presencia de operación pendiente
         * @param modoConversacion modo esperado
         * @param estadoTurno estado del turno
         * @param sustitucionGuardas sustitución esperada
         * @param llamadasModelo llamadas esperadas al modelo
         * @param salientesNuevos salientes generados
         * @param peticionNoContiene textos prohibidos en la petición
         * @param herramientaError indicador de error en herramientas
         */
        public ExpectativaPaso {
            mensajes = List.copyOf(mensajes != null ? mensajes : List.of());
            Objects.requireNonNull(reservasCreadas, "reservasCreadas");
            Objects.requireNonNull(operacionVigente, "operacionVigente");
            Objects.requireNonNull(modoConversacion, "modoConversacion");
            Objects.requireNonNull(estadoTurno, "estadoTurno");
            Objects.requireNonNull(sustitucionGuardas, "sustitucionGuardas");
            Objects.requireNonNull(llamadasModelo, "llamadasModelo");
            Objects.requireNonNull(salientesNuevos, "salientesNuevos");
            peticionNoContiene = List.copyOf(peticionNoContiene != null ? peticionNoContiene : List.of());
            Objects.requireNonNull(herramientaError, "herramientaError");
        }
    }

    /**
     * Un paso dentro del guion de conversación.
     *
     * @param cliente entrada del cliente
     * @param modelo respuestas programadas para el adaptador falso durante este paso
     * @param espera expectativas a comprobar
     * @param avanzarMinutos minutos opcionales a adelantar el reloj antes de procesar el paso (H-XI-02)
     * @param durante evento concurrente a disparar durante la llamada HTTP al modelo (H-XI-05)
     */
    public record PasoGuion(
            EntradaCliente cliente,
            List<RespuestaProgramadaGuion> modelo,
            ExpectativaPaso espera,
            Optional<Integer> avanzarMinutos,
            Optional<DurantePaso> durante
    ) {
        /**
         * Constructor de compatibilidad para 3 argumentos.
         *
         * @param cliente entrada del cliente
         * @param modelo respuestas del modelo
         * @param espera expectativas del paso
         */
        public PasoGuion(EntradaCliente cliente, List<RespuestaProgramadaGuion> modelo, ExpectativaPaso espera) {
            this(cliente != null ? cliente : EntradaCliente.VACIA, modelo, espera, Optional.empty(), Optional.empty());
        }

        /**
         * Valida y copia los componentes del paso.
         *
         * @param cliente entrada del cliente
         * @param modelo lista de respuestas del modelo
         * @param espera expectativas a validar
         * @param avanzarMinutos minutos a avanzar el reloj
         * @param durante evento concurrente
         */
        public PasoGuion {
            cliente = cliente != null ? cliente : EntradaCliente.VACIA;
            modelo = List.copyOf(modelo != null ? modelo : List.of());
            Objects.requireNonNull(espera, "espera");
            Objects.requireNonNull(avanzarMinutos, "avanzarMinutos");
            Objects.requireNonNull(durante, "durante");
        }
    }

    /**
     * Lee y valida un guion desde una cadena en formato YAML.
     *
     * @param contenidoYaml texto YAML
     * @return instancia validada de {@link GuionConversacion}
     * @throws IllegalArgumentException si la estructura es inválida o contiene campos desconocidos
     */
    @SuppressWarnings("unchecked")
    public static GuionConversacion leerYaml(String contenidoYaml) {
        Objects.requireNonNull(contenidoYaml, "contenidoYaml");
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object objeto = yaml.load(contenidoYaml);
        if (!(objeto instanceof Map<?, ?> mapaRaiz)) {
            throw new IllegalArgumentException("El archivo YAML debe contener un mapa en la raíz.");
        }
        Map<String, Object> raiz = (Map<String, Object>) mapaRaiz;
        validarCamposPermitidos("raíz", raiz.keySet(), CAMPOS_RAIZ);

        String nombre = leerCadenaObligatoria(raiz, "nombre");
        String negocio = leerCadenaObligatoria(raiz, "negocio");
        String relojIso = leerCadenaObligatoria(raiz, "reloj");
        Instant reloj = OffsetDateTime.parse(relojIso).toInstant();
        boolean ficha = Boolean.parseBoolean(String.valueOf(raiz.getOrDefault("ficha", "false")));
        boolean iaNegocio = Boolean.parseBoolean(String.valueOf(raiz.getOrDefault("iaNegocio", "true")));

        Optional<Integer> limiteLlamadasDia = Optional.empty();
        if (raiz.containsKey("limiteLlamadasDia")) {
            limiteLlamadasDia = Optional.of(((Number) raiz.get("limiteLlamadasDia")).intValue());
        }

        List<?> pasosRaw = (List<?>) raiz.get("pasos");
        if (pasosRaw == null || pasosRaw.isEmpty()) {
            throw new IllegalArgumentException("El guion debe incluir al menos un paso en 'pasos'.");
        }

        List<PasoGuion> pasos = new ArrayList<>();
        for (int i = 0; i < pasosRaw.size(); i++) {
            Object p = pasosRaw.get(i);
            if (!(p instanceof Map<?, ?> pasoMapa)) {
                throw new IllegalArgumentException("El paso " + (i + 1) + " debe ser un mapa.");
            }
            pasos.add(parsearPaso((Map<String, Object>) pasoMapa, i + 1));
        }

        return new GuionConversacion(nombre, negocio, reloj, ficha, iaNegocio, limiteLlamadasDia, pasos);
    }

    /**
     * Lee un guion desde el classpath.
     *
     * @param rutaRecurso ruta dentro del classpath (p. ej. {@code "/conversaciones/01-inventa-hora.yaml"})
     * @return guion parseado
     * @throws IOException si no se puede leer el recurso
     */
    public static GuionConversacion leerRecurso(String rutaRecurso) throws IOException {
        try (InputStream in = GuionConversacion.class.getResourceAsStream(rutaRecurso)) {
            if (in == null) {
                throw new IOException("Recurso no encontrado: " + rutaRecurso);
            }
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return leerYaml(yaml);
        }
    }

    @SuppressWarnings("unchecked")
    private static PasoGuion parsearPaso(Map<String, Object> paso, int indice) {
        validarCamposPermitidos("paso " + indice, paso.keySet(), CAMPOS_PASO);

        EntradaCliente cliente = EntradaCliente.VACIA;
        if (paso.containsKey("cliente")) {
            Object clienteObj = paso.get("cliente");
            if (!(clienteObj instanceof Map<?, ?> clienteMapa)) {
                throw new IllegalArgumentException("El campo 'cliente' del paso " + indice + " debe ser un mapa.");
            }
            Map<String, Object> cm = (Map<String, Object>) clienteMapa;
            validarCamposPermitidos("paso " + indice + ".cliente", cm.keySet(), CAMPOS_CLIENTE);
            Optional<String> texto = Optional.ofNullable((String) cm.get("texto"));
            Optional<String> seleccion = Optional.ofNullable((String) cm.get("seleccion"));
            Optional<Integer> seleccionDelUltimoMensaje = Optional.empty();
            if (cm.containsKey("seleccionDelUltimoMensaje")) {
                seleccionDelUltimoMensaje = Optional.of(((Number) cm.get("seleccionDelUltimoMensaje")).intValue());
            }
            Optional<Boolean> repetirSeleccion = Optional.empty();
            if (cm.containsKey("repetirSeleccion")) {
                repetirSeleccion = Optional.of(Boolean.parseBoolean(String.valueOf(cm.get("repetirSeleccion"))));
            }
            cliente = new EntradaCliente(texto, seleccion, seleccionDelUltimoMensaje, repetirSeleccion);
        }

        Optional<Integer> avanzarMinutos = Optional.empty();
        if (paso.containsKey("avanzarMinutos")) {
            avanzarMinutos = Optional.of(((Number) paso.get("avanzarMinutos")).intValue());
        }

        Optional<DurantePaso> durante = Optional.empty();
        if (paso.containsKey("durante")) {
            Object dObj = paso.get("durante");
            if (!(dObj instanceof Map<?, ?> dm)) {
                throw new IllegalArgumentException("El campo 'durante' del paso " + indice + " debe ser un mapa.");
            }
            Map<String, Object> dmap = (Map<String, Object>) dm;
            validarCamposPermitidos("paso " + indice + ".durante", dmap.keySet(), CAMPOS_DURANTE);
            Optional<String> txt = Optional.ofNullable((String) dmap.get("texto"));
            durante = Optional.of(new DurantePaso(txt));
        }

        List<RespuestaProgramadaGuion> respuestasModelo = new ArrayList<>();
        Object modeloObj = paso.get("modelo");
        if (modeloObj instanceof List<?> modeloLista) {
            for (Object m : modeloLista) {
                if (m instanceof Map<?, ?> mm) {
                    respuestasModelo.add(parsearRespuestaModelo((Map<String, Object>) mm, indice));
                }
            }
        }

        ExpectativaPaso espera = parsearEspera((Map<String, Object>) paso.get("espera"), indice);

        return new PasoGuion(cliente, respuestasModelo, espera, avanzarMinutos, durante);
    }

    @SuppressWarnings("unchecked")
    private static RespuestaProgramadaGuion parsearRespuestaModelo(Map<String, Object> mapa, int pasoIndice) {
        validarCamposPermitidos("paso " + pasoIndice + ".modelo", mapa.keySet(), CAMPOS_MODELO);
        Optional<String> texto = Optional.ofNullable((String) mapa.get("texto"));
        String parada = (String) mapa.getOrDefault("parada", "end_turn");

        Optional<MotivoFalloModelo> fallo = Optional.empty();
        if (mapa.containsKey("fallo")) {
            fallo = Optional.of(MotivoFalloModelo.valueOf(((String) mapa.get("fallo")).trim()));
        }

        List<LlamadaGuion> llamadas = new ArrayList<>();
        if (mapa.get("llamadas") instanceof List<?> llamadasLista) {
            for (Object ll : llamadasLista) {
                if (ll instanceof Map<?, ?> llm) {
                    llamadas.add(parsearLlamada((Map<String, Object>) llm, pasoIndice));
                }
            }
        }

        return new RespuestaProgramadaGuion(texto, llamadas, fallo, parada);
    }

    private static LlamadaGuion parsearLlamada(Map<String, Object> mapa, int pasoIndice) {
        validarCamposPermitidos("paso " + pasoIndice + ".llamada", mapa.keySet(), CAMPOS_LLAMADA);
        String nomHerramienta = leerCadenaObligatoria(mapa, "herramienta");
        HerramientaIA herramienta = HerramientaIA.porNombre(nomHerramienta);

        Object argsObj = mapa.get("argumentos");
        String argumentosJson;
        if (argsObj == null) {
            argumentosJson = "{}";
        } else if (argsObj instanceof String s) {
            argumentosJson = s;
        } else {
            try {
                argumentosJson = MAPPER.writeValueAsString(argsObj);
            } catch (Exception e) {
                throw new IllegalArgumentException("No se pudieron serializar los argumentos de la herramienta a JSON", e);
            }
        }
        return new LlamadaGuion(herramienta, argumentosJson);
    }

    @SuppressWarnings("unchecked")
    private static ExpectativaPaso parsearEspera(Map<String, Object> mapa, int pasoIndice) {
        if (mapa == null) {
            return new ExpectativaPaso(List.of(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        }
        validarCamposPermitidos("paso " + pasoIndice + ".espera", mapa.keySet(), CAMPOS_ESPERA);

        List<MensajeEsperado> mensajes = new ArrayList<>();
        if (mapa.get("mensajes") instanceof List<?> msgLista) {
            for (Object item : msgLista) {
                if (item instanceof Map<?, ?> mm) {
                    mensajes.add(parsearMensajeEsperado((Map<String, Object>) mm, pasoIndice));
                }
            }
        }

        Optional<Integer> reservasCreadas = Optional.empty();
        if (mapa.containsKey("reservasCreadas")) {
            reservasCreadas = Optional.of(((Number) mapa.get("reservasCreadas")).intValue());
        }

        Optional<Boolean> operacionVigente = Optional.empty();
        if (mapa.containsKey("operacionVigente")) {
            operacionVigente = Optional.of(Boolean.parseBoolean(String.valueOf(mapa.get("operacionVigente"))));
        }

        Optional<String> modoConversacion = Optional.ofNullable((String) mapa.get("modoConversacion"));
        Optional<String> estadoTurno = Optional.ofNullable((String) mapa.get("estadoTurno"));
        Optional<String> sustitucionGuardas = Optional.ofNullable((String) mapa.get("sustitucionGuardas"));

        Optional<Integer> llamadasModelo = Optional.empty();
        if (mapa.containsKey("llamadasModelo")) {
            llamadasModelo = Optional.of(((Number) mapa.get("llamadasModelo")).intValue());
        }

        Optional<Integer> salientesNuevos = Optional.empty();
        if (mapa.containsKey("salientesNuevos")) {
            salientesNuevos = Optional.of(((Number) mapa.get("salientesNuevos")).intValue());
        }

        List<String> peticionNoContiene = new ArrayList<>();
        if (mapa.containsKey("peticionNoContiene")) {
            Object pnc = mapa.get("peticionNoContiene");
            if (pnc instanceof List<?> pncl) {
                for (Object item : pncl) peticionNoContiene.add(String.valueOf(item));
            } else if (pnc != null) {
                peticionNoContiene.add(String.valueOf(pnc));
            }
        }

        Optional<Boolean> herramientaError = Optional.empty();
        if (mapa.containsKey("herramientaError")) {
            herramientaError = Optional.of(Boolean.parseBoolean(String.valueOf(mapa.get("herramientaError"))));
        }

        return new ExpectativaPaso(
                mensajes, reservasCreadas, operacionVigente, modoConversacion, estadoTurno,
                sustitucionGuardas, llamadasModelo, salientesNuevos, peticionNoContiene, herramientaError
        );
    }

    @SuppressWarnings("unchecked")
    private static MensajeEsperado parsearMensajeEsperado(Map<String, Object> mapa, int pasoIndice) {
        validarCamposPermitidos("paso " + pasoIndice + ".espera.mensaje", mapa.keySet(), CAMPOS_MENSAJE);
        Optional<String> tipo = Optional.ofNullable((String) mapa.get("tipo"));

        List<String> contiene = new ArrayList<>();
        if (mapa.containsKey("contiene")) {
            Object c = mapa.get("contiene");
            if (c instanceof List<?> cl) {
                for (Object item : cl) contiene.add(String.valueOf(item));
            } else if (c != null) {
                contiene.add(String.valueOf(c));
            }
        }

        List<String> noContiene = new ArrayList<>();
        if (mapa.containsKey("noContiene")) {
            Object nc = mapa.get("noContiene");
            if (nc instanceof List<?> ncl) {
                for (Object item : ncl) noContiene.add(String.valueOf(item));
            } else if (nc != null) {
                noContiene.add(String.valueOf(nc));
            }
        }

        return new MensajeEsperado(tipo, contiene, noContiene);
    }

    private static String leerCadenaObligatoria(Map<String, Object> mapa, String clave) {
        Object val = mapa.get(clave);
        if (val == null || String.valueOf(val).isBlank()) {
            throw new IllegalArgumentException("El campo obligatorio '" + clave + "' falta o está vacío.");
        }
        return String.valueOf(val).trim();
    }

    private static void validarCamposPermitidos(String contexto, Set<?> camposRecibidos, Set<String> permitidos) {
        for (Object c : camposRecibidos) {
            String campo = String.valueOf(c);
            if (!permitidos.contains(campo)) {
                throw new IllegalArgumentException("Campo desconocido en " + contexto + ": '" + campo + "'. Permitidos: " + permitidos);
            }
        }
    }
}
