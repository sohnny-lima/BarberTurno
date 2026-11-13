package pe.barberturno.whatsapp;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/**
 * Pruebas unitarias de {@link SalidaWhatsAppPropiedades} (12-v2-09-whatsapp-salida.md §4.7).
 * Valida valores por defecto, enmascaramiento del token de Graph en {@code toString()} y rechazo de
 * rangos y combinaciones incoherentes de tiempo al arrancar.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
class SalidaWhatsAppPropiedadesTest {

    @Test
    void valoresPorDefecto_cumplenRangosYEnvioNoConfiguradoSinToken() {
        var propiedades = new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 5,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofMinutes(5), null);

        assertThat(propiedades.habilitado()).isTrue();
        assertThat(propiedades.intervalo()).isEqualTo(Duration.ofSeconds(2));
        assertThat(propiedades.lote()).isEqualTo(20);
        assertThat(propiedades.maxIntentos()).isEqualTo(5);
        assertThat(propiedades.esperaBase()).isEqualTo(Duration.ofSeconds(30));
        assertThat(propiedades.esperaMaxima()).isEqualTo(Duration.ofMinutes(5));
        assertThat(propiedades.esperaConciliacion()).isEqualTo(Duration.ofMinutes(2));
        assertThat(propiedades.reclamo()).isEqualTo(Duration.ofSeconds(60));
        assertThat(propiedades.antiguedadMaxima()).isEqualTo(Duration.ofHours(6));
        assertThat(propiedades.margenVentana()).isEqualTo(Duration.ofMinutes(5));

        assertThat(propiedades.graph().urlBase()).isEqualTo("https://graph.facebook.com");
        assertThat(propiedades.graph().version()).isEqualTo("v26.0");
        assertThat(propiedades.graph().token()).isEmpty();
        assertThat(propiedades.graph().conexion()).isEqualTo(Duration.ofSeconds(5));
        assertThat(propiedades.graph().respuesta()).isEqualTo(Duration.ofSeconds(15));
        assertThat(propiedades.envioConfigurado()).isFalse();
    }

    @Test
    void toString_enmascaraTokenDeGraphYNoMuestraSecretos() {
        var graphConToken = new SalidaWhatsAppPropiedades.Graph("https://graph.facebook.com", "v26.0",
                "token-super-secreto", Duration.ofSeconds(5), Duration.ofSeconds(15));
        assertThat(graphConToken.toString()).doesNotContain("token-super-secreto").contains("***");

        var graphSinToken = new SalidaWhatsAppPropiedades.Graph("https://graph.facebook.com", "v26.0",
                "", Duration.ofSeconds(5), Duration.ofSeconds(15));
        assertThat(graphSinToken.toString()).doesNotContain("***");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080", "http://127.0.0.1:9999", "https://graph.facebook.com"})
    void graph_origenesValidos_admitidos(String urlBase) {
        var graph = new SalidaWhatsAppPropiedades.Graph(urlBase, "v26.0", "token",
                Duration.ofSeconds(5), Duration.ofSeconds(15));
        assertThat(graph.urlBase()).isEqualTo(urlBase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://inseguro.com", "ftp://servidor", "https://graph.facebook.com/", "http://192.168.1.1:8080"})
    void graph_origenesInvalidos_rechazados(String urlBaseInvalida) {
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades.Graph(urlBaseInvalida, "v26.0", "token",
                Duration.ofSeconds(5), Duration.ofSeconds(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.0", "v26", "v1.2.3", "version26.0", ""})
    void graph_versionesInvalidas_rechazadas(String versionInvalida) {
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades.Graph("https://graph.facebook.com", versionInvalida, "token",
                Duration.ofSeconds(5), Duration.ofSeconds(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reclamo_menorOIgualQueConexionMasRespuesta_rechazado() {
        var graph = new SalidaWhatsAppPropiedades.Graph("https://graph.facebook.com", "v26.0", "token",
                Duration.ofSeconds(10), Duration.ofSeconds(20)); // suma = 30s
        // Reclamo de 30s <= 30s de timeout -> debe fallar
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 5,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofSeconds(30), Duration.ofHours(6), Duration.ofMinutes(5), graph))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangosIncoherentes_loteMaxIntentosMargen_rechazados() {
        var graph = new SalidaWhatsAppPropiedades.Graph("https://graph.facebook.com", "v26.0", "token",
                Duration.ofSeconds(5), Duration.ofSeconds(15));

        // Lote 0
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 0, 5,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofMinutes(5), graph))
                .isInstanceOf(IllegalArgumentException.class);

        // MaxIntentos 0 o 101
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 0,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofMinutes(5), graph))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 101,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofMinutes(5), graph))
                .isInstanceOf(IllegalArgumentException.class);

        // Espera máxima < espera base
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 5,
                Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofMinutes(5), graph))
                .isInstanceOf(IllegalArgumentException.class);

        // Margen ventana >= 24h
        assertThatThrownBy(() -> new SalidaWhatsAppPropiedades(true, Duration.ofSeconds(2), 20, 5,
                Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2),
                Duration.ofSeconds(60), Duration.ofHours(6), Duration.ofHours(24), graph))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
