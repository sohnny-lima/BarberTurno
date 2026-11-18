package pe.barberturno.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Nombre de la ficha a partir del perfil de WhatsApp (13-v2-10 §7): limpio, acotado a 100 caracteres
 * sin partir pares sustitutos, y con un valor por defecto cuando no hay nombre utilizable.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class ConsentimientoConversacionTest {

    @Test
    void nombre_nuloVacioOSoloControles_usaElNombrePorDefecto() {
        assertThat(ConsentimientoConversacion.nombre(null)).isEqualTo(ConsentimientoConversacion.NOMBRE_POR_DEFECTO);
        assertThat(ConsentimientoConversacion.nombre("   ")).isEqualTo(ConsentimientoConversacion.NOMBRE_POR_DEFECTO);
        assertThat(ConsentimientoConversacion.nombre("\u0000​\t\n")).isEqualTo(ConsentimientoConversacion.NOMBRE_POR_DEFECTO);
    }

    @Test
    void nombre_conControlesYEspacios_seLimpiaYReduce() {
        assertThat(ConsentimientoConversacion.nombre("  Ana\t​Pérez \n ")).isEqualTo("Ana Pérez");
    }

    @Test
    void nombre_largo_seRecortaA100PuntosDeCodigoSinPartirEmojis() {
        String largo = "😀".repeat(150);
        String recortado = ConsentimientoConversacion.nombre(largo);
        assertThat(recortado.codePointCount(0, recortado.length())).isEqualTo(100);
        assertThat(recortado).isEqualTo("😀".repeat(100));
        assertThat(ConsentimientoConversacion.nombre("a".repeat(100))).hasSize(100);
    }
}
