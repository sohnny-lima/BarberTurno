package pe.barberturno.common.tenancy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.platform.*;
import static org.assertj.core.api.Assertions.*;

/** CF-3: servidor real, dos negocios HTTP y rol/CSRF reales; sin mocks de autorización. */
class InvitacionIT extends PlataformaPruebaBase {
    record Emitida(long id, String enlace) { }

    Emitida invitar(Sesion sesion, String slug, String alias, String rol) throws Exception {
        var r = http(sesion, "POST", "/api/n/" + slug + "/invitaciones",
                "{\"correo\":\"" + alias + "@ejemplo.test\",\"rol\":\"" + rol + "\"}", token(sesion));
        assertThat(r.statusCode()).isEqualTo(201);
        assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
        var datos = mapper.readTree(r.body());
        return new Emitida(datos.get("invitacion").get("id").asLong(), datos.get("enlaceInvitacion").asString());
    }

    int aceptar(Sesion sesion, Emitida i) throws Exception {
        return http(sesion, "POST", i.enlace() + "/aceptacion", null, token(sesion)).statusCode();
    }

    @Test void pendienteCaducada_sePuedeRevocarYReemitir_sinReutilizarCapacidad() throws Exception {
        var i = invitar(titular, "a", "caducada", "BARBERO");
        reloj.adelantar(Duration.ofDays(7));
        titular = login("propietario");
        assertThat(http(titular, "DELETE", "/api/n/a/invitaciones/" + i.id(), null, token(titular)).statusCode()).isEqualTo(200);
        var nueva = invitar(titular, "a", "caducada", "BARBERO");
        assertThat(nueva.enlace()).isNotEqualTo(i.enlace());
        assertThat(http(anonima(), "GET", i.enlace(), null, null).statusCode()).isEqualTo(404);
        assertThat(http(anonima(), "GET", nueva.enlace(), null, null).statusCode()).isEqualTo(200);
    }

