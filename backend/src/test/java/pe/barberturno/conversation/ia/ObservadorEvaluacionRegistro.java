package pe.barberturno.conversation.ia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementación de {@link ObservadorEvaluacion} en memoria para pruebas y evaluación de conversaciones
 * (17-v2-12 §4.4; 08-pruebas §4.2).
 * Registra cada llamada al modelo con su texto original (antes de guardas), las herramientas ejecutadas con sus
 * resultados y franjas, y el estado final del turno. Nunca escribe en los logs de la aplicación.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
public class ObservadorEvaluacionRegistro implements ObservadorEvaluacion {

    /**
     * Registro de una herramienta ejecutada dentro de un turno.
     *
     * @param llamada llamada realizada por el modelo
     * @param resultado resultado devuelto al modelo
     * @param franjas franjas ofrecidas por la ejecución
     */
    public record RegistroHerramienta(
            LlamadaHerramienta llamada,
            ResultadoHerramienta resultado,
            List<FranjaOfrecida> franjas
    ) {
        /**
         * Valida y copia.
         *
         * @param llamada llamada del modelo
         * @param resultado resultado
         * @param franjas franjas ofrecidas
         */
        public RegistroHerramienta {
            Objects.requireNonNull(llamada, "llamada");
            Objects.requireNonNull(resultado, "resultado");
            franjas = List.copyOf(franjas != null ? franjas : List.of());
        }
    }

    /**
     * Registro completo de un turno observado.
     */
    public static final class RegistroTurno {
        private final long turnoId;
        private final List<RespuestaModelo> respuestas = new ArrayList<>();
        private final List<RegistroHerramienta> herramientas = new ArrayList<>();
        private String estadoFinal;
        private Optional<ResultadoGuardas> guardas = Optional.empty();
        private Optional<EfectoTurno> efecto = Optional.empty();
        private Optional<DecisionRespuesta> decision = Optional.empty();
        private Optional<String> textoDescartado = Optional.empty();

        /**
         * Crea el registro de un turno.
         *
         * @param turnoId identificador del turno
         */
        public RegistroTurno(long turnoId) {
            this.turnoId = turnoId;
        }

        /**
         * Identificador del turno.
         *
         * @return id del turno
         */
        public long turnoId() {
            return turnoId;
        }

        /**
         * Respuestas del modelo recibidas en este turno en orden.
         *
         * @return lista de respuestas
         */
        public List<RespuestaModelo> respuestas() {
            return Collections.unmodifiableList(respuestas);
        }

        /**
         * Herramientas ejecutadas en este turno en orden.
         *
         * @return lista de herramientas
         */
        public List<RegistroHerramienta> herramientas() {
            return Collections.unmodifiableList(herramientas);
        }

        /**
         * Estado con el que terminó el turno ({@code HECHO}, {@code DESCARTADO}, {@code FALLIDO}), o nulo si no ha terminado.
         *
         * @return estado final
         */
        public String estadoFinal() {
            return estadoFinal;
        }

        /**
         * Resultado de las guardas de seguridad sobre el texto final del modelo.
         *
         * @return guardas, o vacío si no hubo texto
         */
        public Optional<ResultadoGuardas> guardas() {
            return guardas;
        }

        /**
         * Efecto de turno aplicado en la transacción final.
         *
         * @return efecto, o vacío si no se aplicó ninguno
         */
        public Optional<EfectoTurno> efecto() {
            return efecto;
        }

        /**
         * Decisión validada de {@code responder_al_cliente} (V2-DA-86).
         *
         * @return decisión, o vacía si no la hubo o no era válida
         */
        public Optional<DecisionRespuesta> decision() {
            return decision;
        }

        /**
         * Texto libre del proveedor descartado al cerrar el turno (V2-DA-86): nunca llega al cliente.
         *
         * @return texto descartado, o vacío
         */
        public Optional<String> textoDescartado() {
            return textoDescartado;
        }

