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
 * Verifica el cálculo de las 9 métricas de calidad de 08-pruebas §4.2 y la generación del informe Markdown.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
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
                Optional.of(new EfectoTurno.ProponerReserva(1L, 2L, Instant.parse("2026-11-05T15:00:00Z"))),
                "HECHO", 350L, false
        );

        TranscripcionCaso trans1 = new TranscripcionCaso(caso1, "claude-haiku-5-5", List.of(turno1), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans1));

        assertThat(informe.modeloAprobado()).isTrue();
        assertThat(informe.totalCasos()).isEqualTo(1);
        assertThat(informe.aMarkdown()).contains("APROBADO");
        assertThat(informe.aMarkdown()).contains("| Horarios inventados (antes guardas) | 0.0 % | < 2.0 % (0 tras guardas) | APROBADO |");
        assertThat(informe.aMarkdown()).contains("| Confirmaciones falsas | 0 | 0 | APROBADO |");
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
                Optional.empty(), // No propone operación, pregunta
                "HECHO", 310L, false
        );

        TranscripcionCaso trans = new TranscripcionCaso(caso, "claude-haiku-5-5", List.of(turnoAclaracion), USO_FICTICIO, Instant.now());

        InformeModelo informe = Correctores.evaluar("claude-haiku-5-5", "PRUEBA", List.of(trans));

        assertThat(informe.aMarkdown()).contains("| Aclaración necesaria | 100.0 % | >= 90.0 % | APROBADO |");
    }
}
