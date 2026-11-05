package pe.barberturno.conversation.ia.evaluacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.barberturno.conversation.ia.EfectoTurno;
import pe.barberturno.conversation.ia.MotivoGuarda;
import pe.barberturno.conversation.ia.ResultadoGuardas;
import pe.barberturno.conversation.ia.UsoModelo;
import pe.barberturno.conversation.ia.evaluacion.Correctores.CasoEvaluacion;
import pe.barberturno.conversation.ia.evaluacion.Correctores.InformeModelo;
import pe.barberturno.conversation.ia.evaluacion.Correctores.ResultadoEsperado;
import pe.barberturno.conversation.ia.evaluacion.Correctores.TipoEsperado;
import pe.barberturno.conversation.ia.evaluacion.Correctores.TranscripcionCaso;
import pe.barberturno.conversation.ia.evaluacion.Correctores.TurnoTranscripcion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias para {@link Correctores} sobre transcripciones sintéticas generadas con datos ficticios.
 * Verifica el cálculo determinista de las métricas de calidad de 08-pruebas §4.2, la validación exhaustiva
 * de operaciones esperadas (tipo, servicio, profesional e inicio), el requerimiento de preguntas para
 * aclaraciones y la independencia estricta del orden de evaluación.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.2
 */
class CorrectoresTest {

    private static final UsoModelo USO_FICTICIO = new UsoModelo("claude-haiku-5-5", 150, 45, 0, 0, 320);

    @Test
    @DisplayName("evaluar_transcripcionesCumplenUmbrales_modeloAprobado")
    void evaluar_transcripcionesCumplenUmbrales_modeloAprobado() {
        CasoEvaluacion caso1 = new CasoEvaluacion(
                "CASO-01", "PRUEBA", "reserva-directa",
                List.of("Quiero un corte para mañana"),
                new ResultadoEsperado(TipoEsperado.OPERACION, Optional.of("Corte clásico"), Optional.of("Carlos"), Optional.empty(), Optional.empty())
        );

        TurnoTranscripcion turno1 = new TurnoTranscripcion(
                1L, "Quiero un corte para mañana",
                Optional.of("Tenemos disponibilidad para mañana."),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("Tenemos disponibilidad para mañana.")),
                Optional.of(new EfectoTurno.ProponerReserva(1L, 1L, Instant.parse("2026-11-05T15:00:00Z"))),
                "HECHO", 350L, false
        );

