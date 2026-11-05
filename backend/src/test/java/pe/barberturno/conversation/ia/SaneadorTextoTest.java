package pe.barberturno.conversation.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Saneado del texto del cliente antes de guardarlo en un turno o enviarlo al modelo (17-v2-12 §7, V2-RNF-05).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class SaneadorTextoTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "mi cel es 987654321|mi cel es [dato]",
        "llámame al +51 987 654 321 porfa|llámame al [dato] porfa",
        "fijo (066) 31-2345|fijo [dato]",
        "escríbeme a ana.perez@ejemplo.test|escríbeme a [dato]",
        "correo ANA_99@correo.ejemplo.test y cel 999-888-777|correo [dato] y cel [dato]",
    })
    void sanear_telefonosYCorreos_seSustituyen(String entrada, String esperado) {
        assertThat(SaneadorTexto.sanear(entrada, 2000)).isEqualTo(esperado);
    }

    /** Revisión 129 (defecto 5): separadores Unicode que {@code \s} sin modo Unicode no reconocía. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "cel 987 654 321|cel [dato]",
        "cel 987 654 321|cel [dato]",
        "cel 987‑654‑321|cel [dato]",
        "cel 987–654–321|cel [dato]",
        "cel 987.654.321|cel [dato]",
    })
    void sanear_telefonosConSeparadoresUnicodeOPuntos_seSustituyen(String entrada, String esperado) {
        assertThat(SaneadorTexto.sanear(entrada, 2000)).isEqualTo(esperado);
    }

    @Test
    void contieneDatoPersonal_coincideConLoQueSeSanea() {
        assertThat(SaneadorTexto.contieneDatoPersonal("llámame al 987‑654‑321")).isTrue();
        assertThat(SaneadorTexto.contieneDatoPersonal("escribe a ana@ejemplo.test")).isTrue();
        assertThat(SaneadorTexto.contieneDatoPersonal("corte a las 17:30, S/ 25,00")).isFalse();
        assertThat(SaneadorTexto.contieneDatoPersonal(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "corte mañana a las 17:30",
        "el sábado 23/10 temprano",
        "cuánto cuesta, ¿S/ 25,00?",
        "somos 2 personas, 30 min cada uno",
        "código 123456 no",
    })
    void sanear_horasFechasPreciosYNumerosCortos_seConservan(String entrada) {
        assertThat(SaneadorTexto.sanear(entrada, 2000)).isEqualTo(entrada);
    }

    @Test
    void sanear_recortaPorPuntosDeCodigoSinPartirEmojis() {
        assertThat(SaneadorTexto.sanear("✂️✂️✂️", 2)).isEqualTo("✂️");
        assertThat(SaneadorTexto.sanear("hola", 2)).isEqualTo("ho");
    }

    @Test
    void sanear_nuloYEspacios_devuelveVacio() {
        assertThat(SaneadorTexto.sanear(null, 10)).isEmpty();
        assertThat(SaneadorTexto.sanear("   ", 10)).isEmpty();
    }

    @Test
    void sanear_maximoNoValido_lanza() {
        assertThatThrownBy(() -> SaneadorTexto.sanear("x", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
