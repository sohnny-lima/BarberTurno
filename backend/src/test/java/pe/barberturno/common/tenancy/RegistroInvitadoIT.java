package pe.barberturno.common.tenancy;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.platform.*;
import pe.barberturno.platform.dto.RegistroInvitadoDto;
import static org.assertj.core.api.Assertions.*;

/** V2-DA-47: alta atómica por capacidad, servidor/CSRF reales, aislamiento y concurrencia. */
class RegistroInvitadoIT extends PlataformaPruebaBase {
    static final String DATOS = "{\"nombre\":\"Invitado ficticio\",\"telefono\":\"999000002\","
            + "\"password\":\"ClaveFicticia123\",\"aceptaPrivacidad\":true}";
    @Autowired InvitacionService invitaciones;

    String invitar(String rol) throws Exception {
        var r = http(titular, "POST", "/api/n/a/invitaciones",
                "{\"correo\":\"nuevo@ejemplo.test\",\"rol\":\"" + rol + "\"}", token(titular));
        assertThat(r.statusCode()).isEqualTo(201);
        return mapper.readTree(r.body()).get("enlaceInvitacion").asString();
    }

    Sesion visitante() throws Exception {
        var s = anonima();
        assertThat(http(s, "GET", "/api/auth/sesion", null, null).statusCode()).isEqualTo(401);
        return s;
    }

    List<String> cuentas() {
        return jdbc.queryForList("select to_jsonb(u)::text from usuario u order by id", String.class);
    }

