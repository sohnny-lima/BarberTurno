package pe.barberturno.common.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.platform.*;
import pe.barberturno.platform.dto.CrearNegocioDto;
import static org.assertj.core.api.Assertions.*;

/** CF-2/6/7: separación global, operaciones atómicas y ausencia de secretos. */
class PlataformaIT extends PlataformaPruebaBase {
    @Test void altaPendienteDeV2063_sinTokenEnlaceCorreoNiTablaDeInvitaciones() throws Exception {
        long antes = auditorias();
        long id = alta("nuevo");
        assertThat(jdbc.queryForMap("select slug,nombre,estado,zona_horaria from negocio where id=?", id))
                .containsEntry("slug", "nuevo").containsEntry("estado", "ACTIVO").containsEntry("zona_horaria", "America/Lima");
        assertThat(jdbc.queryForObject("select count(*) from membresia where negocio_id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("select to_regclass('public.invitacion') is null", Boolean.class)).isTrue();
        var lista = http(admin, "GET", "/api/plataforma/negocios", null, null);
        assertThat(lista.statusCode()).isEqualTo(200);
        var filas = mapper.readTree(lista.body());
        for (var fila : filas) {
            assertThat(fila.properties()).hasSize(5);
            assertThat(fila.has("enlaceInvitacion") || fila.has("token") || fila.has("correoPropietario")).isFalse();
            if (fila.get("id").asLong() == id) assertThat(fila.get("miembrosActivos").asLong()).isZero();
            if (fila.get("id").asLong() == a) assertThat(fila.get("miembrosActivos").asLong()).isEqualTo(2);
        }
        assertThat(auditorias()).isEqualTo(antes + 2);
        assertThat(jdbc.queryForMap("select actor_id,accion,negocio_id,objetivo_id,creado_en from auditoria_plataforma where negocio_id=?", id))
                .containsEntry("actor_id", plataforma.getId()).containsEntry("accion", "CREAR_NEGOCIO")
                .containsEntry("negocio_id", id).containsEntry("objetivo_id", null)
                .containsEntry("creado_en", java.sql.Timestamp.from(reloj.instant()));
    }

    @ParameterizedTest @ValueSource(strings = {"A", "a--b", "-a", "a-", "a_b", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void slugInvalido_rechazaSinAltaNiAuditoria(String slug) throws Exception {
        long antes = auditorias();
        assertThat(http(admin, "POST", "/api/plataforma/negocios", altaJson(slug), token(admin)).statusCode()).isEqualTo(400);
        assertThat(auditorias()).isEqualTo(antes);
    }

    @Test void slugDuplicado_409SinAuditoriaNiCambio() throws Exception {
        long antes = auditorias();
        var respuesta = http(admin, "POST", "/api/plataforma/negocios", altaJson("a"), token(admin));
        assertThat(respuesta.statusCode()).isEqualTo(409);
        assertThat(mapper.readTree(respuesta.body()).get("codigo").asString()).isEqualTo("SLUG_DUPLICADO");
        assertThat(auditorias()).isEqualTo(antes);
    }

    @Test void soloPlataforma_sinMembresiaNoAccedeAlNegocio_niPrefijoGlobalLocal() throws Exception {
        for (var sesion : List.of(titular, gestorLocal)) {
            assertThat(http(sesion, "GET", "/api/plataforma/negocios", null, null).statusCode()).isEqualTo(403);
        }
        assertThat(http(admin, "GET", "/api/n/a/usuarios", null, null).statusCode()).isEqualTo(403);
        assertThat(http(admin, "POST", "/api/n/a/servicios", "{}", token(admin)).statusCode()).isEqualTo(403);
        for (String prefijo : List.of("/api/n/a", "/api/n/b")) {
            assertThat(http(admin, "GET", prefijo + "/plataforma/negocios", null, null).statusCode()).isEqualTo(404);
            assertThat(http(admin, "POST", prefijo + "/plataforma/negocios", altaJson("no"), token(admin)).statusCode()).isEqualTo(404);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"alta", "estado", "password"})
    void cadaEscritura_sesionValidaSinCsrfOIncorrectoYRolesLocales_rechazaSinEfectos(String operacion) throws Exception {
        String ruta = switch (operacion) {
            case "alta" -> "/api/plataforma/negocios";
            case "estado" -> "/api/plataforma/negocios/" + a + "/estado";
            default -> "/api/plataforma/cuentas/" + propietario.getId() + "/restablecer-password";
        };
        String metodo = operacion.equals("estado") ? "PATCH" : "POST";
        String cuerpo = operacion.equals("alta") ? altaJson("prohibido") : "{\"estado\":\"SUSPENDIDO\"}";
        long antes = auditorias();
        for (String csrf : new String[]{null, "incorrecto"}) {
            assertThat(http(admin, metodo, ruta, cuerpo, csrf).statusCode()).isEqualTo(403);
        }
        cuenta("sin-rol");
        for (var sesion : List.of(titular, gestorLocal, login("sin-rol"))) {
            assertThat(http(sesion, metodo, ruta, cuerpo, token(sesion)).statusCode()).isEqualTo(403);
        }
        assertThat(usuarios.findById(propietario.getId()).orElseThrow().getTokenVersion()).isZero();
        assertThat(jdbc.queryForObject("select estado from negocio where id=?", String.class, a)).isEqualTo("ACTIVO");
        assertThat(jdbc.queryForObject("select count(*) from negocio where slug='prohibido'", Long.class)).isZero();
        assertThat(auditorias()).isEqualTo(antes);
    }

    @Test void restablecerCompartida_revocaSesionExigeCambioConservaPertenencias_yNoPermiteAutoReset() throws Exception {
        var aAntes = huella("a"); var bAntes = huella("b");
        var objetivo = usuarios.findById(propietario.getId()).orElseThrow();
        var respuesta = http(admin, "POST", "/api/plataforma/cuentas/" + objetivo.getId() + "/restablecer-password", null, token(admin));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control")).contains("no-store");
        var datos = mapper.readTree(respuesta.body());
        assertThat(datos.properties()).hasSize(1);
        String temporal = datos.get("passwordTemporal").asString();
        assertThat(temporal.length()).isEqualTo(12);
        assertThat(temporal.chars().anyMatch(Character::isLetter) && temporal.chars().anyMatch(Character::isDigit)).isTrue();
        var actual = usuarios.findById(objetivo.getId()).orElseThrow();
        assertThat(passwords.matches(temporal, actual.getPasswordHash())).isTrue();
        assertThat(actual.isDebeCambiarPassword()).isTrue();
        assertThat(actual.getTokenVersion()).isEqualTo(objetivo.getTokenVersion() + 1);
        assertThat(http(titular, "GET", "/api/perfil", null, null).statusCode()).isEqualTo(401);
        var recuperada = loginClave("propietario", temporal);
        assertThat(http(recuperada, "GET", "/api/n/a/usuarios", null, null).statusCode()).isEqualTo(403);
        assertThat(huella("a")).isEqualTo(aAntes); assertThat(huella("b")).isEqualTo(bAntes);
        assertThat(http(admin, "POST", "/api/plataforma/cuentas/" + plataforma.getId() + "/restablecer-password", null, token(admin)).statusCode()).isEqualTo(403);
        assertThat(http(admin, "GET", "/api/perfil", null, null).statusCode()).isEqualTo(200);
    }

    @Test void restablecerOtraPlataforma_acepta_ausentesYEstadoInvalidoNoAuditan() throws Exception {
        var otra = cuenta("otra-plataforma"); otra.habilitarAdministracionPlataforma(); usuarios.saveAndFlush(otra);
        assertThat(http(admin, "POST", "/api/plataforma/cuentas/" + otra.getId() + "/restablecer-password", null, token(admin)).statusCode()).isEqualTo(200);
        assertThat(usuarios.findById(otra.getId()).orElseThrow().getRolPlataforma()).isEqualTo(RolPlataforma.ADMIN_PLATAFORMA);
        long antes = auditorias();
        assertThat(http(admin, "POST", "/api/plataforma/cuentas/999999/restablecer-password", null, token(admin)).statusCode()).isEqualTo(404);
        assertThat(http(admin, "PATCH", "/api/plataforma/negocios/999999/estado", "{\"estado\":\"ACTIVO\"}", token(admin)).statusCode()).isEqualTo(404);
        for (String cuerpo : List.of("{}", "{\"estado\":null}", "{\"estado\":\"OTRO\"}")) {
            assertThat(http(admin, "PATCH", "/api/plataforma/negocios/" + a + "/estado", cuerpo, token(admin)).statusCode()).isEqualTo(400);
        }
        assertThat(auditorias()).isEqualTo(antes);
    }

    @Test void auditoriaSinSecretosNiDatosPersonales_enTablaYListAppender_yRollbackDeCadaOperacion() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            alta("auditoria"); estado(a, "SUSPENDIDO"); estado(a, "ACTIVO");
            assertThat(http(admin, "GET", "/api/plataforma/negocios", null, null).statusCode()).isEqualTo(200);
            var respuesta = http(admin, "POST", "/api/plataforma/cuentas/" + propietario.getId() + "/restablecer-password", null, token(admin));
            assertThat(respuesta.statusCode()).isEqualTo(200);
            String temporal = mapper.readTree(respuesta.body()).get("passwordTemporal").asString();
            var tabla = jdbc.queryForList("select * from auditoria_plataforma").toString();
            var logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (x,y) -> x + y);
            for (String secreto : List.of(temporal, CLAVE, "@ejemplo.test", "999000001", "Persona ficticia", "invitado")) {
                assertThat(tabla.contains(secreto) || logs.contains(secreto)).as("Sin credenciales ni datos personales").isFalse();
            }
            assertThat(jdbc.queryForList("select distinct accion from auditoria_plataforma", String.class))
                    .containsExactlyInAnyOrder("CREAR_NEGOCIO", "LISTAR_NEGOCIOS", "SUSPENDER_NEGOCIO", "REACTIVAR_NEGOCIO", "RESTABLECER_PASSWORD");
        } finally { logger.detachAppender(appender); appender.stop(); }
        var actor = new UsuarioAutenticado(plataforma.getId(), null, Optional.empty(), false,
                RolPlataforma.ADMIN_PLATAFORMA, Optional.empty());
        long antes = auditorias();
        var cuentaAntes = jdbc.queryForMap("select * from usuario where id=?", propietario.getId());
        var negocioAntes = jdbc.queryForMap("select * from negocio where id=?", a);
        new TransactionTemplate(gestor).executeWithoutResult(tx -> {
            plataformaService.crear(new CrearNegocioDto("Ficticio", "revertido", "invitado@ejemplo.test"), actor);
            plataformaService.cambiarEstado(a, EstadoNegocio.SUSPENDIDO, actor);
            plataformaService.restablecer(propietario.getId(), actor);
            plataformaService.listar(actor);
            assertThat(auditorias()).isEqualTo(antes + 4);
            tx.setRollbackOnly();
        });
        assertThat(auditorias()).isEqualTo(antes);
        assertThat(directorio.buscar("revertido")).isEmpty();
        assertThat(jdbc.queryForMap("select * from usuario where id=?", propietario.getId())).isEqualTo(cuentaAntes);
        assertThat(jdbc.queryForMap("select * from negocio where id=?", a)).isEqualTo(negocioAntes);
    }

    @RepeatedTest(20)
    void altasSimultaneasMismoSlug_exactamenteUna_yUnaAuditoria() throws Exception {
        var otraSesion = login("plataforma");
        long antes = auditorias();
        var barrera = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var resultados = new java.util.ArrayList<Future<Integer>>();
            for (var sesion : List.of(admin, otraSesion)) resultados.add(hilos.submit(() -> {
                barrera.await(10, TimeUnit.SECONDS);
                return http(sesion, "POST", "/api/plataforma/negocios", altaJson("carrera"), token(sesion)).statusCode();
            }));
            assertThat(List.of(resultados.get(0).get(30, TimeUnit.SECONDS), resultados.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from negocio where slug='carrera'", Long.class)).isEqualTo(1);
        assertThat(auditorias()).isEqualTo(antes + 1);
    }
}
