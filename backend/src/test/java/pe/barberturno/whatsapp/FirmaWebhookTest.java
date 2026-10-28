package pe.barberturno.whatsapp;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FirmaWebhookTest {
    static final String SECRETO = "secreto-ficticio-de-prueba";
    static final byte[] CUERPO = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
    final FirmaWebhook firma = new FirmaWebhook(new WhatsAppPropiedades("token-ficticio", SECRETO, null));

    static String firmar(String secreto, byte[] cuerpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo));
    }

    @Test void firmaDelCuerpoExacto_esValidaTambienEnMayusculas() throws Exception {
        String cabecera = firmar(SECRETO, CUERPO);
        assertThat(firma.firmaValida(CUERPO, cabecera)).isTrue();
        assertThat(firma.firmaValida(CUERPO, "sha256=" + cabecera.substring(7).toUpperCase())).isTrue();
    }

    @Test void firmaAusenteMalFormadaErroneaOSobreCuerpoAlterado_seRechaza() throws Exception {
        String cabecera = firmar(SECRETO, CUERPO);
        byte[] alterado = CUERPO.clone();
        alterado[alterado.length - 2] = ' ';
        assertThat(firma.firmaValida(CUERPO, null)).isFalse();
        assertThat(firma.firmaValida(CUERPO, "")).isFalse();
        assertThat(firma.firmaValida(CUERPO, cabecera.substring(7))).as("sin prefijo").isFalse();
        assertThat(firma.firmaValida(CUERPO, "sha1=" + cabecera.substring(7))).isFalse();
        assertThat(firma.firmaValida(CUERPO, cabecera + "00")).as("longitud").isFalse();
        assertThat(firma.firmaValida(CUERPO, "sha256=" + "z".repeat(64))).as("no hexadecimal").isFalse();
        assertThat(firma.firmaValida(CUERPO, firmar("otro-secreto", CUERPO))).isFalse();
        assertThat(firma.firmaValida(alterado, cabecera)).isFalse();
    }

    @Test void sinSecretoConfigurado_ningunaFirmaEsValida() throws Exception {
        var sinSecreto = new FirmaWebhook(new WhatsAppPropiedades("token-ficticio", "", null));
        assertThat(sinSecreto.firmaValida(CUERPO, "sha256=" + "0".repeat(64))).isFalse();
        assertThat(sinSecreto.firmaValida(CUERPO, firmar(SECRETO, CUERPO))).isFalse();
    }

    @Test void tokenDeVerificacion_soloCoincidenciaExactaYConfigurada() {
        assertThat(firma.tokenValido("token-ficticio")).isTrue();
        assertThat(firma.tokenValido("token-ficticiO")).isFalse();
        assertThat(firma.tokenValido("token-ficticio ")).isFalse();
        assertThat(firma.tokenValido(null)).isFalse();
        var sinToken = new FirmaWebhook(new WhatsAppPropiedades("", SECRETO, null));
        assertThat(sinToken.tokenValido("")).isFalse();
        assertThat(sinToken.tokenValido(null)).isFalse();
    }
}