    @ParameterizedTest @ValueSource(strings = {"BARBERO", "ENCARGADO", "PROPIETARIO"})
    void pendiente_creaCuentaConCorreoFijo_yMembresiaSinFichaNiRolGlobal(String rol) throws Exception {
        String enlace = invitar(rol);
        var antesB = huella("b"); var antesAlias = huella("barberturno");
        // La barbería del alias no participa ni cuando está suspendida (regresión H-103-01).
        estado(directorio.buscar("barberturno").orElseThrow(), "SUSPENDIDO");
        var s = visitante(); String previo = token(s);
        var r = http(s, "POST", enlace + "/registro", DATOS.substring(0, DATOS.length() - 1)
                + ",\"correo\":\"otro@ejemplo.test\",\"rol\":\"ADMIN_PLATAFORMA\",\"negocioId\":" + b + "}", previo);
        assertThat(r.statusCode()).isEqualTo(201);
        assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
        var dto = mapper.readTree(r.body()); long id = dto.get("id").asLong();
        assertThat(dto.get("correo").asString()).isEqualTo("nuevo@ejemplo.test");
        assertThat(dto.get("rolPlataforma").isNull()).isTrue();
        assertThat(dto.get("rolNegocio").isNull()).isTrue();
        assertThat(dto.get("rol").isNull()).isTrue();
        assertThat(token(s)).isNotEqualTo(previo);
        assertThat(r.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith("BT_SESION=")
                && c.contains("HttpOnly") && c.contains("SameSite=Strict") && c.contains("Path=/"));
        assertThat(http(s, "GET", "/api/auth/sesion", null, null).statusCode()).isEqualTo(200);
        var local = http(s, "GET", "/api/n/a/sesion", null, null);
        assertThat(local.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(local.body()).get("rolNegocio").asString()).isEqualTo(rol);
        var cuenta = usuarios.findById(id).orElseThrow();
        assertThat(cuenta.getRolPlataforma()).isEqualTo(RolPlataforma.NINGUNO);
        assertThat(passwords.matches(CLAVE, cuenta.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("select privacidad_aceptada_en is not null from usuario where id=?", Boolean.class, id)).isTrue();
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, id).orElseThrow().getRol().name()).isEqualTo(rol);
        assertThat(jdbc.queryForObject("select count(*) from membresia where usuario_id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vinculo_cliente where usuario_id=?", Long.class, id)).isZero();
        for (String slug : List.of("a", "b", "barberturno")) {
            assertThat(en(slug, () -> jdbc.queryForObject("select count(*) from cliente where usuario_id=?", Long.class, id))).isZero();
            assertThat(en(slug, () -> jdbc.queryForObject("select count(*) from barbero where usuario_id=?", Long.class, id))).isZero();
        }
        assertThat(huella("b")).isEqualTo(antesB); assertThat(huella("barberturno")).isEqualTo(antesAlias);
        assertThat(http(s, "GET", "/api/n/b/equipo", null, null).statusCode()).isEqualTo(403);
        assertThat(http(s, "GET", "/api/plataforma/negocios", null, null).statusCode()).isEqualTo(403);
    }

    @ParameterizedTest @ValueSource(strings = {"existente", "caducada", "revocada", "usada", "suspendida", "desconocida"})
    void rechazo_noCambiaCuentasMembresiasNiLaOtraBarberia(String caso) throws Exception {
        String enlace = invitar("BARBERO");
        if (caso.equals("existente")) cuenta("nuevo");
        if (caso.equals("caducada")) reloj.adelantar(Duration.ofDays(7));
        if (caso.equals("revocada")) new TransactionTemplate(gestor).executeWithoutResult(tx ->
                jdbc.update("update invitacion set estado='REVOCADA' where correo='nuevo@ejemplo.test'"));
        if (caso.equals("usada")) {
            var previo = visitante();
            assertThat(http(previo, "POST", enlace + "/registro", DATOS, token(previo)).statusCode()).isEqualTo(201);
        }
        if (caso.equals("suspendida")) estado(a, "SUSPENDIDO");
        if (caso.equals("desconocida")) enlace = "/api/invitaciones/" + "x".repeat(43);
        var antes = cuentas(); var antesA = huella("a"); var antesB = huella("b");
        var invitacionesAntes = jdbc.queryForList("select to_jsonb(i)::text from invitacion i order by id", String.class);
        var s = visitante(); var r = http(s, "POST", enlace + "/registro", DATOS, token(s));
        assertThat(r.statusCode()).isEqualTo(caso.equals("desconocida") ? 404 : 409);
        assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo(switch (caso) {
            case "desconocida" -> "NO_ENCONTRADO";
            case "suspendida" -> "NEGOCIO_SUSPENDIDO";
            case "existente" -> "CORREO_DUPLICADO";
            default -> "INVITACION_NO_VIGENTE";
        });
        assertThat(cuentas()).isEqualTo(antes); assertThat(huella("a")).isEqualTo(antesA);
        assertThat(huella("b")).isEqualTo(antesB);
        assertThat(jdbc.queryForList("select to_jsonb(i)::text from invitacion i order by id", String.class)).isEqualTo(invitacionesAntes);
        assertThat(s.cookies().getCookieStore().getCookies()).noneMatch(c -> c.getName().equals("BT_SESION"));
    }

    @ParameterizedTest @ValueSource(strings = {"nombre", "telefono", "password", "debil", "utf8", "privacidad", "nula"})
    void validacionYRN25_noCreanCuentaNiConsumenCapacidad(String caso) throws Exception {
        String enlace = invitar("BARBERO"); var s = visitante();
        String datos = switch (caso) {
            case "nombre" -> DATOS.replace("Invitado ficticio", "");
            case "telefono" -> DATOS.replace("999000002", "123");
            case "password" -> DATOS.replace(CLAVE, "");
            case "debil" -> DATOS.replace(CLAVE, "abcdefgh");
            case "utf8" -> DATOS.replace(CLAVE, "á".repeat(36) + "1");
            case "privacidad" -> DATOS.replace("true", "false");
            default -> DATOS.replace("true", "null");
        };
        var antes = cuentas();
        var r = http(s, "POST", enlace + "/registro", datos, token(s));
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo("VALIDACION");
        assertThat(cuentas()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("select estado from invitacion where correo='nuevo@ejemplo.test'", String.class)).isEqualTo("PENDIENTE");
    }

    @Test void csrfAusenteOIncorrecto_conSesionValida_yRutaGlobalSinPrefijo() throws Exception {
        String enlace = invitar("BARBERO"); var antes = cuentas();
        for (String csrf : new String[]{null, "incorrecto"}) {
            assertThat(http(titular, "POST", enlace + "/registro", DATOS, csrf).statusCode()).isEqualTo(403);
        }
        for (String slug : List.of("a", "b")) {
            assertThat(http(titular, "POST", "/api/n/" + slug + enlace.substring(4) + "/registro", DATOS, token(titular)).statusCode()).isEqualTo(404);
        }
        assertThat(cuentas()).isEqualTo(antes);
        // Es un registro público: no hay rol excluido. La capacidad y CSRF son las acreditaciones.
        assertThat(http(titular, "POST", enlace + "/registro", DATOS, token(titular)).statusCode()).isEqualTo(201);
    }

    @Test void rollback_revierteCuentaMembresiaYConsumoJuntos() throws Exception {
        String enlace = invitar("BARBERO"); var antes = cuentas();
        new TransactionTemplate(gestor).executeWithoutResult(tx -> {
            var u = invitaciones.registrar(enlace.substring("/api/invitaciones/".length()),
                    new RegistroInvitadoDto("Invitado ficticio", "999000002", CLAVE, true));
            assertThat(membresias.findByNegocioIdAndUsuarioId(a, u.getId())).isPresent();
            tx.setRollbackOnly();
        });
        assertThat(cuentas()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("select estado from invitacion where correo='nuevo@ejemplo.test'", String.class)).isEqualTo("PENDIENTE");
        var s = visitante();
        assertThat(http(s, "POST", enlace + "/registro", DATOS, token(s)).statusCode()).isEqualTo(201);
    }

    @RepeatedTest(20)
    void dosRegistrosSimultaneos_unGanador_sinCuentasHuerfanas() throws Exception {
        String enlace = invitar("BARBERO"); var uno = visitante(); var dos = visitante();
        var antesB = huella("b"); int anteriores = cuentas().size();
        var barrera = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var resultados = new ArrayList<Future<Integer>>();
            for (var s : List.of(uno, dos)) resultados.add(hilos.submit(() -> {
                barrera.await(10, TimeUnit.SECONDS);
                return http(s, "POST", enlace + "/registro", DATOS, token(s)).statusCode();
            }));
            assertThat(List.of(resultados.get(0).get(30, TimeUnit.SECONDS), resultados.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(cuentas()).hasSize(anteriores + 1);
        long id = usuarios.findByCorreo("nuevo@ejemplo.test").orElseThrow().getId();
        assertThat(jdbc.queryForObject("select count(*) from membresia where usuario_id=? and negocio_id=?", Long.class, id, a)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select estado,aceptada_por from invitacion where correo='nuevo@ejemplo.test'"))
                .containsEntry("estado", "ACEPTADA").containsEntry("aceptada_por", id);
        assertThat(jdbc.queryForObject("select count(*) from vinculo_cliente where usuario_id=?", Long.class, id)).isZero();
        assertThat(huella("b")).isEqualTo(antesB);
    }
}
