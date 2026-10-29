package pe.barberturno.conversation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de reconocimiento y normalización de {@link PalabraClave}.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class PalabraClaveTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "menu", "Menú", "MENU", " menù ", "¿menú?", "¡Menú!",
            "hola", "Hola", "HOLA", "¡Hola!", "¿Hola?", "  hola. ",
            "inicio", "Inicio", "INICIO", "  inicio.  ",
            "0", "0.", " 0 ", " ¡0! "
    })
    @DisplayName("Reconoce conjunto MENU con variantes de mayúsculas, tildes y signos")
    void de_variantesMenu_reconoceMenu(String entrada) {
        assertThat(PalabraClave.de(entrada)).isEqualTo(PalabraClave.MENU);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "si", "sí", "SI", "Sí.", "  si  ", "¡Sí!", "¿si?", "  SÍ  ",
            "confirmar", "Confirmar", "CONFIRMAR", "confirmar.", "¡Confirmar!",
            "confirmo", "Confirmo", "CONFIRMO", "¡Confirmo!", " confirmo. "
    })
    @DisplayName("Reconoce conjunto SI con variantes de mayúsculas, tildes y signos")
    void de_variantesSi_reconoceSi(String entrada) {
        assertThat(PalabraClave.de(entrada)).isEqualTo(PalabraClave.SI);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no", "No", "NO", "¡No!", "¿no?", " no. ", "  NO!  "
    })
    @DisplayName("Reconoce conjunto NO con variantes de mayúsculas y signos")
    void de_variantesNo_reconoceNo(String entrada) {
        assertThat(PalabraClave.de(entrada)).isEqualTo(PalabraClave.NO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sí, claro", "si por favor", "si confirmo", "confirmar cita",
            "no quiero", "no gracias", "no cancelar",
            "hola amigo", "hola, ¿cómo estás?", "menu principal", "volver al inicio",
            "00", "01", "1", "2", "cancelar", "reprogramar", "ayuda", "humano",
            "", "   ", "...", "¡¿?!", "---"
    })
    @DisplayName("Frases completas o textos distintos devuelven OTRA")
    void de_frasesYTextosDistintos_devuelveOtra(String entrada) {
        assertThat(PalabraClave.de(entrada)).isEqualTo(PalabraClave.OTRA);
    }

    @Test
    @DisplayName("Entrada nula devuelve OTRA")
    void de_textoNulo_devuelveOtra() {
        assertThat(PalabraClave.de(null)).isEqualTo(PalabraClave.OTRA);
    }
}
