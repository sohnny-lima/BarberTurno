package pe.barberturno.conversation.ia;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.conversation.CodigoSeleccion;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias para {@link GuardasSalida} que validan el comportamiento determinista y sin estado
 * de la red de seguridad del modelo frente a los 5 motivos de sustitución y los textos válidos.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class GuardasSalidaTest {

    private final GuardasSalida guardas = new GuardasSalida();

    private final ZonedDateTime ahoraViernes =
            ZonedDateTime.of(2026, 10, 23, 10, 0, 0, 0, TiempoNegocio.ZONA);

    private final Instant viernes17 =
            ZonedDateTime.of(2026, 10, 23, 17, 0, 0, 0, TiempoNegocio.ZONA).toInstant();
    private final Instant viernes1730 =
            ZonedDateTime.of(2026, 10, 23, 17, 30, 0, 0, TiempoNegocio.ZONA).toInstant();

    private final Instant sabado1730 =
            ZonedDateTime.of(2026, 10, 24, 17, 30, 0, 0, TiempoNegocio.ZONA).toInstant();
    private final Instant sabado1800 =
            ZonedDateTime.of(2026, 10, 24, 18, 0, 0, 0, TiempoNegocio.ZONA).toInstant();

    private final List<FranjaOfrecida> franjasPrueba = List.of(
            new FranjaOfrecida("f1", viernes17, viernes1730, "Carlos",
                    new CodigoSeleccion.Franja(1L, 2L, viernes17.getEpochSecond())),
            new FranjaOfrecida("f2", sabado1730, sabado1800, "Mateo",
                    new CodigoSeleccion.Franja(1L, 3L, sabado1730.getEpochSecond()))
    );

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\t", "\n\r  \n"})
    @DisplayName("revisar: texto vacío o en blanco retorna VACIO")
    void revisar_textoVacioOBlanco_retornaVacio(String texto) {
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.VACIO));
    }

    @Test
    @DisplayName("revisar: texto nulo retorna VACIO")
    void revisar_textoNulo_retornaVacio() {
        ResultadoGuardas res = guardas.revisar(null, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.VACIO));
    }

    @Test
    @DisplayName("revisar: texto con más de 1024 puntos de código retorna DEMASIADO_LARGO")
    void revisar_textoMasDe1024CodePoints_retornaDemasiadoLargo() {
        String textoLargo = "a".repeat(1025);
        ResultadoGuardas res = guardas.revisar(textoLargo, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.DEMASIADO_LARGO));
    }

    @Test
    @DisplayName("revisar: texto con exactamente 1024 puntos de código no es demasiado largo")
    void revisar_textoExactamente1024CodePoints_pasaGuardaLongitud() {
        String texto1024 = "Hola. ".repeat(170) + "¡Bien!";
        texto1024 = texto1024.substring(0, 1024);
        ResultadoGuardas res = guardas.revisar(texto1024, franjasPrueba, ahoraViernes);
        assertThat(res).isInstanceOf(ResultadoGuardas.Aceptado.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Visita http://barberia.com para más información.",
            "Puedes entrar a https://barberturno.pe y ver fotos.",
            "Consulta en www.barberturno.pe los horarios.",
            "Escríbenos a contacto@barberia.pe.",
            "Mi correo es asistente@ejemplo.test para dudas.",
            "Tu reserva anterior fue BT-123.",
            "Tu código de cita es BT-00459.",
            "Elige el servicio s1 para continuar.",
            "Elige el profesional p2 disponible.",
            "Selecciona la cita c3 de tu lista.",
            "Te propongo el turno f10 de la tarde.",
            "Tenemos disponible f2 para mañana.",
            "Mi número es 987654321, llámame.",
            "Puedes llamar al +51 987 654 321.",
            "Teléfono de contacto: 987-654-321.",
            "Llámanos al +51-987-654-321.",
            "Comunícate al 4567890 para coordinar.",
            "El número fijo es 01-4567890."
    })
    @DisplayName("revisar: texto con datos prohibidos retorna DATOS_PROHIBIDOS")
    void revisar_datosProhibidos_retornaDatosProhibidos(String texto) {
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.DATOS_PROHIBIDOS));
    }

    @Test
    @DisplayName("revisar: texto con alias f2 es rechazado por DATOS_PROHIBIDOS")
    void revisar_textoConAliasF2_retornaDatosProhibidos() {
        String texto = "Tenemos disponible f2 para tu cita.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.DATOS_PROHIBIDOS));
    }

    @Test
    @DisplayName("revisar: precios y duraciones no son datos prohibidos")
    void revisar_preciosYDuraciones_noSonDatosProhibidos() {
        String texto = "El corte cuesta S/ 25,00 y dura 30 min. ¿Te gustaría agendar?";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ya reservé tu cita para el viernes.",
            "Reserve tu cita con Carlos.",
            "Te reservé el turno de las cinco.",
            "He reservado tu turno con éxito.",
            "Hemos reservado tu cita en la barbería.",
            "Tu cita quedó reservada para esa hora.",
            "Quedó reservado tu espacio.",
            "Tu cita ya está reservada.",
            "Esta reservada tu cita en el sistema.",
            "Quedó confirmada tu cita con el barbero.",
            "Tu cita quedó confirmada.",
            "Está confirmada tu cita para el viernes.",
            "Tu cita está confirmada.",
            "Confirmada tu cita en nuestro local.",
            "Ya te agendé el servicio pedido.",
            "Cita agendada con éxito.",
            "Tu turno quedó agendado.",
            "Cancelé tu cita anterior.",
            "Ya cancelé tu reserva solicitada.",
            "Tu cita quedó cancelada.",
            "Reprogramé tu cita para otra fecha.",
            "Ya reprogramé tu turno con Carlos.",
            "Tu cita quedó reprogramada.",
            "Te esperamos el viernes en la barbería.",
            "Listo, tu cita ya está en agenda.",
            "¡Listo! Tu cita con Mateo está programada."
    })
    @DisplayName("revisar: afirmaciones de operación retornan AFIRMA_OPERACION (>= 15 casos)")
    void revisar_afirmaOperacion_retornaAfirmaOperacion(String texto) {
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.AFIRMA_OPERACION));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Te envío el resumen para que lo confirmes.",
            "¿Quieres que lo reserve?",
            "¿Deseas que reserve esta cita?",
            "¿Te gustaría que lo reserve por ti?",
            "Puedes confirmar con el botón.",
            "Por favor, pulsa confirmar para continuar.",
            "Revisa los datos y confirma con el botón de abajo.",
            "¿Deseas que lo cancele?",
            "¿Quieres que lo reprograme?",
            "Te mando los datos para confirmar si te parece bien.",
            "Si estás de acuerdo, pulsa confirmar.",
            "¿Confirmas esta opción para proceder?",
            "Puedes pulsar el botón para confirmar tu solicitud.",
            "¿Prefieres que reserve con Carlos o con Mateo?",
            "¿Quieres que reserve el corte clásico?"
    })
    @DisplayName("revisar: propuestas y solicitudes de confirmación son aceptadas (verdaderos negativos de afirmación)")
    void revisar_propuestasSubjuntivas_noSonAfirmacionOperacion(String texto) {
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tenemos cita hoy a las 18:00.",
            "Tenemos disponible el viernes a las 18:00.",
            "Tenemos disponible el sábado a las 17:00.",
            "Podemos atenderte el sábado a las 10:00.",
            "Te puedo ofrecer el domingo a las 17:00.",
            "Tenemos espacio el lunes a las 17:00.",
            "Podemos atenderte el 25/10 a las 17:00.",
            "Hay un turno disponible el 26/10 a las 17:30.",
            "Podemos agendar el 24 de octubre a las 10:00.",
            "Tenemos cita el 25 de octubre a las 17:00.",
            "Te atiendo a las 5 pm el sábado.",
            "Te puedo atender a las 5 y media hoy.",
            "Queda libre cinco de la tarde el sábado.",
            "Podemos al mediodía el viernes.",
            "Tenemos disponibilidad para el domingo.",
            "Tenemos disponibilidad el 28/10.",
            "Hay espacio el 30 de octubre."
    })
    @DisplayName("revisar: horarios no ofrecidos retornan HORARIO_NO_OFRECIDO (>= 15 casos)")
    void revisar_horarioNoOfrecido_retornaHorarioNoOfrecido(String texto) {
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.HORARIO_NO_OFRECIDO));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Tenemos citas disponibles para hoy.",
            "Tenemos citas disponibles para mañana.",
            "Hay horarios a las 5."
    })
    @DisplayName("revisar: con franjas vacía cualquier fecha u hora concreta retorna HORARIO_NO_OFRECIDO")
    void revisar_franjasVaciaConFechaUHora_retornaHorarioNoOfrecido(String texto) {
        ResultadoGuardas res = guardas.revisar(texto, List.of(), ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.HORARIO_NO_OFRECIDO));
    }

    @Test
    @DisplayName("revisar: con franjas vacía y sin fecha ni hora el texto es aceptado")
    void revisar_franjasVaciaSinFechaNiHora_retornaAceptado() {
        String texto = "¡Hola! ¿En qué servicio estás interesado?";
        ResultadoGuardas res = guardas.revisar(texto, List.of(), ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @Test
    @DisplayName("revisar: hora ofrecida hoy a las 17:00 es aceptada")
    void revisar_horaOfrecidaAceptada() {
        String texto = "Tenemos disponible hoy a las 17:00 con Carlos.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @Test
    @DisplayName("revisar: misma hora en otro día sin franja es rechazada")
    void revisar_mismaHoraOtroDiaRechazada() {
        String texto = "Tenemos disponible el sábado a las 17:00.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.HORARIO_NO_OFRECIDO));
    }

    @Test
    @DisplayName("revisar: «el viernes a las 5» siendo hoy viernes es aceptada")
    void revisar_elViernesALas5SiendoHoyViernes_aceptada() {
        String texto = "¿Te gustaría el viernes a las 5 con Carlos?";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @Test
    @DisplayName("revisar: «mañana a las 17:30» siendo mañana sábado con franja es aceptada")
    void revisar_mananaALas1730_aceptada() {
        String texto = "Podemos agendar mañana a las 17:30 con Mateo.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @Test
    @DisplayName("revisar: fecha sola sin hora de un día ofrecido es válida")
    void revisar_fechaSolaDeDiaOfrecido_aceptada() {
        String texto = "Tenemos disponibilidad para el viernes.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @Test
    @DisplayName("revisar: fecha numérica y hora coincidentes son aceptadas")
    void revisar_fechaNumericaYHora_aceptada() {
        String texto = "Te ofrecemos el 24/10 a las 17:30.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @Test
    @DisplayName("revisar: fecha en texto y hora coincidentes son aceptadas")
    void revisar_fechaEnTextoYHora_aceptada() {
        String texto = "Te ofrecemos el 24 de octubre a las 17:30.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }

    @Test
    @DisplayName("revisar: frase con «te esperamos el» tiene precedencia como AFIRMA_OPERACION aunque la hora coincida")
    void revisar_teEsperamosEl_tienePrecedenciaSobreHorarioValido() {
        String texto = "Te esperamos el viernes a las cinco de la tarde si confirmas.";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Sustituido(MotivoGuarda.AFIRMA_OPERACION));
    }

    @Test
    @DisplayName("revisar: hora en palabras «cinco de la tarde» coincidente es aceptada")
    void revisar_horaEnPalabras_aceptada() {
        String texto = "¿Te viene bien el viernes a las cinco de la tarde?";
        ResultadoGuardas res = guardas.revisar(texto, franjasPrueba, ahoraViernes);
        assertThat(res).isEqualTo(new ResultadoGuardas.Aceptado(texto));
    }
}
