package pe.barberturno.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AsociacionAndroidControllerTest {
    private static final String HUELLA = String.join(":", java.util.Collections.nCopies(32, "AB"));
    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void declaracion_sinPaquete_responde404() {
        var respuesta = new AsociacionAndroidController(new AsociacionAndroidPropiedades("", List.of(HUELLA))).declaracion();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).isNull();
    }

    @Test
    void declaracion_sinHuellas_responde404() {
        var respuesta = new AsociacionAndroidController(
                new AsociacionAndroidPropiedades("pe.barberturno.prueba.twa", List.of())).declaracion();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void declaracion_configurada_usaElFormatoOficialConUnaSentencia() {
        var segunda = HUELLA.replace("AB", "0F");
        var respuesta = new AsociacionAndroidController(
                new AsociacionAndroidPropiedades("pe.barberturno.prueba.twa", List.of(HUELLA, segunda))).declaracion();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).singleElement().satisfies(sentencia -> {
            assertThat(sentencia).containsOnlyKeys("relation", "target");
            assertThat(sentencia.get("relation")).isEqualTo(List.of("delegate_permission/common.handle_all_urls"));
            assertThat(sentencia.get("target")).isEqualTo(Map.of(
                    "namespace", "android_app",
                    "package_name", "pe.barberturno.prueba.twa",
                    "sha256_cert_fingerprints", List.of(HUELLA, segunda)));
        });
    }

    @Test
    void propiedades_validas_oVacias_noTienenErrores() {
        assertThat(validador.validate(new AsociacionAndroidPropiedades("pe.barberturno.prueba.twa", List.of(HUELLA))))
                .isEmpty();
        assertThat(validador.validate(new AsociacionAndroidPropiedades("", List.of()))).isEmpty();
    }

    @Test
    void propiedades_conHuellaMinusculaCortaOSinSeparadores_seRechazan() {
        for (String mala : List.of(HUELLA.toLowerCase(), HUELLA.substring(3), HUELLA.replace(":", ""), "AB")) {
            assertThat(validador.validate(new AsociacionAndroidPropiedades("pe.barberturno.prueba.twa", List.of(mala))))
                    .as(mala).hasSize(1);
        }
    }

    @Test
    void propiedades_conPaqueteInvalido_seRechazan() {
        for (String malo : List.of("barberturno", "pe..barberturno", "1pe.barberturno", "pe.barber-turno", "https://x.y")) {
            assertThat(validador.validate(new AsociacionAndroidPropiedades(malo, List.of(HUELLA)))).as(malo).hasSize(1);
        }
    }
}