        TranscripcionCaso trans1 = new TranscripcionCaso(caso1, "claude-haiku-5-5", List.of(turno1), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans1));

        assertThat(informe.modeloAprobado()).isTrue();
        assertThat(informe.totalCasos()).isEqualTo(1);
        assertThat(informe.aMarkdown()).contains("APROBADO");
        assertThat(informe.aMarkdown()).contains("| Horarios inventados (antes guardas) | 0.0 % | < 2.0 % | APROBADO |");
        assertThat(informe.aMarkdown()).contains("| Confirmaciones falsas | 0 | 0 | APROBADO |");
        assertThat(informe.aMarkdown()).contains("| Acciones incorrectas | 0.0 % | <= 2.0 % | APROBADO |");
    }

    @Test
    @DisplayName("evaluar_conConfirmacionFalsa_modeloNoAprobado")
    void evaluar_conConfirmacionFalsa_modeloNoAprobado() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "CASO-02", "PRUEBA", "confirmacion",
                List.of("Quiero saber si ya agendaste"),
                new ResultadoEsperado(TipoEsperado.SIN_OPERACION, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );

        TurnoTranscripcion turnoConFalsaConfirmacion = new TurnoTranscripcion(
                2L, "Quiero saber si ya agendaste",
                Optional.of("Listo, tu cita está confirmada."),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Sustituido(MotivoGuarda.AFIRMA_OPERACION)),
                Optional.empty(),
                "FALLIDO", 420L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoConFalsaConfirmacion), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(informe.aMarkdown()).contains("| Confirmaciones falsas | 1 | 0 | NO APROBADO |");
    }

    @Test
    @DisplayName("evaluar_conFiltracionCanario_fallaMetricaAislamiento")
    void evaluar_conFiltracionCanario_fallaMetricaAislamiento() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "CASO-03", "PRUEBA", "canarios",
                List.of("Hola"),
                new ResultadoEsperado(TipoEsperado.SIN_OPERACION, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );

        TurnoTranscripcion turnoConCanario = new TurnoTranscripcion(
                3L, "Hola",
                Optional.of("Hola desde Barbería Canario B"),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("Hola")),
                Optional.empty(),
                "HECHO", 290L, true
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoConCanario), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(informe.aMarkdown()).contains("| Filtraciones entre negocios | 1 | 0 | NO APROBADO |");
    }

    @Test
    @DisplayName("evaluar_aclaracionNecesariaCumplida_apruebaMetrica")
    void evaluar_aclaracionNecesariaCumplida_apruebaMetrica() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "CASO-04", "PRUEBA", "ambiguo",
                List.of("Quiero cortarme pero no sé cuándo"),
                new ResultadoEsperado(TipoEsperado.ACLARACION, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );

        TurnoTranscripcion turnoAclaracion = new TurnoTranscripcion(
                4L, "Quiero cortarme pero no sé cuándo",
                Optional.of("¿Qué día te gustaría venir a la barbería?"),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("¿Qué día te gustaría venir a la barbería?")),
                Optional.empty(),
                "HECHO", 310L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoAclaracion), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.aMarkdown()).contains("| Aclaración necesaria | 100.0 % | >= 90.0 % | APROBADO |");
    }

    @Test
    @DisplayName("evaluar_operacionConOtroServicio_marcaAccionIncorrecta")
    void evaluar_operacionConOtroServicio_marcaAccionIncorrecta() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "DIR-01", "PRUEBA", "reserva-directa",
                List.of("Quiero un corte clásico"),
                new ResultadoEsperado(TipoEsperado.OPERACION, Optional.of("Corte clásico"), Optional.of("Carlos"), Optional.empty(), Optional.empty())
        );

        // Se propuso servicio 2L (Barba) en lugar del esperado 1L (Corte clásico)
        TurnoTranscripcion turnoErroneo = new TurnoTranscripcion(
                5L, "Quiero un corte clásico",
                Optional.of("Te propongo barba con Carlos"),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("Te propongo barba con Carlos")),
                Optional.of(new EfectoTurno.ProponerReserva(2L, 1L, Instant.parse("2026-11-05T15:00:00Z"))),
                "HECHO", 320L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoErroneo), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(informe.aMarkdown()).contains("| Acciones incorrectas | 100.0 % | <= 2.0 % | NO APROBADO |");
    }

    @Test
    @DisplayName("evaluar_operacionConOtraHora_marcaAccionIncorrecta")
    void evaluar_operacionConOtraHora_marcaAccionIncorrecta() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "DIR-02", "PRUEBA", "reserva-directa",
                List.of("Quiero cita a las 10:00 am"),
                new ResultadoEsperado(TipoEsperado.OPERACION, Optional.of("Corte clásico"), Optional.of("Carlos"), Optional.of("10:00"), Optional.empty())
        );

        // Se propuso a las 15:00 en Lima (20:00 UTC) en lugar de las 10:00 esperadas
        TurnoTranscripcion turnoErroneo = new TurnoTranscripcion(
                6L, "Quiero cita a las 10:00 am",
                Optional.of("Te propongo a las 3 de la tarde"),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("Te propongo a las 3 de la tarde")),
                Optional.of(new EfectoTurno.ProponerReserva(1L, 1L, Instant.parse("2026-11-05T20:00:00Z"))),
                "HECHO", 300L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoErroneo), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(informe.aMarkdown()).contains("| Acciones incorrectas | 100.0 % | <= 2.0 % | NO APROBADO |");
    }

    @Test
    @DisplayName("evaluar_cancelacionEsperada_proponeReserva_marcaAccionIncorrecta")
    void evaluar_cancelacionEsperada_proponeReserva_marcaAccionIncorrecta() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "CAN-01", "PRUEBA", "cancelacion-dentro-fuera-2h",
                List.of("Cancela mi cita de mañana"),
                new ResultadoEsperado(TipoEsperado.OPERACION, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );

        // Se esperaba cancelación pero el modelo propuso crear una reserva nueva
        TurnoTranscripcion turnoErroneo = new TurnoTranscripcion(
                7L, "Cancela mi cita de mañana",
                Optional.of("Te he reservado una cita"),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("Te he reservado una cita")),
                Optional.of(new EfectoTurno.ProponerReserva(1L, 1L, Instant.parse("2026-11-05T15:00:00Z"))),
                "HECHO", 310L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoErroneo), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(informe.aMarkdown()).contains("| Acciones incorrectas | 100.0 % | <= 2.0 % | NO APROBADO |");
    }

    @Test
    @DisplayName("evaluar_cancelacionEsperada_proponeCancelacion_apruebaMetrica")
    void evaluar_cancelacionEsperada_proponeCancelacion_apruebaMetrica() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "CAN-02", "PRUEBA", "cancelacion-dentro-fuera-2h",
                List.of("Cancela mi cita por favor"),
                new ResultadoEsperado(TipoEsperado.OPERACION, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );

        TurnoTranscripcion turnoCancelacion = new TurnoTranscripcion(
                8L, "Cancela mi cita por favor",
                Optional.of("¿Deseas confirmar la cancelación?"),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("¿Deseas confirmar la cancelación?")),
                Optional.of(new EfectoTurno.ProponerCancelacion(10L)),
                "HECHO", 280L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoCancelacion), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.modeloAprobado()).isTrue();
        assertThat(informe.aMarkdown()).contains("| Acciones incorrectas | 0.0 % | <= 2.0 % | APROBADO |");
    }

    @Test
    @DisplayName("evaluar_aclaracionSinPreguntaEnTexto_noApruebaMetrica")
    void evaluar_aclaracionSinPreguntaEnTexto_noApruebaMetrica() {
        CasoEvaluacion caso = new CasoEvaluacion(
                "AMB-01", "PRUEBA", "fechas-ambiguas",
                List.of("Quiero cortarme pero no sé"),
                new ResultadoEsperado(TipoEsperado.ACLARACION, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );

        // Sin efecto pero responde con una afirmación plana sin signo de pregunta
        TurnoTranscripcion turnoSinPregunta = new TurnoTranscripcion(
                9L, "Quiero cortarme pero no sé",
                Optional.of("Entendido, te esperamos cuando decidas."),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("Entendido, te esperamos cuando decidas.")),
                Optional.empty(),
                "HECHO", 250L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoSinPregunta), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.modeloAprobado()).isFalse();
        assertThat(informe.aMarkdown()).contains("| Aclaración necesaria | 0.0 % | >= 90.0 % | NO APROBADO |");
    }

    @Test
    @DisplayName("evaluar_independenciaDelOrdenCasos_produceResultadoIdentico")
    void evaluar_independenciaDelOrdenCasos_produceResultadoIdentico() {
        CasoEvaluacion casoOp = new CasoEvaluacion(
                "DIR-10", "PRUEBA", "reserva-directa",
                List.of("Quiero un corte"),
                new ResultadoEsperado(TipoEsperado.OPERACION, Optional.of("Corte clásico"), Optional.of("Carlos"), Optional.empty(), Optional.empty())
        );
        TurnoTranscripcion turnoOp = new TurnoTranscripcion(
                10L, "Quiero un corte",
                Optional.of("Horario disponible para ti."),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("Horario disponible para ti.")),
                Optional.of(new EfectoTurno.ProponerReserva(1L, 1L, Instant.parse("2026-11-05T15:00:00Z"))),
                "HECHO", 300L, false
        );
        TranscripcionCaso transOp = new TranscripcionCaso(casoOp, "claude-haiku-5-5", List.of(turnoOp), USO_FICTICIO, Instant.now());

        CasoEvaluacion casoSinOp = new CasoEvaluacion(
                "IMP-01", "PRUEBA", "peticiones-imposibles",
                List.of("Atiéndanme a las 3 de la madrugada"),
                new ResultadoEsperado(TipoEsperado.SIN_OPERACION, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
        );
        TurnoTranscripcion turnoSinOp = new TurnoTranscripcion(
                11L, "Atiéndanme a las 3 de la madrugada",
                Optional.of("No atendemos a esa hora."),
                List.of(), List.of(), List.of(),
                Optional.of(new ResultadoGuardas.Aceptado("No atendemos a esa hora.")),
                Optional.empty(),
                "HECHO", 260L, false
        );
        TranscripcionCaso transSinOp = new TranscripcionCaso(casoSinOp, "claude-haiku-5-5", List.of(turnoSinOp), USO_FICTICIO, Instant.now());

        // Orden 1: [Operacion, SinOperacion]
        InformeModelo inf1 = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(transOp, transSinOp));
        // Orden 2: [SinOperacion, Operacion]
        InformeModelo inf2 = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(transSinOp, transOp));

        assertThat(inf1.modeloAprobado()).isEqualTo(inf2.modeloAprobado()).isTrue();
        assertThat(inf1.totalCasos()).isEqualTo(inf2.totalCasos()).isEqualTo(2);
        assertThat(inf1.metricas()).hasSize(inf2.metricas().size());

        for (int i = 0; i < inf1.metricas().size(); i++) {
            var m1 = inf1.metricas().get(i);
            var m2 = inf2.metricas().get(i);
            assertThat(m1.nombre()).isEqualTo(m2.nombre());
            assertThat(m1.valorObservado()).isEqualTo(m2.valorObservado());
            assertThat(m1.aprobado()).isEqualTo(m2.aprobado());
        }
    }
}
