package pe.barberturno.common.tenancy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** V2-DA-44: proyección exacta pública con aislamiento A/B y suspensión inmediata. */
class NegocioPublicoIT extends PlataformaPruebaBase {
    @Test void parametrosDistintosDeAyB_publicos_ySinCamposInternos() throws Exception {
        en("a", () -> jdbc.update("update negocio set horizonte_dias=7, anticipacion_cambio_cliente_min=60, limite_reservas_activas=2, confirmacion_manual=true where id=?", a));
        en("b", () -> jdbc.update("update negocio set horizonte_dias=15, anticipacion_cambio_cliente_min=90, limite_reservas_activas=4, confirmacion_manual=false where id=?", b));
        var visitante = anonima();
        for (String slug : java.util.List.of("a", "b")) {
            var r = http(visitante, "GET", "/api/n/" + slug + "/negocio", null, null);
            assertThat(r.statusCode()).isEqualTo(200);
            var dto = mapper.readTree(r.body());
            assertThat(dto.properties()).extracting(java.util.Map.Entry::getKey).containsExactlyInAnyOrder(
                    "slug", "nombre", "horizonteDias", "anticipacionCambioClienteMin", "limiteReservasActivas", "confirmacionManual");
            assertThat(dto.get("slug").asString()).isEqualTo(slug);
            assertThat(dto.get("nombre").asString()).isEqualTo("Negocio ficticio");
            assertThat(dto.get("horizonteDias").asInt()).isEqualTo(slug.equals("a") ? 7 : 15);
            assertThat(dto.get("anticipacionCambioClienteMin").asInt()).isEqualTo(slug.equals("a") ? 60 : 90);
            assertThat(dto.get("limiteReservasActivas").asInt()).isEqualTo(slug.equals("a") ? 2 : 4);
            assertThat(dto.get("confirmacionManual").asBoolean()).isEqualTo(slug.equals("a"));
        }
    }

    @Test void suspendidoOInexistente_404_tambienConSesion_yBConservaSuLectura() throws Exception {
        estado(a, "SUSPENDIDO");
        for (var sesion : java.util.List.of(anonima(), titular, gestorLocal, admin)) {
            for (String slug : java.util.List.of("a", "inexistente")) {
                var r = http(sesion, "GET", "/api/n/" + slug + "/negocio", null, null);
                assertThat(r.statusCode()).isEqualTo(404);
                assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo("NO_ENCONTRADO");
            }
            assertThat(http(sesion, "GET", "/api/n/b/negocio", null, null).statusCode()).isEqualTo(200);
        }
        estado(a, "ACTIVO");
        assertThat(http(anonima(), "GET", "/api/n/a/negocio", null, null).statusCode()).isEqualTo(200);
    }
}
