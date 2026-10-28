package pe.barberturno.common.tenancy;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** CH-4: alta manual del canal por la plataforma (V2-DA-56), V2-RN-07, auditoría, roles y CSRF. */
class CanalWhatsAppIT extends PlataformaPruebaBase {
    static String ruta(long negocio) { return "/api/plataforma/negocios/" + negocio + "/canal-whatsapp"; }

    static String datos(String numero) {
        return "{\"wabaId\":\"900000000000001\",\"phoneNumberId\":\"" + numero + "\",\"numeroVisible\":\"+15550000001\"}";
    }

    int auditorias(String accion, long negocio) {
        return jdbc.queryForObject("select count(*) from auditoria_plataforma where accion=? and negocio_id=?", Integer.class, accion, negocio);
    }

    String codigo(HttpResponse<String> respuesta) throws Exception {
        return mapper.readTree(respuesta.body()).get("codigo").asString();
    }

    @Test void ciclo_altaConsultaCambioYDesconexionIdempotente_auditados() throws Exception {
        assertThat(http(admin, "GET", ruta(a), null, null).statusCode()).isEqualTo(404);
        var alta = http(admin, "PUT", ruta(a), datos("100000000000001"), token(admin));
        assertThat(alta.statusCode()).isEqualTo(200);
        var canal = mapper.readTree(alta.body());
        assertThat(canal.get("negocioId").asLong()).isEqualTo(a);
        assertThat(canal.get("phoneNumberId").asString()).isEqualTo("100000000000001");
        assertThat(canal.get("estado").asString()).isEqualTo("ACTIVO");
        assertThat(alta.body()).doesNotContain("token");
        assertThat(mapper.readTree(http(admin, "GET", ruta(a), null, null).body()).get("numeroVisible").asString()).isEqualTo("+15550000001");
        assertThat(http(admin, "PUT", ruta(a), datos("100000000000002"), token(admin)).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForList("select phone_number_id from canal_whatsapp where negocio_id=?", String.class, a))
                .containsExactly("100000000000002");
        assertThat(auditorias("REGISTRAR_CANAL_WHATSAPP", a)).isEqualTo(2);
        for (int vez = 0; vez < 2; vez++) {
            assertThat(http(admin, "POST", ruta(a) + "/desconexion", null, token(admin)).statusCode()).isEqualTo(204);
        }
        assertThat(jdbc.queryForObject("select estado from canal_whatsapp where negocio_id=?", String.class, a)).isEqualTo("DESCONECTADO");
        assertThat(auditorias("DESCONECTAR_CANAL_WHATSAPP", a)).as("idempotente").isEqualTo(1);
        assertThat(mapper.readTree(http(admin, "PUT", ruta(a), datos("100000000000002"), token(admin)).body()).get("estado").asString())
                .isEqualTo("ACTIVO");
        assertThat(http(admin, "POST", ruta(b) + "/desconexion", null, token(admin)).statusCode()).as("sin canal").isEqualTo(404);
    }

    @Test void unNumeroPorNegocio_numeroDeOtro409SinCambiarNada() throws Exception {
        assertThat(http(admin, "PUT", ruta(a), datos("100000000000001"), token(admin)).statusCode()).isEqualTo(200);
        var enUso = http(admin, "PUT", ruta(b), datos("100000000000001"), token(admin));
        assertThat(enUso.statusCode()).isEqualTo(409);
        assertThat(codigo(enUso)).isEqualTo("NUMERO_WHATSAPP_EN_USO");
        assertThat(jdbc.queryForObject("select count(*) from canal_whatsapp where negocio_id=?", Integer.class, b)).isZero();
        assertThat(auditorias("REGISTRAR_CANAL_WHATSAPP", b)).isZero();
        assertThat(http(admin, "PUT", ruta(b), datos("100000000000002"), token(admin)).statusCode()).isEqualTo(200);
        assertThat(codigo(http(admin, "PUT", ruta(a), datos("100000000000002"), token(admin)))).isEqualTo("NUMERO_WHATSAPP_EN_USO");
        assertThat(jdbc.queryForList("select phone_number_id from canal_whatsapp where negocio_id=?", String.class, a))
                .containsExactly("100000000000001");
    }

