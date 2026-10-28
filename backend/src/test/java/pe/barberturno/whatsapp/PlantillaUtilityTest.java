package pe.barberturno.whatsapp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.barberturno.common.error.ErrorCodigo;
import pe.barberturno.common.error.NegocioException;
import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias del catálogo de plantillas <em>utility</em> (12-v2-09-whatsapp-salida.md §3.5, CI-1).
 * Verifica los nombres exactos, los parámetros esperados, la composición del texto en ventana y las
 * reglas estrictas de validación de parámetros para Meta.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class PlantillaUtilityTest {

    @Test
    void catalogo_tresPlantillasConNombresParametrosEIdiomaConformes() {
        assertThat(PlantillaUtility.values()).containsExactly(
                PlantillaUtility.BT_RECORDATORIO_CITA,
                PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO,
                PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO);

        assertThat(PlantillaUtility.BT_RECORDATORIO_CITA.nombre()).isEqualTo("bt_recordatorio_cita");
        assertThat(PlantillaUtility.BT_RECORDATORIO_CITA.parametros())
                .containsExactly("negocio", "fecha", "profesional", "codigo");
        assertThat(PlantillaUtility.BT_RECORDATORIO_CITA.idioma()).isEqualTo("es");

        assertThat(PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO.nombre()).isEqualTo("bt_cita_modificada_por_negocio");
        assertThat(PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO.parametros())
                .containsExactly("negocio", "codigo", "fecha", "profesional");

        assertThat(PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO.nombre()).isEqualTo("bt_cita_cancelada_por_negocio");
        assertThat(PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO.parametros())
                .containsExactly("negocio", "codigo", "fecha");
    }

    @Test
    void porNombre_localizaPlantillaExistenteYFallaSiNoExiste() {
        assertThat(PlantillaUtility.porNombre("bt_recordatorio_cita"))
                .isEqualTo(PlantillaUtility.BT_RECORDATORIO_CITA);
        assertThat(PlantillaUtility.porNombre("bt_cita_modificada_por_negocio"))
                .isEqualTo(PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO);
        assertThat(PlantillaUtility.porNombre("bt_cita_cancelada_por_negocio"))
                .isEqualTo(PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO);

        assertThatThrownBy(() -> PlantillaUtility.porNombre("bt_plantilla_inexistente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textoEnVentana_componeElTextoConLosParametrosReemplazados() {
        Map<String, String> recordatorio = Map.of(
                "negocio", "Barbería Ficticia",
                "fecha", "28 de octubre a las 15:00",
                "profesional", "Carlos Barbero",
                "codigo", "BT-101");
        assertThat(PlantillaUtility.BT_RECORDATORIO_CITA.textoEnVentana(recordatorio))
                .isEqualTo("Hola. Te recordamos tu cita en Barbería Ficticia el 28 de octubre a las 15:00 con Carlos Barbero (reserva BT-101).");

        Map<String, String> modificada = Map.of(
                "negocio", "Barbería Ficticia",
                "codigo", "BT-102",
                "fecha", "29 de octubre a las 16:00",
                "profesional", "Ana Estilista");
        assertThat(PlantillaUtility.BT_CITA_MODIFICADA_POR_NEGOCIO.textoEnVentana(modificada))
                .isEqualTo("Barbería Ficticia cambió tu reserva BT-102: ahora es el 29 de octubre a las 16:00 con Ana Estilista.");

        Map<String, String> cancelada = Map.of(
                "negocio", "Barbería Ficticia",
                "codigo", "BT-103",
                "fecha", "30 de octubre a las 11:00");
        assertThat(PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO.textoEnVentana(cancelada))
                .isEqualTo("Barbería Ficticia canceló tu reserva BT-103 del 30 de octubre a las 11:00. Escríbenos si quieres reservar otra hora.");
    }

    @Test
    void validar_fallaSiElMapaEsNuloOFaltanOSobranParametros() {
        var plantilla = PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO;

        assertThatThrownBy(() -> plantilla.validar(null))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));

        // Falta "fecha"
        Map<String, String> incompleto = Map.of("negocio", "Barbería", "codigo", "BT-1");
        assertThatThrownBy(() -> plantilla.validar(incompleto))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));

        // Parámetro sobrante "otro"
        Map<String, String> conExtra = Map.of("negocio", "Barbería", "codigo", "BT-1", "fecha", "hoy", "extra", "valor");
        assertThatThrownBy(() -> plantilla.validar(conExtra))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n", "\r", "valor\ncon salto", "valor\rretorno", "valor\ttabulador", "cuatro    espacios"})
    void validar_valoresInvalidosRechazados(String valorInvalido) {
        var plantilla = PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO;
        Map<String, String> parametros = new HashMap<>();
        parametros.put("negocio", valorInvalido);
        parametros.put("codigo", "BT-101");
        parametros.put("fecha", "28/10/2026");

        assertThatThrownBy(() -> plantilla.validar(parametros))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @Test
    void validar_longitudMaximaDoscientosCaracteres() {
        var plantilla = PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO;
        String justo200 = "a".repeat(200);
        String exceso201 = "a".repeat(201);

        Map<String, String> valido200 = Map.of("negocio", justo200, "codigo", "BT-101", "fecha", "28/10/2026");
        assertThatNoException().isThrownBy(() -> plantilla.validar(valido200));

        Map<String, String> invalido201 = Map.of("negocio", exceso201, "codigo", "BT-101", "fecha", "28/10/2026");
        assertThatThrownBy(() -> plantilla.validar(invalido201))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }

    @Test
    void validar_tresEspaciosPermitidos_cuatroEspaciosRechazados() {
        var plantilla = PlantillaUtility.BT_CITA_CANCELADA_POR_NEGOCIO;
        Map<String, String> tresEspacios = Map.of("negocio", "Barbería   Ficticia", "codigo", "BT-101", "fecha", "28/10/2026");
        assertThatNoException().isThrownBy(() -> plantilla.validar(tresEspacios));

        Map<String, String> cuatroEspacios = Map.of("negocio", "Barbería    Ficticia", "codigo", "BT-101", "fecha", "28/10/2026");
        assertThatThrownBy(() -> plantilla.validar(cuatroEspacios))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getCodigo()).isEqualTo(ErrorCodigo.VALIDACION));
    }
}
