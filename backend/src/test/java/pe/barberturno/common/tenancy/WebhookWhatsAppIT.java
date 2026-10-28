package pe.barberturno.common.tenancy;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

/** CH-1…CH-3: verificación, firma sobre el cuerpo crudo, idempotencia en la base y exención de CSRF acotada (V2-CP-06). */
@TestPropertySource(properties = {"barberturno.whatsapp.verify-token=token-ficticio-it",
        "barberturno.whatsapp.app-secret=secreto-ficticio-it"})
class WebhookWhatsAppIT extends PlataformaPruebaBase {
    static final String SECRETO = "secreto-ficticio-it";
    static final String RUTA = "/api/whatsapp/webhook";

    static byte[] ejemplo() throws Exception {
        try (var entrada = WebhookWhatsAppIT.class.getResourceAsStream("/whatsapp/mensajes-y-estados.json")) {
            return entrada.readAllBytes();
        }
    }

    static String firmar(byte[] cuerpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo));
    }

    HttpResponse<String> publicar(Sesion sesion, String ruta, byte[] cuerpo, String firma) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo));
        if (firma != null) peticion.header("X-Hub-Signature-256", firma);
        return sesion.cliente().send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    int eventos() {
        return jdbc.queryForObject("select count(*) from evento_whatsapp_entrante", Integer.class);
    }

    @Test void verificacion_devuelveElDesafioSoloConModoYTokenCorrectos() throws Exception {
        var anonima = anonima();
        var correcta = http(anonima, "GET", RUTA + "?hub.mode=subscribe&hub.verify_token=token-ficticio-it&hub.challenge=1158201444", null, null);
        assertThat(correcta.statusCode()).isEqualTo(200);
        assertThat(correcta.body()).isEqualTo("1158201444");
        assertThat(correcta.headers().firstValue("Content-Type")).hasValueSatisfying(tipo -> assertThat(tipo).startsWith("text/plain"));
        assertThat(http(anonima, "GET", RUTA + "?hub.mode=subscribe&hub.verify_token=otro&hub.challenge=1", null, null).statusCode()).isEqualTo(403);
        assertThat(http(anonima, "GET", RUTA + "?hub.mode=subscribe&hub.challenge=1", null, null).statusCode()).isEqualTo(403);
        assertThat(http(anonima, "GET", RUTA, null, null).statusCode()).isEqualTo(403);
        assertThat(http(anonima, "GET", "/api/n/a/whatsapp/webhook?hub.mode=subscribe&hub.verify_token=token-ficticio-it&hub.challenge=1", null, null)
                .statusCode()).as("nunca como ruta de negocio").isEqualTo(404);
    }

    @Test void firmaAusenteErroneaOSobreCuerpoAlterado_401SinNingunaFila() throws Exception {
        var anonima = anonima();
        byte[] cuerpo = ejemplo();
        byte[] alterado = new String(cuerpo, StandardCharsets.UTF_8).replace("Hola", "Hole").getBytes(StandardCharsets.UTF_8);
        assertThat(publicar(anonima, RUTA, cuerpo, null).statusCode()).isEqualTo(401);
        assertThat(publicar(anonima, RUTA, cuerpo, "sha256=" + "0".repeat(64)).statusCode()).isEqualTo(401);
        assertThat(publicar(anonima, RUTA, cuerpo, firmar(cuerpo).substring(7)).statusCode()).isEqualTo(401);
        assertThat(publicar(anonima, RUTA, alterado, firmar(cuerpo)).statusCode()).isEqualTo(401);
        assertThat(eventos()).isZero();
    }

    @Test void firmaValida_unaFilaPorMensajeYEstado_sinCsrfNiSesion_enMenosDeDosSegundos() throws Exception {
        var anonima = anonima();
        byte[] cuerpo = ejemplo();
        long inicio = System.nanoTime();
        var respuesta = publicar(anonima, RUTA, cuerpo, firmar(cuerpo));
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEmpty();
        assertThat(ms).as("V2-RNF-03, %d ms", ms).isLessThan(2000);
        assertThat(jdbc.queryForList("select clave_idempotencia from evento_whatsapp_entrante order by id", String.class)).containsExactly(
                "m:wamid.FICTICIO-MENSAJE-001", "m:wamid.FICTICIO-MENSAJE-002",
                "s:wamid.FICTICIO-SALIENTE-001:delivered", "s:wamid.FICTICIO-SALIENTE-001:read");
        var fila = jdbc.queryForMap("""
                select tipo, phone_number_id, negocio_id, estado, motivo, intentos::int as intentos, recibido_en = proximo_intento as mismo,
                       cuerpo->'contacto'->>'waId' as wa, cuerpo->'dato'->'text'->>'body' as texto
                from evento_whatsapp_entrante where clave_idempotencia = 'm:wamid.FICTICIO-MENSAJE-001'""");
        assertThat(fila).containsEntry("tipo", "MENSAJE").containsEntry("phone_number_id", "100000000000001")
                .containsEntry("estado", "PENDIENTE").containsEntry("intentos", 0).containsEntry("mismo", true)
                .containsEntry("wa", "51900000001").containsEntry("texto", "Hola, quiero reservar");
        assertThat(fila.get("negocio_id")).as("el webhook no resuelve el negocio (V2-DA-52)").isNull();
        assertThat(fila.get("motivo")).isNull();
        assertThat(jdbc.queryForObject("select recibido_en from evento_whatsapp_entrante limit 1", java.sql.Timestamp.class).toInstant())
                .isEqualTo(reloj.instant());
    }

    @Test void mismoCuerpoDosVeces_unaFilaPorClave_yUnoNuevoSoloAnadeLoNuevo() throws Exception {
        var anonima = anonima();
        byte[] cuerpo = ejemplo();
        assertThat(publicar(anonima, RUTA, cuerpo, firmar(cuerpo)).statusCode()).isEqualTo(200);
        assertThat(publicar(anonima, RUTA, cuerpo, firmar(cuerpo)).statusCode()).as("Meta no reintenta un 200").isEqualTo(200);
        assertThat(eventos()).isEqualTo(4);
        byte[] otro = new String(cuerpo, StandardCharsets.UTF_8).replace("FICTICIO-MENSAJE-002", "FICTICIO-MENSAJE-003")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(publicar(anonima, RUTA, otro, firmar(otro)).statusCode()).isEqualTo(200);
        assertThat(eventos()).isEqualTo(5);
    }

    @Test void cuerpoFirmadoNoJsonOEnorme_sinFilas_ySinEventosAunAs200() throws Exception {
        var anonima = anonima();
        byte[] roto = "{\"object\":".getBytes(StandardCharsets.UTF_8);
        assertThat(publicar(anonima, RUTA, roto, firmar(roto)).statusCode()).isEqualTo(400);
        byte[] lista = "[]".getBytes(StandardCharsets.UTF_8);
        assertThat(publicar(anonima, RUTA, lista, firmar(lista)).statusCode()).isEqualTo(400);
        byte[] enorme = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(enorme, (byte) ' ');
        assertThat(publicar(anonima, RUTA, enorme, firmar(enorme)).statusCode()).isEqualTo(413);
        byte[] otroObjeto = "{\"object\":\"page\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        assertThat(publicar(anonima, RUTA, otroObjeto, firmar(otroObjeto)).statusCode()).as("sin eventos, pero autenticado").isEqualTo(200);
        assertThat(eventos()).isZero();
    }

    @Test void exencionDeCsrf_soloEnElPostDelWebhook() throws Exception {
        byte[] cuerpo = ejemplo();
        // Con una sesión válida, el resto de escrituras sigue exigiendo CSRF.
        assertThat(http(admin, "POST", "/api/plataforma/negocios", altaJson("sin-csrf"), null).statusCode()).isEqualTo(403);
        assertThat(publicar(admin, "/api/whatsapp/webhook/otra", cuerpo, firmar(cuerpo)).statusCode()).isEqualTo(403);
        assertThat(publicar(admin, "/api/n/a/whatsapp/webhook", cuerpo, firmar(cuerpo)).statusCode()).isIn(403, 404);
        var put = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + RUTA)).header("X-Hub-Signature-256", firmar(cuerpo))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(cuerpo)).build();
        assertThat(admin.cliente().send(put, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(eventos()).isZero();
        // La misma notificación con la cookie de una sesión también entra por la firma, no por la sesión.
        assertThat(publicar(admin, RUTA, cuerpo, firmar(cuerpo)).statusCode()).isEqualTo(200);
        assertThat(eventos()).isEqualTo(4);
    }
}