        /**
         * Determina si el texto del modelo fue sustituido por alguna regla de las guardas de salida.
         *
         * @return {@code true} si fue sustituido
         */
        public boolean textoSustituido() {
            return guardas.map(g -> g instanceof ResultadoGuardas.Sustituido).orElse(false);
        }

        /**
         * Motivo de la sustitución de guardas, si ocurrió.
         *
         * @return motivo de sustitución, o vacío
         */
        public Optional<MotivoGuarda> motivoSustitucion() {
            return guardas.flatMap(g -> {
                if (g instanceof ResultadoGuardas.Sustituido s) {
                    return Optional.of(s.motivo());
                }
                return Optional.empty();
            });
        }

        /**
         * Calcula el uso total de tokens acumulado en todas las llamadas de este turno.
         *
         * @return uso consolidado de tokens
         */
        public UsoModelo usoTotal() {
            long entrada = 0;
            long salida = 0;
            long cacheLeida = 0;
            long cacheEscrita = 0;
            long duracionMs = 0;
            String modelo = "indefinido";

            for (RespuestaModelo r : respuestas) {
                UsoModelo u = r.uso();
                modelo = u.modelo();
                entrada += u.entrada();
                salida += u.salida();
                cacheLeida += u.cacheLeida();
                cacheEscrita += u.cacheEscrita();
                duracionMs += u.duracionMs();
            }
            return new UsoModelo(modelo, entrada, salida, cacheLeida, cacheEscrita, duracionMs);
        }
    }

    private final Map<Long, RegistroTurno> turnos = new LinkedHashMap<>();

    /** Constructor sin dependencias. */
    public ObservadorEvaluacionRegistro() { }

    @Override
    public synchronized void llamada(long turnoId, RespuestaModelo respuesta) {
        Objects.requireNonNull(respuesta, "respuesta");
        turnos.computeIfAbsent(turnoId, RegistroTurno::new).respuestas.add(respuesta);
    }

    @Override
    public synchronized void herramienta(long turnoId, LlamadaHerramienta llamada,
            ResultadoHerramienta resultado, List<FranjaOfrecida> franjas) {
        Objects.requireNonNull(llamada, "llamada");
        Objects.requireNonNull(resultado, "resultado");
        turnos.computeIfAbsent(turnoId, RegistroTurno::new).herramientas.add(
                new RegistroHerramienta(llamada, resultado, franjas));
    }

    @Override
    public synchronized void fin(long turnoId, String estado,
            Optional<ResultadoGuardas> guardas, Optional<EfectoTurno> efecto) {
        RegistroTurno reg = turnos.computeIfAbsent(turnoId, RegistroTurno::new);
        reg.estadoFinal = estado;
        reg.guardas = Objects.requireNonNull(guardas, "guardas");
        reg.efecto = Objects.requireNonNull(efecto, "efecto");
    }

    @Override
    public synchronized void decision(long turnoId, Optional<DecisionRespuesta> decision, Optional<String> textoDescartado) {
        RegistroTurno reg = turnos.computeIfAbsent(turnoId, RegistroTurno::new);
        reg.decision = Objects.requireNonNull(decision, "decision");
        reg.textoDescartado = Objects.requireNonNull(textoDescartado, "textoDescartado");
    }

    /**
     * Obtiene todos los turnos registrados hasta el momento.
     *
     * @return lista inmutable de turnos observados
     */
    public synchronized List<RegistroTurno> turnos() {
        return Collections.unmodifiableList(new ArrayList<>(turnos.values()));
    }

    /**
     * Busca el registro de un turno específico por su identificador.
     *
     * @param turnoId id del turno
     * @return registro del turno si existe
     */
    public synchronized Optional<RegistroTurno> buscarTurno(long turnoId) {
        return Optional.ofNullable(turnos.get(turnoId));
    }

    /**
     * Número total de turnos registrados.
     *
     * @return cantidad de turnos
     */
    public synchronized int cantidadTurnos() {
        return turnos.size();
    }

    /**
     * Reinicia todos los registros almacenados en memoria.
     */
    public synchronized void reiniciar() {
        turnos.clear();
    }
}
