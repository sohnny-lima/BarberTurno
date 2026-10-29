package pe.barberturno.conversation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.reservations.EstadoReserva;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de composición de textos de la conversación guiada en {@link TextosConversacion}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class TextosConversacionTest {

    private final TextosConversacion textos = new TextosConversacion();

    @Test
    @DisplayName("fecha, hora y precio: formato Lima, cambio de día en UTC y redondeo de precios")
    void fechaHoraYPrecio_formatoLimaYRedondeos_produceValoresEsperados() {
        // En Lima es UTC-5. 04:30 UTC del 24/10 es 23:30 Lima del 23/10 (viernes)
        Instant instanteCambioDia = Instant.parse("2026-10-24T04:30:00Z");

        String fecha = textos.fecha(instanteCambioDia);
        String hora = textos.hora(instanteCambioDia);

        assertThat(fecha).isEqualTo("viernes 23 de octubre de 2026");
        assertThat(hora).isEqualTo("23:30");

        // LocalDate directo
        LocalDate dia = LocalDate.of(2026, 10, 23);
        assertThat(textos.fecha(dia)).isEqualTo("viernes 23 de octubre de 2026");

        // Precios con coma decimal y redondeo HALF_UP
        assertThat(textos.precio(BigDecimal.valueOf(25))).isEqualTo("S/ 25,00");
        assertThat(textos.precio(new BigDecimal("25.00"))).isEqualTo("S/ 25,00");
        assertThat(textos.precio(new BigDecimal("25.005"))).isEqualTo("S/ 25,01");
        assertThat(textos.precio(new BigDecimal("25.004"))).isEqualTo("S/ 25,00");
        assertThat(textos.precio(new BigDecimal("1250.5"))).isEqualTo("S/ 1.250,50");
        assertThat(textos.precio(null)).isEqualTo("S/ 0,00");
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCodigo.class, names = {
            "FRANJA_NO_DISPONIBLE",
            "CLIENTE_CON_RESERVA_SOLAPADA",
            "LIMITE_RESERVAS_ACTIVAS",
            "FUERA_DE_POLITICA",
            "FUERA_DE_HORARIO",
            "FUERA_DE_HORIZONTE",
            "INICIO_EN_PASADO",
            "RECURSO_INACTIVO",
            "VERSION_DESACTUALIZADA",
            "TRANSICION_INVALIDA",
            "NO_ENCONTRADO"
    })
    @DisplayName("rechazo: cada código listado tiene texto propio o noDisponible")
    void rechazo_codigosListados_devuelveTextoPropio(ErrorCodigo codigo) {
        String mensaje = textos.rechazo(codigo);

        assertThat(mensaje).isNotBlank();
        assertThat(mensaje).doesNotContain("Exception").doesNotContain(codigo.name());
        if (codigo == ErrorCodigo.NO_ENCONTRADO) {
            assertThat(mensaje).isEqualTo(textos.noDisponible());
        }
    }

    @Test
    @DisplayName("rechazo: código no listado o nulo devuelve texto genérico sin detalles internos")
    void rechazo_codigoGenerico_devuelveTextoGenerico() {
        String mensajeGenerico = textos.rechazo(ErrorCodigo.ERROR_INTERNO);
        String mensajeNulo = textos.rechazo(null);

        assertThat(mensajeGenerico).isEqualTo("No pudimos completar tu solicitud en este momento. Por favor, intenta de nuevo más tarde.");
        assertThat(mensajeNulo).isEqualTo(mensajeGenerico);
    }

    @Test
    @DisplayName("resultado: variantes Ejecutada (CREAR, REPROGRAMAR, CANCELAR) con CONFIRMADA y PENDIENTE")
    void resultado_variantesEjecutada_textosAdecuados() {
        Instant inicio = Instant.parse("2026-10-23T23:00:00Z"); // 18:00 Lima
        Instant fin = Instant.parse("2026-10-23T23:30:00Z");

        CitaOpcion citaConfirmada = new CitaOpcion(1L, "BT-245", 10L, "Corte clásico", 20L, "Carlos",
                inicio, fin, new BigDecimal("25.00"), EstadoReserva.CONFIRMADA, 1, true, true);
        CitaOpcion citaPendiente = new CitaOpcion(1L, "BT-245", 10L, "Corte clásico", 20L, "Carlos",
                inicio, fin, new BigDecimal("25.00"), EstadoReserva.PENDIENTE, 1, true, true);

        // CREAR + CONFIRMADA
        ResultadoOperacion resCrearConf = new ResultadoOperacion.Ejecutada(TipoOperacion.CREAR, citaConfirmada, false);
        String txtCrearConf = textos.resultado(resCrearConf);
        assertThat(txtCrearConf).startsWith("Listo: BT-245")
                .contains("viernes 23 de octubre de 2026")
                .contains("18:00")
                .contains("Carlos")
                .doesNotContain("pendiente de confirmación");

        // CREAR + repetida da el mismo texto
        ResultadoOperacion resCrearRep = new ResultadoOperacion.Ejecutada(TipoOperacion.CREAR, citaConfirmada, true);
        assertThat(textos.resultado(resCrearRep)).isEqualTo(txtCrearConf);

        // CREAR + PENDIENTE
        ResultadoOperacion resCrearPend = new ResultadoOperacion.Ejecutada(TipoOperacion.CREAR, citaPendiente, false);
        String txtCrearPend = textos.resultado(resCrearPend);
        assertThat(txtCrearPend).contains("queda pendiente de confirmación por la barbería");

        // REPROGRAMAR + CONFIRMADA
        ResultadoOperacion resRepConf = new ResultadoOperacion.Ejecutada(TipoOperacion.REPROGRAMAR, citaConfirmada, false);
        String txtRepConf = textos.resultado(resRepConf);
        assertThat(txtRepConf).startsWith("Cambiamos tu cita BT-245")
                .contains("viernes 23 de octubre de 2026")
                .contains("18:00")
                .contains("Carlos");

        // REPROGRAMAR + PENDIENTE
        ResultadoOperacion resRepPend = new ResultadoOperacion.Ejecutada(TipoOperacion.REPROGRAMAR, citaPendiente, false);
        assertThat(textos.resultado(resRepPend)).contains("queda pendiente de confirmación por la barbería");

        // CANCELAR
        ResultadoOperacion resCanc = new ResultadoOperacion.Ejecutada(TipoOperacion.CANCELAR, citaConfirmada, false);
        String txtCanc = textos.resultado(resCanc);
        assertThat(txtCanc).startsWith("Cancelamos tu cita BT-245")
                .contains("viernes 23 de octubre de 2026")
                .contains("18:00");
    }

    @Test
    @DisplayName("resultado: variantes Rechazada, NoVigente y Anulada")
    void resultado_otrasVariantes_textosCorrectos() {
        // Rechazada
        ResultadoOperacion rech = new ResultadoOperacion.Rechazada(ErrorCodigo.FRANJA_NO_DISPONIBLE);
        assertThat(textos.resultado(rech)).isEqualTo(textos.rechazo(ErrorCodigo.FRANJA_NO_DISPONIBLE));

        // NoVigente CADUCADA
        ResultadoOperacion caducada = new ResultadoOperacion.NoVigente(MotivoNoVigente.CADUCADA);
        String txtCaducada = textos.resultado(caducada);
        assertThat(txtCaducada).contains("Esa propuesta ya no es válida").contains("pasaron más de 10 minutos");

        // NoVigente SUSTITUIDA
        ResultadoOperacion sustituida = new ResultadoOperacion.NoVigente(MotivoNoVigente.SUSTITUIDA);
        String txtSustituida = textos.resultado(sustituida);
        assertThat(txtSustituida).isEqualTo("Esa propuesta ya no es válida.");

        // Anulada
        ResultadoOperacion anulada = new ResultadoOperacion.Anulada();
        assertThat(textos.resultado(anulada)).isEqualTo("De acuerdo, no hice ningún cambio.");
    }

    @Test
    @DisplayName("privacidad: reemplaza marcadores {negocio} y {enlace} repetidos")
    void privacidad_marcadoresRepetidos_reemplazaTodos() {
        String plantilla = "Bienvenido a {negocio}. En {negocio} cuidamos tus datos: lee {enlace}. Consulta {enlace}.";
        String resultado = textos.privacidad(plantilla, "Barbería Central", "https://ejemplo.test/privacidad");

        assertThat(resultado).isEqualTo("Bienvenido a Barbería Central. En Barbería Central cuidamos tus datos: lee https://ejemplo.test/privacidad. Consulta https://ejemplo.test/privacidad.");
        assertThat(resultado).doesNotContain("{negocio}").doesNotContain("{enlace}");
    }

    @Test
    @DisplayName("resumen: contiene fecha completa, horas de inicio a fin, profesional, servicio, duración y precio")
    void resumen_creacionYReprogramacion_contieneTodosLosCamposExigidos() {
        Instant inicio = Instant.parse("2026-10-23T23:00:00Z"); // 18:00 Lima
        Instant fin = Instant.parse("2026-10-23T23:30:00Z");   // 18:30 Lima

        // Resumen CREAR
        ResumenOperacion resumenCrear = new ResumenOperacion(
                TipoOperacion.CREAR,
                "Corte de cabello",
                "Carlos",
                inicio,
                fin,
                30,
                new BigDecimal("25.00"),
                Optional.empty()
        );
        String txtCrear = textos.resumen(resumenCrear);
        assertThat(txtCrear)
                .contains("Corte de cabello")
                .contains("Carlos")
                .contains("viernes 23 de octubre de 2026")
                .contains("18:00 a 18:30")
                .contains("30 min")
                .contains("S/ 25,00");

        // Resumen REPROGRAMAR (con antes y después)
        Instant inicioAnt = Instant.parse("2026-10-20T15:00:00Z"); // 10:00 Lima
        Instant finAnt = Instant.parse("2026-10-20T15:30:00Z");
        CitaOpcion antes = new CitaOpcion(99L, "BT-99", 10L, "Corte de cabello", 20L, "Mateo",
                inicioAnt, finAnt, new BigDecimal("25.00"), EstadoReserva.CONFIRMADA, 1, true, true);

        ResumenOperacion resumenReprog = new ResumenOperacion(
                TipoOperacion.REPROGRAMAR,
                "Corte de cabello",
                "Carlos",
                inicio,
                fin,
                30,
                new BigDecimal("25.00"),
                Optional.of(antes)
        );
        String txtReprog = textos.resumen(resumenReprog);
        assertThat(txtReprog)
                .contains("Reprogramación de tu cita BT-99")
                .contains("Antes:")
                .contains("Nueva cita:")
                .contains("Corte de cabello")
                .contains("Carlos")
                .contains("viernes 23 de octubre de 2026")
                .contains("18:00 a 18:30")
                .contains("30 min")
                .contains("S/ 25,00");
    }

    @Test
    @DisplayName("Textos auxiliares (bienvenida, opciones, citas, noSoportado)")
    void textosAuxiliares_mensajesCordialesySinDatosInternos() {
        assertThat(textos.bienvenida("Barbería Élite")).contains("Barbería Élite");
        assertThat(textos.privacidadRechazada()).contains("Sin aceptar el aviso de privacidad");
        assertThat(textos.elegirServicio()).isNotBlank();
        assertThat(textos.elegirProfesional("Corte")).contains("Corte");
        assertThat(textos.elegirDia()).isNotBlank();
        assertThat(textos.elegirFranja(LocalDate.of(2026, 10, 23))).contains("viernes 23 de octubre de 2026");
        assertThat(textos.sinOpciones()).isNotBlank();
        assertThat(textos.misCitas()).isNotBlank();
        assertThat(textos.sinCitas()).isNotBlank();
        assertThat(textos.noDisponible()).isEqualTo("Esa opción o cita ya no está disponible.");
        assertThat(textos.noReconocido()).contains("menú");
        assertThat(textos.noSoportado()).isEqualTo("Por ahora solo entiendo texto y botones.");
    }
}