    @Test void carreraDeDosNegociosPorElMismoNumero_unoGanaYElOtro409() throws Exception {
        var barrera = new CyclicBarrier(2);
        List<Future<HttpResponse<String>>> respuestas = new ArrayList<>();
        try (var hilos = Executors.newFixedThreadPool(2)) {
            for (long negocio : new long[]{a, b}) {
                respuestas.add(hilos.submit(() -> {
                    barrera.await();
                    return http(admin, "PUT", ruta(negocio), datos("100000000000009"), token(admin));
                }));
            }
        }
        var estados = new ArrayList<Integer>();
        for (var respuesta : respuestas) {
            estados.add(respuesta.get().statusCode());
            if (respuesta.get().statusCode() == 409) assertThat(codigo(respuesta.get())).isEqualTo("NUMERO_WHATSAPP_EN_USO");
        }
        assertThat(estados).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from canal_whatsapp where phone_number_id='100000000000009'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from auditoria_plataforma where accion='REGISTRAR_CANAL_WHATSAPP'", Integer.class)).isEqualTo(1);
    }

    @Test void negocioInexistenteSuspendidoOSinPropietario_yDatosInvalidos() throws Exception {
        assertThat(http(admin, "PUT", ruta(999_999), datos("100000000000001"), token(admin)).statusCode()).isEqualTo(404);
        assertThat(http(admin, "GET", ruta(999_999), null, null).statusCode()).isEqualTo(404);
        assertThat(http(admin, "PATCH", "/api/plataforma/negocios/" + b + "/estado", "{\"estado\":\"SUSPENDIDO\"}", token(admin)).statusCode()).isEqualTo(200);
        var suspendido = http(admin, "PUT", ruta(b), datos("100000000000001"), token(admin));
        assertThat(suspendido.statusCode()).isEqualTo(409);
        assertThat(codigo(suspendido)).isEqualTo("NEGOCIO_SUSPENDIDO");
        long sinPropietario = alta("sin-propietario");
        var pendiente = http(admin, "PUT", ruta(sinPropietario), datos("100000000000001"), token(admin));
        assertThat(pendiente.statusCode()).isEqualTo(409);
        assertThat(codigo(pendiente)).isEqualTo("CONFLICTO");
        for (String invalido : List.of(
                "{\"wabaId\":\"9\",\"phoneNumberId\":\"12a\",\"numeroVisible\":\"+15550000001\"}",
                "{\"wabaId\":\"9\",\"phoneNumberId\":\"1\",\"numeroVisible\":\"555\"}",
                "{\"wabaId\":\"\",\"phoneNumberId\":\"1\",\"numeroVisible\":\"+15550000001\"}",
                "{\"phoneNumberId\":\"1\",\"numeroVisible\":\"+15550000001\"}")) {
            assertThat(http(admin, "PUT", ruta(a), invalido, token(admin)).statusCode()).as(invalido).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("select count(*) from canal_whatsapp", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from auditoria_plataforma where accion like '%CANAL_WHATSAPP'", Integer.class)).isZero();
    }

    @Test void soloPlataformaConCsrf_enCadaOperacion() throws Exception {
        assertThat(http(admin, "PUT", ruta(a), datos("100000000000001"), token(admin)).statusCode()).isEqualTo(200);
        for (var sesion : List.of(titular, gestorLocal)) {
            assertThat(http(sesion, "GET", ruta(a), null, null).statusCode()).isEqualTo(403);
            assertThat(http(sesion, "PUT", ruta(a), datos("100000000000002"), token(sesion)).statusCode()).isEqualTo(403);
            assertThat(http(sesion, "POST", ruta(a) + "/desconexion", null, token(sesion)).statusCode()).isEqualTo(403);
        }
        var anonima = anonima();
        assertThat(http(anonima, "GET", ruta(a), null, null).statusCode()).isEqualTo(401);
        assertThat(http(admin, "PUT", ruta(a), datos("100000000000002"), null).statusCode()).as("sin CSRF").isEqualTo(403);
        assertThat(http(admin, "PUT", ruta(a), datos("100000000000002"), "csrf-incorrecto").statusCode()).as("CSRF incorrecto").isEqualTo(403);
        assertThat(http(admin, "POST", ruta(a) + "/desconexion", null, null).statusCode()).as("sin CSRF").isEqualTo(403);
        assertThat(http(admin, "POST", ruta(a) + "/desconexion", null, "csrf-incorrecto").statusCode()).as("CSRF incorrecto").isEqualTo(403);
        assertThat(jdbc.queryForMap("select phone_number_id, estado from canal_whatsapp where negocio_id=?", a))
                .containsEntry("phone_number_id", "100000000000001").containsEntry("estado", "ACTIVO");
        assertThat(http(admin, "GET", "/api/n/a/plataforma/negocios/" + a + "/canal-whatsapp", null, null).statusCode())
                .as("nunca bajo una ruta de negocio").isEqualTo(404);
    }
}