    @Test void enlaceUnico_hashDe32Bytes_caducaEnSieteDias_correoOculto_yNoSeRegistra() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            var emitida = invitar(titular, "a", "DESTINO", "BARBERO");
            String secreto = emitida.enlace().substring("/api/invitaciones/".length());
            assertThat(Base64.getUrlDecoder().decode(secreto)).hasSize(32);
            assertThat(secreto).matches("[A-Za-z0-9_-]{43}");
            var fila = jdbc.queryForMap("select * from invitacion where id=?", emitida.id());
            assertThat((byte[]) fila.get("token_hash")).containsExactly(MessageDigest.getInstance("SHA-256")
                    .digest(secreto.getBytes(StandardCharsets.US_ASCII)));
            assertThat(fila).containsEntry("correo", "destino@ejemplo.test")
                    .containsEntry("expira_en", java.sql.Timestamp.from(reloj.instant().plus(Duration.ofDays(7))));
            assertThat(fila.keySet()).containsExactlyInAnyOrder("id", "negocio_id", "correo", "rol", "token_hash",
                    "estado", "expira_en", "invitada_por", "aceptada_por", "creado_en", "actualizado_en");
            var publico = http(anonima(), "GET", emitida.enlace(), null, null);
            assertThat(publico.statusCode()).isEqualTo(200);
            var vista = mapper.readTree(publico.body());
            assertThat(vista.properties()).hasSize(3);
            assertThat(vista.get("negocio").asString()).isEqualTo("Negocio ficticio");
            assertThat(vista.get("rol").asString()).isEqualTo("BARBERO");
            assertThat(vista.get("correoEnmascarado").asString()).isEqualTo("d***@ejemplo.test");
            var lista = http(titular, "GET", "/api/n/a/invitaciones", null, null);
            assertThat(lista.statusCode()).isEqualTo(200);
            assertThat(lista.body()).doesNotContain(secreto, "token_hash", "enlaceInvitacion");
            // Un duplicado se traduce antes de provocar un error SQL que podría registrar el correo.
            assertThat(http(titular, "POST", "/api/n/a/invitaciones",
                    "{\"correo\":\"destino@ejemplo.test\",\"rol\":\"BARBERO\"}", token(titular)).statusCode()).isEqualTo(409);
            assertThat(appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
                    .noneMatch(m -> m.contains(secreto) || m.contains(emitida.enlace()) || m.contains("destino@ejemplo.test"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @ParameterizedTest @ValueSource(strings = {"distinto", "caducada", "revocada", "usada", "inexistente", "suspendido"})
    void aceptarInvalida_rechazoGenericoSinCrearNiModificarMembresias(String caso) throws Exception {
        var destino = cuenta("destino"); var sesion = login("destino");
        var i = invitar(titular, "a", "destino", "BARBERO");
        if (caso.equals("distinto")) sesion = gestorLocal;
        if (caso.equals("caducada")) {
            reloj.adelantar(Duration.ofDays(7)); sesion = login("destino");
        }
        if (caso.equals("revocada")) assertThat(http(titular, "DELETE", "/api/n/a/invitaciones/" + i.id(), null, token(titular)).statusCode()).isEqualTo(200);
        if (caso.equals("usada")) assertThat(aceptar(sesion, i)).isEqualTo(200);
        if (caso.equals("inexistente")) i = new Emitida(-1, "/api/invitaciones/" + "x".repeat(43));
        if (caso.equals("suspendido")) estado(a, "SUSPENDIDO");
        if (!caso.equals("distinto")) {
            assertThat(http(anonima(), "GET", i.enlace(), null, null).statusCode()).isEqualTo(caso.equals("suspendido") ? 409 : 404);
        }
        var antes = jdbc.queryForList("select * from membresia order by id");
        var r = http(sesion, "POST", i.enlace() + "/aceptacion", null, token(sesion));
        assertThat(r.statusCode()).isEqualTo(caso.equals("suspendido") ? 409 : 404);
        assertThat(mapper.readTree(r.body()).get("codigo").asString())
                .isEqualTo(caso.equals("suspendido") ? "NEGOCIO_SUSPENDIDO" : "NO_ENCONTRADO");
        if (!caso.equals("suspendido")) assertThat(mapper.readTree(r.body()).get("detail").asString()).isEqualTo("No se encontró la invitación.");
        assertThat(jdbc.queryForList("select * from membresia order by id")).isEqualTo(antes);
        if (!caso.equals("usada")) assertThat(membresias.findByNegocioIdAndUsuarioId(a, destino.getId())).isEmpty();
    }

    @Test void consultaInvalidaOConsumida_noRevelaDestinatario_yRutasGlobalesNoAceptanPrefijo() throws Exception {
        var persona = cuenta("destino"); var sesion = login("destino");
        var i = invitar(titular, "a", "destino", "BARBERO");
        for (String slug : List.of("a", "b")) {
            String local = "/api/n/" + slug + i.enlace().substring(4);
            assertThat(http(titular, "GET", local, null, null).statusCode()).isEqualTo(404);
            assertThat(http(sesion, "POST", local + "/aceptacion", "{}", token(sesion)).statusCode()).isEqualTo(404);
        }
        assertThat(http(anonima(), "GET", "/api/invitaciones/" + "x".repeat(43), null, null).statusCode()).isEqualTo(404);
        assertThat(aceptar(sesion, i)).isEqualTo(200);
        var r = http(anonima(), "GET", i.enlace(), null, null);
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).doesNotContain(persona.getCorreo());
    }

    @ParameterizedTest @ValueSource(strings = {"ENCARGADO", "PROPIETARIO"})
    void encargado_noInvitaNiRevocaRolesDeGestion(String rol) throws Exception {
        long antes = jdbc.queryForObject("select count(*) from invitacion", Long.class);
        assertThat(http(gestorLocal, "POST", "/api/n/a/invitaciones",
                "{\"correo\":\"otro@ejemplo.test\",\"rol\":\"" + rol + "\"}", token(gestorLocal)).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from invitacion", Long.class)).isEqualTo(antes);
        var i = invitar(titular, "a", "otro", rol);
        assertThat(http(gestorLocal, "DELETE", "/api/n/a/invitaciones/" + i.id(), null, token(gestorLocal)).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select estado from invitacion where id=?", String.class, i.id())).isEqualTo("PENDIENTE");
    }

    @Test void encargado_barberoPermitido_listadoYRevocacionAjenosSinCruce() throws Exception {
        var i = invitar(gestorLocal, "a", "barbero", "BARBERO");
        var otra = invitar(titular, "b", "barbero", "BARBERO");
        var lista = http(gestorLocal, "GET", "/api/n/a/invitaciones", null, null);
        assertThat(lista.statusCode()).isEqualTo(200);
        var ids = new ArrayList<Long>(); mapper.readTree(lista.body()).forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).contains(i.id()).doesNotContain(otra.id());
        assertThat(http(titular, "DELETE", "/api/n/a/invitaciones/" + otra.id(), null, token(titular)).statusCode()).isEqualTo(404);
        assertThat(http(gestorLocal, "DELETE", "/api/n/a/invitaciones/" + i.id(), null, token(gestorLocal)).statusCode()).isEqualTo(200);
        assertThat(http(anonima(), "GET", i.enlace(), null, null).statusCode()).isEqualTo(404);
        assertThat(http(anonima(), "GET", otra.enlace(), null, null).statusCode()).isEqualTo(200);
    }

    @Test void cadaEscritura_csrfAusenteOIncorrecto_yRolNoAutorizado_sinEfectos() throws Exception {
        var cuenta = cuenta("destino"); var persona = login("destino");
        var i = invitar(titular, "a", "destino", "BARBERO");
        var antes = jdbc.queryForList("select to_jsonb(i)::text from invitacion i order by id", String.class);
        for (String csrf : new String[]{null, "incorrecto"}) {
            assertThat(http(titular, "POST", "/api/n/a/invitaciones",
                    "{\"correo\":\"otra@ejemplo.test\",\"rol\":\"BARBERO\"}", csrf).statusCode()).isEqualTo(403);
            assertThat(http(titular, "DELETE", "/api/n/a/invitaciones/" + i.id(), null, csrf).statusCode()).isEqualTo(403);
            assertThat(http(persona, "POST", i.enlace() + "/aceptacion", null, csrf).statusCode()).isEqualTo(403);
        }
        for (var sinRol : List.of(persona, admin)) {
            assertThat(http(sinRol, "POST", "/api/n/a/invitaciones", "{}", token(sinRol)).statusCode()).isEqualTo(403);
            assertThat(http(sinRol, "DELETE", "/api/n/a/invitaciones/" + i.id(), null, token(sinRol)).statusCode()).isEqualTo(403);
            assertThat(http(sinRol, "GET", "/api/n/a/invitaciones", null, null).statusCode()).isEqualTo(403);
        }
        var publico = anonima(); http(publico, "GET", "/api/auth/sesion", null, null);
        assertThat(http(publico, "POST", i.enlace() + "/aceptacion", null, token(publico)).statusCode()).isEqualTo(401);
        // Cualquier identidad autenticada está autorizada a intentar aceptar; no existe un rol excluido.
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, cuenta.getId())).isEmpty();
        assertThat(jdbc.queryForList("select to_jsonb(i)::text from invitacion i order by id", String.class)).isEqualTo(antes);
    }

    @Test void aceptarIgnoraCamposExtra_preservaB_yYaMiembroActivoNoCambiaRol() throws Exception {
        var persona = cuenta("destino"); var sesion = login("destino");
        membresias.save(new Membresia(b, persona.getId(), RolNegocio.ENCARGADO, reloj.instant()));
        var antesB = huella("b");
        var i = invitar(titular, "a", "destino", "BARBERO");
        assertThat(http(sesion, "POST", i.enlace() + "/aceptacion",
                "{\"rol\":\"PROPIETARIO\",\"negocioId\":" + b + ",\"usuarioId\":" + propietario.getId()
                        + ",\"correo\":\"propietario@ejemplo.test\"}", token(sesion)).statusCode()).isEqualTo(200);
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow().getRol()).isEqualTo(RolNegocio.BARBERO);
        assertThat(huella("b")).isEqualTo(antesB);
        assertThat(jdbc.queryForObject("select aceptada_por from invitacion where id=?", Long.class, i.id())).isEqualTo(persona.getId());
        var otra = invitar(titular, "a", "destino", "PROPIETARIO");
        var r = http(sesion, "POST", otra.enlace() + "/aceptacion", null, token(sesion));
        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo("YA_ES_MIEMBRO");
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow().getRol()).isEqualTo(RolNegocio.BARBERO);
        assertThat(jdbc.queryForObject("select estado from invitacion where id=?", String.class, otra.id())).isEqualTo("PENDIENTE");
    }

    @ParameterizedTest @ValueSource(strings = {"DECISION_GESTOR", "SALIDA", "PLATAFORMA", "PERFIL_DESACTIVADO"})
    void invitacionNueva_restableceAccesoRevocado_conRolInvitado_yLimpiaMotivo(String motivo) throws Exception {
        var persona = cuenta("destino"); var sesion = login("destino");
        membresias.save(new Membresia(a, persona.getId(), RolNegocio.BARBERO, reloj.instant()));
        new TransactionTemplate(gestor).executeWithoutResult(tx ->
                membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow().revocar(MotivoRevocacion.valueOf(motivo), reloj.instant()));
        assertThat(http(sesion, "GET", "/api/n/a/usuarios", null, null).statusCode()).isEqualTo(403);
        var cuentaAntes = jdbc.queryForMap("select * from usuario where id=?", persona.getId());
        assertThat(aceptar(sesion, invitar(titular, "a", "destino", "ENCARGADO"))).isEqualTo(200);
        var m = membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow();
        assertThat(m.isActiva()).isTrue(); assertThat(m.getRol()).isEqualTo(RolNegocio.ENCARGADO);
        assertThat(m.getMotivoRevocacion()).isNull();
        assertThat(http(sesion, "GET", "/api/n/a/usuarios", null, null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForMap("select * from usuario where id=?", persona.getId())).isEqualTo(cuentaAntes);
    }

    @RepeatedTest(20)
    void dosAceptacionesSimultaneas_unaMembresia_yUnConsumo() throws Exception {
        var persona = cuenta("destino"); var uno = login("destino"); var dos = login("destino");
        var i = invitar(titular, "a", "destino", "BARBERO");
        var barrera = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var resultados = new ArrayList<Future<Integer>>();
            for (var sesion : List.of(uno, dos)) resultados.add(hilos.submit(() -> {
                barrera.await(10, TimeUnit.SECONDS); return aceptar(sesion, i);
            }));
            assertThat(List.of(resultados.get(0).get(30, TimeUnit.SECONDS), resultados.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 404);
        }
        assertThat(jdbc.queryForObject("select count(*) from membresia where negocio_id=? and usuario_id=?", Long.class, a, persona.getId())).isEqualTo(1);
        assertThat(jdbc.queryForMap("select estado,aceptada_por from invitacion where id=?", i.id()))
                .containsEntry("estado", "ACEPTADA").containsEntry("aceptada_por", persona.getId());
    }

    @Test void aceptarYCrearPerfil_suAgendaEnA_sinAccesoEnB_eIdentidadDelPerfilInmutable() throws Exception {
        var persona = cuenta("destino"); var sesion = login("destino");
        var sinMembresia = cuenta("ajeno");
        assertThat(vincular(sinMembresia.getId()).statusCode()).isEqualTo(404);
        var antesB = huella("b");
        assertThat(aceptar(sesion, invitar(titular, "a", "destino", "BARBERO"))).isEqualTo(200);
        assertThat(en("a", () -> jdbc.queryForObject("select count(*) from barbero where usuario_id=?", Long.class, persona.getId()))).isZero();
        var respuesta = vincular(persona.getId()); assertThat(respuesta.statusCode()).isEqualTo(201);
        long perfil = mapper.readTree(respuesta.body()).get("id").asLong();
        assertThat(vincular(persona.getId()).statusCode()).isEqualTo(409);
        long reserva = en("a", () -> {
            var otros = DatosNegocioPrueba.crear(jdbc, "agenda-invitado");
            return DatosNegocioPrueba.reservar(jdbc, otros.ficha(), propietario.getId(), perfil, otros.servicio(), "CONFIRMADA", 60);
        });
        String agenda = "/reservas?desde=2026-10-01&hasta=2026-10-01";
        var agendaPropia = http(sesion, "GET", "/api/n/a" + agenda, null, null);
        assertThat(agendaPropia.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(agendaPropia.body()).get("contenido")).hasSize(1);
        assertThat(mapper.readTree(agendaPropia.body()).get("contenido").get(0).get("id").asLong()).isEqualTo(reserva);
        assertThat(http(sesion, "GET", "/api/n/b" + agenda, null, null).statusCode()).isEqualTo(403);
        var antes = en("a", () -> jdbc.queryForMap("select * from barbero where id=?", perfil));
        assertThat(http(titular, "PUT", "/api/n/a/barberos/" + perfil,
                "{\"nombre\":\"Profesional local\",\"telefono\":null,\"especialidad\":\"Corte\",\"usuarioId\":" + propietario.getId() + "}", token(titular)).statusCode()).isEqualTo(200);
        assertThat(en("a", () -> jdbc.queryForObject("select usuario_id from barbero where id=?", Long.class, perfil))).isEqualTo(persona.getId());
        assertThatThrownBy(() -> en("a", () -> jdbc.update("update barbero set usuario_id=? where id=?", propietario.getId(), perfil)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class).satisfies(e -> {
                    var causa = (org.postgresql.util.PSQLException) ((org.springframework.dao.DataAccessException)e).getMostSpecificCause();
                    assertThat(causa.getSQLState()).isEqualTo("23514");
                    assertThat(causa.getServerErrorMessage().getConstraint()).isEqualTo("barbero_usuario_inmutable");
                });
        assertThat(en("a", () -> jdbc.queryForObject("select usuario_id from barbero where id=?", Long.class, perfil))).isEqualTo(antes.get("usuario_id"));
        assertThat(huella("b")).isEqualTo(antesB);
    }

    java.net.http.HttpResponse<String> vincular(long usuarioId) throws Exception {
        return http(titular, "POST", "/api/n/a/barberos", "{\"usuarioId\":" + usuarioId + ",\"especialidad\":\"Corte\"}", token(titular));
    }
}
