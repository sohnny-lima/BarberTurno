package pe.barberturno.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static pe.barberturno.auth.PoliticaPassword.Incumplimiento.*;

class PoliticaPasswordTest {
    private final PoliticaPassword politica = new PoliticaPassword();
    @ParameterizedTest @ValueSource(strings = {"abcdefg1", "ábcdefg1", "漢字abcd12", "Abcde١٢٣"})
    void validar_letrasYDigitosUnicodePermitidos(String password) {
        assertThat(politica.validar(password)).isEmpty();
    }
    @Test void validar_limitesDeCaracteresYBytes() {
        assertThat(politica.validar("abcdef1")).containsExactly(LONGITUD);
        assertThat(politica.validar("a".repeat(71) + "1")).isEmpty();
        assertThat(politica.validar("a".repeat(72) + "1")).containsExactly(LONGITUD, BYTES_UTF8);
        assertThat(politica.validar("á".repeat(71) + "1")).containsExactly(BYTES_UTF8);
        assertThat(politica.validar("á".repeat(35) + "a1")).isEmpty();
        assertThat(politica.validar("á".repeat(35) + "ab1")).containsExactly(BYTES_UTF8);
        assertThat(politica.validar("𐐀abcde12")).isEmpty();
    }
    @Test void validar_devuelveTodosLosIncumplimientosSinLaPassword() {
        assertThat(politica.validar(null)).containsExactly(OBLIGATORIA);
        assertThat(politica.validar("")).containsExactly(LONGITUD, LETRA, DIGITO);
        assertThat(politica.validar("abcdefgh")).containsExactly(DIGITO);
        assertThat(politica.validar("12345678")).containsExactly(LETRA);
        assertThat(politica.validar("        ")).containsExactly(LETRA, DIGITO);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> politica.validar("").add(OBLIGATORIA));
    }
}
