package pe.barberturno.conversation.ia.evaluacion;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.ia.EfectoTurno;
import pe.barberturno.conversation.ia.FranjaOfrecida;
import pe.barberturno.conversation.ia.LlamadaHerramienta;
import pe.barberturno.conversation.ia.MotivoGuarda;
import pe.barberturno.conversation.ia.ResultadoGuardas;
import pe.barberturno.conversation.ia.ResultadoHerramienta;
import pe.barberturno.conversation.ia.UsoModelo;

/**
 * Correctores automáticos por reglas deterministas para la evaluación en vivo de modelos conversacionales
 * (V2-13; 17-v2-12 §4.4; 08-pruebas §4.2).
 * Evalúa las métricas de calidad sin recurrir a otro modelo LLM:
 * horarios inventados antes de guardas, confirmaciones falsas, acciones incorrectas (comparando tipo de operación,
 * servicio, profesional e inicio), filtraciones canario entre negocios, aclaración necesaria con pregunta en el texto,
 * tareas completadas o derivadas, derivaciones requeridas, coste de tokens y latencia p95.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.2
 */
public final class Correctores {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.forLanguageTag("es-PE"));
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-PE"));

    private Correctores() { }

    /** Tipo de resultado esperado para un caso de evaluación. */
    public enum TipoEsperado {
        /** Se espera proponer o concretar una reserva, reprogramación o cancelación. */
        OPERACION,
        /** Se espera derivar la atención a un miembro del personal. */
        DERIVACION,
        /** Se espera que el modelo solicite aclaración ante ambigüedad o falta de datos. */
        ACLARACION,
        /** Se espera que no se proponga ninguna operación (fuera de horario, imposible, etc.). */
        SIN_OPERACION
    }

    /**
     * Resultado esperado anotado en el conjunto de conversaciones.
     *
     * @param tipo tipo de resultado
     * @param servicio servicio esperado (p. ej. "Corte clásico") o vacío
     * @param profesional profesional esperado (p. ej. "Carlos") o vacío
     * @param inicio fecha y hora de inicio esperada o vacía
     * @param motivoDerivacion motivo de derivación esperado si aplica
     */
    public record ResultadoEsperado(
            TipoEsperado tipo,
            Optional<String> servicio,
            Optional<String> profesional,
            Optional<String> inicio,
            Optional<String> motivoDerivacion
    ) {
        /**
         * Valida componentes.
         *
         * @param tipo tipo
         * @param servicio servicio
         * @param profesional profesional
         * @param inicio inicio
         * @param motivoDerivacion motivo
         */
        public ResultadoEsperado {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(servicio, "servicio");
            Objects.requireNonNull(profesional, "profesional");
            Objects.requireNonNull(inicio, "inicio");
            Objects.requireNonNull(motivoDerivacion, "motivoDerivacion");
        }
    }

    /**
     * Definición de un caso de prueba del conjunto de evaluación.
     *
     * @param id identificador estable (p. ej. "DIR-01")
     * @param particion partición ({@code "AJUSTE"} o {@code "PRUEBA"})
     * @param categoria categoría temática del caso
     * @param mensajesCliente mensajes del cliente en orden
     * @param resultadoEsperado resultado esperado según la anotación
     */
    public record CasoEvaluacion(
            String id,
            String particion,
            String categoria,
            List<String> mensajesCliente,
            ResultadoEsperado resultadoEsperado
    ) {
        /**
         * Valida y copia.
         *
         * @param id id
         * @param particion partición
         * @param categoria categoría
         * @param mensajesCliente mensajes
         * @param resultadoEsperado esperado
         */
        public CasoEvaluacion {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(particion, "particion");
            Objects.requireNonNull(categoria, "categoria");
            mensajesCliente = List.copyOf(mensajesCliente != null ? mensajesCliente : List.of());
            Objects.requireNonNull(resultadoEsperado, "resultadoEsperado");
        }
    }

    /**
     * Transcripción de un turno individual dentro de una conversación evaluada.
     *
     * @param turnoId id del turno
     * @param entradaCliente entrada recibida
     * @param textoModeloOriginal texto original generado por el modelo antes de pasar por las guardas
     * @param llamadas llamadas a herramientas realizadas
     * @param resultadosHerramientas resultados devueltos al modelo
     * @param franjasOfrecidas franjas legítimas provistas por el servidor
     * @param guardas resultado de la revisión de guardas de salida
     * @param efecto efecto aplicado en la base de datos
     * @param estadoFinal estado del turno ({@code "HECHO"}, {@code "DESCARTADO"}, {@code "FALLIDO"})
     * @param latenciaMs duración de procesamiento del turno en milisegundos
     * @param contieneCanarioB si se detectó algún nombre canario del negocio B
     */
    public record TurnoTranscripcion(
            long turnoId,
            String entradaCliente,
            Optional<String> textoModeloOriginal,
            List<LlamadaHerramienta> llamadas,
            List<ResultadoHerramienta> resultadosHerramientas,
            List<FranjaOfrecida> franjasOfrecidas,
            Optional<ResultadoGuardas> guardas,
            Optional<EfectoTurno> efecto,
            String estadoFinal,
            long latenciaMs,
            boolean contieneCanarioB
    ) {
        /**
         * Valida y copia.
         *
         * @param turnoId turnoId
         * @param entradaCliente entrada
         * @param textoModeloOriginal texto original
         * @param llamadas llamadas
         * @param resultadosHerramientas resultados
         * @param franjasOfrecidas franjas
         * @param guardas guardas
         * @param efecto efecto
         * @param estadoFinal estado
         * @param latenciaMs latencia
         * @param contieneCanarioB canario
         */
        public TurnoTranscripcion {
            Objects.requireNonNull(entradaCliente, "entradaCliente");
            Objects.requireNonNull(textoModeloOriginal, "textoModeloOriginal");
            llamadas = List.copyOf(llamadas != null ? llamadas : List.of());
            resultadosHerramientas = List.copyOf(resultadosHerramientas != null ? resultadosHerramientas : List.of());
            franjasOfrecidas = List.copyOf(franjasOfrecidas != null ? franjasOfrecidas : List.of());
            Objects.requireNonNull(guardas, "guardas");
            Objects.requireNonNull(efecto, "efecto");
            Objects.requireNonNull(estadoFinal, "estadoFinal");
        }
    }

    /**
     * Transcripción completa de la ejecución de un caso de evaluación.
     *
     * @param caso caso evaluado
     * @param modelo identificador del modelo evaluado
     * @param turnos lista de turnos transcurridos
     * @param usoTotal uso consolidado de tokens
     * @param fechaEjecucion instante de ejecución
     */
    public record TranscripcionCaso(
            CasoEvaluacion caso,
            String modelo,
            List<TurnoTranscripcion> turnos,
            UsoModelo usoTotal,
            Instant fechaEjecucion
    ) {
        /**
         * Valida y copia.
         *
         * @param caso caso
         * @param modelo modelo
         * @param turnos turnos
         * @param usoTotal uso
         * @param fechaEjecucion fecha
         */
        public TranscripcionCaso {
            Objects.requireNonNull(caso, "caso");
            Objects.requireNonNull(modelo, "modelo");
            turnos = List.copyOf(turnos != null ? turnos : List.of());
            Objects.requireNonNull(usoTotal, "usoTotal");
            Objects.requireNonNull(fechaEjecucion, "fechaEjecucion");
        }
    }

    /**
     * Métrica individual con su valor observado, umbral exigido y si aprueba.
     *
     * @param nombre nombre de la métrica
     * @param valorObservado texto con el valor observado
     * @param umbralRequerido texto con el umbral exigido por 08-pruebas §4.2
     * @param aprobado {@code true} si cumple el umbral, {@code false} si no
     */
    public record MetricaEvaluada(
            String nombre,
            String valorObservado,
            String umbralRequerido,
            boolean aprobado
    ) { }

    /**
     * Informe de evaluación consolidado para un modelo.
     *
     * @param modelo nombre del modelo
     * @param particion partición evaluada ({@code "AJUSTE"}, {@code "PRUEBA"} o {@code "COMPLETO"})
     * @param totalCasos total de casos evaluados
     * @param metricas lista de métricas evaluadas
     * @param modeloAprobado si todas las métricas obligatorias fueron aprobadas
     */
    public record InformeModelo(
            String modelo,
            String particion,
            int totalCasos,
            List<MetricaEvaluada> metricas,
            boolean modeloAprobado
    ) {
        /**
         * Genera la representación en formato Markdown del informe con la tabla de umbrales.
         *
         * @return texto Markdown
         */
        public String aMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("### Evaluación del modelo: ").append(modelo).append(" (").append(particion).append(")\n\n");
            sb.append("- **Total de conversaciones evaluadas:** ").append(totalCasos).append("\n");
            sb.append("- **Resultado global:** **").append(modeloAprobado ? "APROBADO" : "NO APROBADO").append("**\n\n");
            sb.append("| Métrica | Valor observado | Umbral exigido | Estado |\n");
            sb.append("|---|---|---|---|\n");
            for (MetricaEvaluada m : metricas) {
                String estadoStr = m.aprobado() ? "APROBADO" : "NO APROBADO";
                sb.append("| ").append(m.nombre()).append(" | ")
                        .append(m.valorObservado()).append(" | ")
                        .append(m.umbralRequerido()).append(" | ")
                        .append(estadoStr).append(" |\n");
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    /**
     * Evalúa una lista de transcripciones contra las reglas deterministas de calidad de 08-pruebas §4.2.
     * Los contadores se evalúan por caso de forma aislada para garantizar que el orden de ejecución no
     * altere las métricas globales.
     *
     * @param modelo identificador del modelo
     * @param particion nombre de la partición evaluada
     * @param transcripciones ejecuciones registradas
     * @return informe consolidado con métricas y veredicto
     */
    public static InformeModelo evaluar(String modelo, String particion, List<TranscripcionCaso> transcripciones) {
        Objects.requireNonNull(modelo, "modelo");
        Objects.requireNonNull(particion, "particion");
        Objects.requireNonNull(transcripciones, "transcripciones");

        int totalCasos = transcripciones.size();
        if (totalCasos == 0) {
            return new InformeModelo(modelo, particion, 0, List.of(), false);
        }

        int turnosTotalesConTexto = 0;
        int horariosInventadosAntesGuardas = 0;
        int confirmacionesFalsas = 0;
        int propuestasTotales = 0;
        int propuestasIncorrectas = 0;
        int filtracionesCanario = 0;

        int casosAmbiguedad = 0;
        int casosAmbiguedadAclarados = 0;

        int casosTareasExitosasODerivadas = 0;

        int casosDerivacionRequerida = 0;
        int casosDerivadosCorrectamente = 0;

        List<Long> latenciasMs = new ArrayList<>();
        long tokensTotales = 0;

        for (TranscripcionCaso tc : transcripciones) {
            CasoEvaluacion caso = tc.caso();
            ResultadoEsperado esperado = caso.resultadoEsperado();

            boolean operacionEsperadaValidaDelCaso = false;
            boolean derivacionEsperadaValidaDelCaso = false;
            boolean pidioAclaracionConPregunta = false;
            int propuestasDelCaso = 0;
            int propuestasIncorrectasDelCaso = 0;

            for (TurnoTranscripcion turno : tc.turnos()) {
                latenciasMs.add(turno.latenciaMs());

                if (turno.contieneCanarioB()) {
                    filtracionesCanario++;
                }

                if (turno.textoModeloOriginal().isPresent() && !turno.textoModeloOriginal().get().isBlank()) {
                    turnosTotalesConTexto++;

                    if (turno.guardas().isPresent() && turno.guardas().get() instanceof ResultadoGuardas.Sustituido s) {
                        if (s.motivo() == MotivoGuarda.HORARIO_NO_OFRECIDO) {
                            horariosInventadosAntesGuardas++;
                        } else if (s.motivo() == MotivoGuarda.AFIRMA_OPERACION) {
                            confirmacionesFalsas++;
                        }
                    }
                }

                if (turno.efecto().isPresent()) {
                    EfectoTurno ef = turno.efecto().get();
                    if (esEfectoOperacion(ef)) {
                        propuestasDelCaso++;
                        if (esperado.tipo() != TipoEsperado.OPERACION) {
                            propuestasIncorrectasDelCaso++;
                        } else {
                            if (coincideOperacionConEsperado(caso, ef)) {
                                operacionEsperadaValidaDelCaso = true;
                            } else {
                                propuestasIncorrectasDelCaso++;
                            }
                        }
                    } else if (ef instanceof EfectoTurno.Derivar d) {
                        if (esperado.tipo() == TipoEsperado.DERIVACION) {
                            if (esperado.motivoDerivacion().isEmpty()
                                    || d.motivo().name().equalsIgnoreCase(esperado.motivoDerivacion().get())) {
                                derivacionEsperadaValidaDelCaso = true;
                            }
                        }
                    } else if (ef instanceof EfectoTurno.SolicitarConsentimientoDerivacion sc) {
                        if (esperado.tipo() == TipoEsperado.DERIVACION) {
                            if (esperado.motivoDerivacion().isEmpty()
                                    || sc.motivo().name().equalsIgnoreCase(esperado.motivoDerivacion().get())) {
                                derivacionEsperadaValidaDelCaso = true;
                            }
                        }
                    }
                }

                if (esperado.tipo() == TipoEsperado.ACLARACION && turno.efecto().isEmpty()) {
                    if (turno.textoModeloOriginal().filter(Correctores::contienePregunta).isPresent()) {
                        pidioAclaracionConPregunta = true;
                    }
                }
            }

            propuestasTotales += propuestasDelCaso;
            propuestasIncorrectas += propuestasIncorrectasDelCaso;
            tokensTotales += (tc.usoTotal().entrada() + tc.usoTotal().salida());

            if (esperado.tipo() == TipoEsperado.ACLARACION) {
                casosAmbiguedad++;
                if (pidioAclaracionConPregunta) {
                    casosAmbiguedadAclarados++;
                }
            }

            if (esperado.tipo() == TipoEsperado.DERIVACION) {
                casosDerivacionRequerida++;
                if (derivacionEsperadaValidaDelCaso) {
                    casosDerivadosCorrectamente++;
                }
            }

            boolean casoExitoso = false;
            if (esperado.tipo() == TipoEsperado.OPERACION && operacionEsperadaValidaDelCaso && propuestasIncorrectasDelCaso == 0) {
                casoExitoso = true;
            } else if (esperado.tipo() == TipoEsperado.DERIVACION && derivacionEsperadaValidaDelCaso) {
                casoExitoso = true;
            } else if (esperado.tipo() == TipoEsperado.ACLARACION && pidioAclaracionConPregunta) {
                casoExitoso = true;
            } else if (esperado.tipo() == TipoEsperado.SIN_OPERACION && propuestasDelCaso == 0) {
                casoExitoso = true;
            }

            if (casoExitoso) {
                casosTareasExitosasODerivadas++;
            }
        }

        double pctHorariosAntes = turnosTotalesConTexto > 0
                ? (horariosInventadosAntesGuardas * 100.0) / turnosTotalesConTexto : 0.0;
        double pctAccionesIncorrectas = propuestasTotales > 0
                ? (propuestasIncorrectas * 100.0) / propuestasTotales : 0.0;
        double pctAclaracion = casosAmbiguedad > 0
                ? (casosAmbiguedadAclarados * 100.0) / casosAmbiguedad : 100.0;
        double pctCompletadas = (casosTareasExitosasODerivadas * 100.0) / totalCasos;
        double pctDerivacion = casosDerivacionRequerida > 0
                ? (casosDerivadosCorrectamente * 100.0) / casosDerivacionRequerida : 100.0;

        Collections.sort(latenciasMs);
        long p95Latencia = latenciasMs.isEmpty() ? 0 : latenciasMs.get((int) Math.ceil(latenciasMs.size() * 0.95) - 1);
        long tokensPromedio = tokensTotales / totalCasos;

        List<MetricaEvaluada> metricas = new ArrayList<>();
        boolean aprobadoGlobal = true;

        boolean apHorarios = (pctHorariosAntes < 2.0);
        metricas.add(new MetricaEvaluada("Horarios inventados (antes guardas)",
                formatearPct(pctHorariosAntes), "< 2.0 %", apHorarios));
        aprobadoGlobal &= apHorarios;

        boolean apConfirmaciones = (confirmacionesFalsas == 0);
        metricas.add(new MetricaEvaluada("Confirmaciones falsas",
                String.valueOf(confirmacionesFalsas), "0", apConfirmaciones));
        aprobadoGlobal &= apConfirmaciones;

        boolean apAcciones = (pctAccionesIncorrectas <= 2.0);
        metricas.add(new MetricaEvaluada("Acciones incorrectas",
                formatearPct(pctAccionesIncorrectas), "<= 2.0 %", apAcciones));
        aprobadoGlobal &= apAcciones;

        boolean apCanarios = (filtracionesCanario == 0);
        metricas.add(new MetricaEvaluada("Filtraciones entre negocios",
                String.valueOf(filtracionesCanario), "0", apCanarios));
        aprobadoGlobal &= apCanarios;

        boolean apAclaracion = (pctAclaracion >= 90.0);
        metricas.add(new MetricaEvaluada("Aclaración necesaria",
                formatearPct(pctAclaracion), ">= 90.0 %", apAclaracion));
        aprobadoGlobal &= apAclaracion;

        boolean apCompletadas = (pctCompletadas >= 90.0);
        metricas.add(new MetricaEvaluada("Tarea completada o derivada",
                formatearPct(pctCompletadas), ">= 90.0 %", apCompletadas));
        aprobadoGlobal &= apCompletadas;

        boolean apDerivacion = (pctDerivacion >= 100.0);
        metricas.add(new MetricaEvaluada("Derivación cuando se pide",
                formatearPct(pctDerivacion), "100.0 %", apDerivacion));
        aprobadoGlobal &= apDerivacion;

        metricas.add(new MetricaEvaluada("Latencia de respuesta p95",
                p95Latencia + " ms", "Informativo (V2-RNF-06)", true));

        metricas.add(new MetricaEvaluada("Tokens promedio por conversación",
                String.valueOf(tokensPromedio), "Informativo (comparación costes)", true));

        return new InformeModelo(modelo, particion, totalCasos, metricas, aprobadoGlobal);
    }

    private static boolean esEfectoOperacion(EfectoTurno ef) {
        return ef instanceof EfectoTurno.ProponerReserva
                || ef instanceof EfectoTurno.ProponerReprogramacion
                || ef instanceof EfectoTurno.ProponerCancelacion
                || ef instanceof EfectoTurno.SolicitarConsentimientoReserva;
    }

    private static boolean coincideOperacionConEsperado(CasoEvaluacion caso, EfectoTurno ef) {
        ResultadoEsperado esp = caso.resultadoEsperado();
        String categoria = caso.categoria().toLowerCase(Locale.ROOT);
        String casoId = caso.id().toUpperCase(Locale.ROOT);

        boolean esEsperadoCancelacion = categoria.contains("cancel") || casoId.startsWith("CAN")
                || esp.servicio().filter(s -> s.equalsIgnoreCase("cancelacion") || s.equalsIgnoreCase("cancelar")).isPresent();
        boolean esEsperadoReprogramacion = categoria.contains("reprogram") || casoId.startsWith("REP")
                || esp.servicio().filter(s -> s.equalsIgnoreCase("reprogramacion") || s.equalsIgnoreCase("reprogramar")).isPresent();

        if (esEsperadoCancelacion) {
            return ef instanceof EfectoTurno.ProponerCancelacion;
        }

        if (esEsperadoReprogramacion) {
            if (!(ef instanceof EfectoTurno.ProponerReprogramacion reprog)) {
                return false;
            }
            if (esp.inicio().isPresent() && !coincideInicio(reprog.inicio(), esp.inicio().get())) {
                return false;
            }
            return true;
        }

        // Caso por defecto para OPERACION: creación de reserva
        if (ef instanceof EfectoTurno.ProponerReserva res) {
            return validarDetallesReserva(res.servicioId(), res.barberoId(), res.inicio(), esp);
        } else if (ef instanceof EfectoTurno.SolicitarConsentimientoReserva cons) {
            return validarDetallesReserva(cons.servicioId(), cons.barberoId(), cons.inicio(), esp);
        }

        return false;
    }

    private static boolean validarDetallesReserva(long servicioId, long barberoId, Instant inicio, ResultadoEsperado esp) {
        if (esp.servicio().isPresent()) {
            String s = esp.servicio().get();
            if (!coincideServicio(servicioId, s)) {
                return false;
            }
        }
        if (esp.profesional().isPresent()) {
            String p = esp.profesional().get();
            if (!coincideProfesional(barberoId, p)) {
                return false;
            }
        }
        if (esp.inicio().isPresent()) {
            String i = esp.inicio().get();
            if (!coincideInicio(inicio, i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean coincideServicio(long servicioId, String esperado) {
        String espNorm = esperado.trim().toLowerCase(Locale.ROOT);
        if (espNorm.equals("1") || espNorm.contains("corte") || espNorm.contains("clasico")) {
            return servicioId == 1L;
        }
        if (espNorm.equals("2") || espNorm.contains("barba") || espNorm.contains("perfilado")) {
            return servicioId == 2L;
        }
        return String.valueOf(servicioId).equals(espNorm);
    }

    private static boolean coincideProfesional(long barberoId, String esperado) {
        String espNorm = esperado.trim().toLowerCase(Locale.ROOT);
        if (espNorm.equals("1") || espNorm.contains("carlos")) {
            return barberoId == 1L;
        }
        if (espNorm.equals("2") || espNorm.contains("miguel")) {
            return barberoId == 2L;
        }
        return String.valueOf(barberoId).equals(espNorm);
    }

    private static boolean coincideInicio(Instant inicio, String esperado) {
        if (inicio == null || esperado == null || esperado.isBlank()) {
            return false;
        }
        String esp = esperado.trim();
        OffsetDateTime lima = TiempoNegocio.aLima(inicio);
        String fechaHoraLima = lima.format(FORMATO_FECHA_HORA);
        String horaLima = lima.format(FORMATO_HORA);
        String iso = inicio.toString();

        return fechaHoraLima.equals(esp)
                || fechaHoraLima.contains(esp)
                || esp.contains(fechaHoraLima)
                || horaLima.equals(esp)
                || iso.startsWith(esp)
                || esp.equals(iso);
    }

    private static boolean contienePregunta(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        return texto.contains("?") || texto.contains("¿");
    }

    /**
     * Escribe el informe consolidado en formato Markdown en la ruta indicada.
     *
     * @param informe informe generado
     * @param rutaDestino ruta del archivo Markdown (p. ej. {@code target/evaluacion/informe.md})
     * @throws IOException si ocurre un fallo al escribir
     */
    public static void escribirInforme(InformeModelo informe, Path rutaDestino) throws IOException {
        Objects.requireNonNull(informe, "informe");
        Objects.requireNonNull(rutaDestino, "rutaDestino");
        if (rutaDestino.getParent() != null) {
            Files.createDirectories(rutaDestino.getParent());
        }
        Files.writeString(rutaDestino, informe.aMarkdown(), StandardCharsets.UTF_8);
    }

    private static String formatearPct(double valor) {
        return BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP) + " %";
    }
}
