package pe.barberturno.common.tenancy;

import java.util.*;
import java.util.concurrent.*;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.*;
import pe.barberturno.platform.*;
import pe.barberturno.users.Usuario;
import static org.assertj.core.api.Assertions.*;

/** CF-4: HTTP real, dos negocios, sesiones y CSRF reales; cuatro carreras de veinte repeticiones. */
class EquipoIT extends PlataformaPruebaBase {
    @org.springframework.beans.factory.annotation.Autowired pe.barberturno.customers.ClienteRepository clientes;
    long miembro(long negocioId, Usuario cuenta, RolNegocio rol) {
        return membresias.save(new Membresia(negocioId, cuenta.getId(), rol, reloj.instant())).getId();
    }
    long miembro(long negocioId, Usuario cuenta) {
        return membresias.findByNegocioIdAndUsuarioId(negocioId, cuenta.getId()).orElseThrow().getId();
    }
    String ruta(long id, String accion) { return "/api/n/a/equipo/" + id + "/" + accion; }
    long perfil(Usuario cuenta, String slug) throws Exception {
        var r = http(titular, "POST", "/api/n/" + slug + "/barberos",
                "{\"usuarioId\":" + cuenta.getId() + ",\"especialidad\":\"Corte\"}", token(titular));
        assertThat(r.statusCode()).isEqualTo(201);
        return mapper.readTree(r.body()).get("id").asLong();
    }
    void codigo(HttpResponse<String> r, int estado, String codigo) {
        assertThat(r.statusCode()).isEqualTo(estado);
        assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo(codigo);
    }

    @Test void listar_soloGestores_delNegocio_conEstadoYMotivo_sinDatosGlobales() throws Exception {
        var persona = cuenta("profesional"); long local = miembro(a, persona, RolNegocio.BARBERO);
        long ajena = miembro(b, persona, RolNegocio.ENCARGADO);
        var sesion = login("profesional");
        assertThat(http(sesion, "GET", "/api/n/a/equipo", null, null).statusCode()).isEqualTo(403);
        assertThat(http(admin, "GET", "/api/n/a/equipo", null, null).statusCode()).isEqualTo(403);
        for (var gestor : List.of(titular, gestorLocal)) {
            var r = http(gestor, "GET", "/api/n/a/equipo", null, null);
            assertThat(r.statusCode()).isEqualTo(200);
            var lista = mapper.readTree(r.body());
            assertThat(lista).hasSize(3);
            for (var m : lista) {
                assertThat(m.get("id").asLong()).isNotEqualTo(ajena);
                assertThat(m.properties()).hasSize(5);
            }
        }
        assertThat(http(gestorLocal, "POST", ruta(local, "revocacion"), null, token(gestorLocal)).statusCode()).isEqualTo(200);
        var lista = mapper.readTree(http(titular, "GET", "/api/n/a/equipo", null, null).body());
        assertThat(lista.get(2).get("estado").asString()).isEqualTo("REVOCADA");
        assertThat(lista.get(2).get("motivoRevocacion").asString()).isEqualTo("DECISION_GESTOR");
        assertThat(http(sesion, "GET", "/api/n/a/reservas", null, null).statusCode()).isEqualTo(403);
    }

    @Test void cadaEscritura_csrfAusenteIncorrecto_yRolIndebido_sinEfectos() throws Exception {
        var cliente = cuenta("cliente"); var sesionCliente = login("cliente");
        var profesional = cuenta("profesional"); miembro(a, profesional, RolNegocio.BARBERO);
        var sesionProfesional = login("profesional");
        long id = miembro(a, encargado);
        var antes = huella("a");
        for (String accion : List.of("rol", "revocacion", "salida")) {
            String metodo = accion.equals("rol") ? "PATCH" : "POST";
            String ruta = accion.equals("salida") ? "/api/n/a/equipo/salida" : ruta(id, accion);
            String cuerpo = accion.equals("rol") ? "{\"rol\":\"BARBERO\"}" : null;
            for (String csrf : Arrays.asList(null, "incorrecto")) {
                assertThat(http(titular, metodo, ruta, cuerpo, csrf).statusCode()).isEqualTo(403);
            }
            for (var indebida : List.of(sesionCliente, admin)) {
                assertThat(http(indebida, metodo, ruta, cuerpo, token(indebida)).statusCode()).isEqualTo(403);
            }
            if (!accion.equals("salida")) assertThat(http(sesionProfesional, metodo, ruta, cuerpo, token(sesionProfesional)).statusCode()).isEqualTo(403);
        }
        assertThat(http(gestorLocal, "PATCH", ruta(id, "rol"), "{\"rol\":\"BARBERO\"}", token(gestorLocal)).statusCode()).isEqualTo(403);
        for (long objetivo : List.of(id, miembro(a, propietario))) {
            assertThat(http(gestorLocal, "POST", ruta(objetivo, "revocacion"), null, token(gestorLocal)).statusCode()).isEqualTo(403);
        }
        assertThat(huella("a")).isEqualTo(antes);
        assertThat(usuarios.findById(cliente.getId())).isPresent();
        assertThat(http(sesionProfesional, "POST", "/api/n/a/equipo/salida", null, token(sesionProfesional)).statusCode()).isEqualTo(200);
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, profesional.getId()).orElseThrow().getMotivoRevocacion()).isEqualTo(MotivoRevocacion.SALIDA);
    }

    @Test void recursosAjenos_404_ySuspension_409_sinEfectos() throws Exception {
        long ajena = miembro(b, encargado); var antes = huella("b");
        codigo(http(titular, "PATCH", ruta(ajena, "rol"), "{\"rol\":\"BARBERO\"}", token(titular)), 404, "NO_ENCONTRADO");
        codigo(http(titular, "POST", ruta(ajena, "revocacion"), null, token(titular)), 404, "NO_ENCONTRADO");
        estado(a, "SUSPENDIDO");
        assertThat(http(titular, "GET", "/api/n/a/equipo", null, null).statusCode()).isEqualTo(200);
        codigo(http(titular, "PATCH", ruta(miembro(a, encargado), "rol"), "{\"rol\":\"BARBERO\"}", token(titular)), 409, "NEGOCIO_SUSPENDIDO");
        codigo(http(titular, "POST", ruta(miembro(a, encargado), "revocacion"), null, token(titular)), 409, "NEGOCIO_SUSPENDIDO");
        codigo(http(titular, "POST", "/api/n/a/equipo/salida", null, token(titular)), 409, "NEGOCIO_SUSPENDIDO");
        assertThat(huella("b")).isEqualTo(antes);
    }

    @Test void ultimoPropietario_noLoSustituyenCuentasInactivasRevocadasNiOtrosNegocios() throws Exception {
        var inactiva = cuenta("inactiva"); miembro(a, inactiva, RolNegocio.PROPIETARIO);
        inactiva.desactivar(reloj.instant()); usuarios.saveAndFlush(inactiva);
        var revocada = cuenta("revocada"); miembro(a, revocada, RolNegocio.PROPIETARIO);
        var m = membresias.findByNegocioIdAndUsuarioId(a, revocada.getId()).orElseThrow();
        m.revocar(MotivoRevocacion.SALIDA, reloj.instant()); membresias.save(m);
        long id = miembro(a, propietario);
        codigo(http(titular, "PATCH", ruta(id, "rol"), "{\"rol\":\"ENCARGADO\"}", token(titular)), 409, "ULTIMO_PROPIETARIO");
        codigo(http(titular, "POST", ruta(id, "revocacion"), null, token(titular)), 409, "ULTIMO_PROPIETARIO");
        codigo(http(titular, "POST", "/api/n/a/equipo/salida", null, token(titular)), 409, "ULTIMO_PROPIETARIO");
        assertThat(membresias.contarPropietariosActivos(a)).isEqualTo(1);
    }

    @Test void revocacionConPerfil_conservaCuentaHistorialYNegocioB_reactivarNoDevuelveAcceso() throws Exception {
        var persona = cuenta("profesional"); long id = miembro(a, persona, RolNegocio.BARBERO);
        en("a", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(persona, reloj.instant())));
        miembro(b, persona, RolNegocio.BARBERO);
        long perfil = perfil(persona, "a"); perfil(persona, "b"); var sesion = login("profesional");
        var cuentaAntes = jdbc.queryForMap("select * from usuario where id=?", persona.getId());
        var bAntes = huella("b");
        assertThat(http(titular, "POST", ruta(id, "revocacion"), null, token(titular)).statusCode()).isEqualTo(200);
        assertThat(en("a", () -> jdbc.queryForObject("select activo from barbero where id=?", Boolean.class, perfil))).isFalse();
        assertThat(http(titular, "PATCH", "/api/n/a/barberos/" + perfil + "/estado", "{\"activo\":true}", token(titular)).statusCode()).isEqualTo(200);
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow().getMotivoRevocacion()).isEqualTo(MotivoRevocacion.DECISION_GESTOR);
        assertThat(http(sesion, "GET", "/api/n/a/reservas", null, null).statusCode()).isEqualTo(403);
        assertThat(http(sesion, "GET", "/api/n/b/reservas?desde=2026-10-01&hasta=2026-10-01", null, null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForMap("select * from usuario where id=?", persona.getId())).isEqualTo(cuentaAntes);
        assertThat(huella("b")).isEqualTo(bAntes);
    }

    @Test void cambiarRol_ySalida_conservanCuentaYOtrosNegocios_yDesactivanPerfil() throws Exception {
        var persona = cuenta("profesional"); long id = miembro(a, persona, RolNegocio.BARBERO);
        miembro(b, persona, RolNegocio.PROPIETARIO); long perfil = perfil(persona, "a");
        var sesion = login("profesional"); var bAntes = huella("b");
        var cuentaAntes = jdbc.queryForMap("select * from usuario where id=?", persona.getId());
        assertThat(http(titular, "PATCH", ruta(id, "rol"), "{\"rol\":\"PROPIETARIO\"}", token(titular)).statusCode()).isEqualTo(200);
        assertThat(en("a", () -> jdbc.queryForObject("select activo from barbero where id=?", Boolean.class, perfil))).isFalse();
        assertThat(http(sesion, "GET", "/api/n/a/equipo", null, null).statusCode()).isEqualTo(200);
        assertThat(http(sesion, "POST", "/api/n/a/equipo/salida", "{\"usuarioId\":" + propietario.getId() + "}", token(sesion)).statusCode()).isEqualTo(200);
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow().getMotivoRevocacion()).isEqualTo(MotivoRevocacion.SALIDA);
        assertThat(jdbc.queryForMap("select * from usuario where id=?", persona.getId())).isEqualTo(cuentaAntes);
        assertThat(huella("b")).isEqualTo(bAntes);
    }

    @Test void invitacionInicial_reenvioRevocacion_auditoriaAtomica_yOtrosRolesRechazados() throws Exception {
        String ruta = "/api/plataforma/negocios/" + a + "/invitacion-propietario";
        var antes = jdbc.queryForList("select * from invitacion where negocio_id=? order by id", b);
        for (String metodo : List.of("POST", "DELETE")) {
            for (String csrf : Arrays.asList(null, "incorrecto")) assertThat(http(admin, metodo, ruta, null, csrf).statusCode()).isEqualTo(403);
            for (var gestor : List.of(titular, gestorLocal)) assertThat(http(gestor, metodo, ruta, null, token(gestor)).statusCode()).isEqualTo(403);
        }
        String enlaceAnterior = null;
        for (int i = 0; i < 2; i++) {
            var r = http(admin, "POST", ruta, null, token(admin));
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
            if (enlaceAnterior != null) assertThat(http(anonima(), "GET", enlaceAnterior, null, null).statusCode()).isEqualTo(404);
            enlaceAnterior = mapper.readTree(r.body()).get("enlaceInvitacion").asString();
            assertThat(http(anonima(), "GET", enlaceAnterior, null, null).statusCode()).isEqualTo(200);
        }
        assertThat(http(admin, "DELETE", ruta, null, token(admin)).statusCode()).isEqualTo(200);
        assertThat(http(anonima(), "GET", enlaceAnterior, null, null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select count(*) from auditoria_plataforma where negocio_id=? and accion='REENVIAR_INVITACION_PROPIETARIO' and objetivo_id is null and actor_id=?", Long.class, a, plataforma.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from auditoria_plataforma where negocio_id=? and accion='REVOCAR_INVITACION_PROPIETARIO' and objetivo_id is null", Long.class, a)).isEqualTo(1);
        assertThat(jdbc.queryForList("select * from invitacion where negocio_id=? order by id", b)).usingRecursiveComparison().isEqualTo(antes);
        for (String metodo : List.of("POST", "DELETE")) assertThat(http(admin, metodo,
                "/api/plataforma/negocios/2147483646/invitacion-propietario", null, token(admin)).statusCode()).isEqualTo(404);
    }

    @Test void invitacionInicial_rollbackRevierteCapacidadYAuditoria_yNoRetiraOtrasInvitaciones() throws Exception {
        var r = http(titular, "POST", "/api/n/a/invitaciones",
                "{\"correo\":\"otro@ejemplo.test\",\"rol\":\"PROPIETARIO\"}", token(titular));
        assertThat(r.statusCode()).isEqualTo(201);
        String enlaceOtro = mapper.readTree(r.body()).get("enlaceInvitacion").asString();
        var antes = jdbc.queryForList("select * from invitacion where negocio_id=? order by id", a);
        long auditoriasAntes = auditorias();
        var actor = new pe.barberturno.common.security.UsuarioAutenticado(plataforma.getId(), null,
                Optional.empty(), false, RolPlataforma.ADMIN_PLATAFORMA, Optional.empty());
        new org.springframework.transaction.support.TransactionTemplate(gestor).executeWithoutResult(tx -> {
            plataformaService.reenviarInicial(a, actor);
            plataformaService.revocarInicial(a, actor);
            assertThat(auditorias()).isEqualTo(auditoriasAntes + 2);
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForList("select * from invitacion where negocio_id=? order by id", a)).usingRecursiveComparison().isEqualTo(antes);
        assertThat(auditorias()).isEqualTo(auditoriasAntes);
        assertThat(http(admin, "POST", "/api/plataforma/negocios/" + a + "/invitacion-propietario", null, token(admin)).statusCode()).isEqualTo(200);
        assertThat(http(anonima(), "GET", enlaceOtro, null, null).statusCode()).isEqualTo(200);
    }

    @RepeatedTest(20) void dosPropietarios_revocacionCruzada_quedaUno() throws Exception { carreraPropietarios("revocacion"); }
    @RepeatedTest(20) void dosPropietarios_salidaSimultanea_quedaUno() throws Exception { carreraPropietarios("salida"); }
    @RepeatedTest(20) void cambioDeRolPropio_contraSalida_quedaUno() throws Exception { carreraPropietarios("rol"); }

    void carreraPropietarios(String accion) throws Exception {
        var segundo = cuenta("segundo"); long idSegundo = miembro(a, segundo, RolNegocio.PROPIETARIO);
        var sesion = login("segundo"); long primero = miembro(a, propietario);
        var inicio = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var uno = hilos.submit(() -> {
                inicio.await(10, TimeUnit.SECONDS);
                return http(titular, accion.equals("rol") ? "PATCH" : "POST",
                        accion.equals("salida") ? "/api/n/a/equipo/salida" : ruta(accion.equals("rol") ? primero : idSegundo, accion),
                        accion.equals("rol") ? "{\"rol\":\"BARBERO\"}" : null, token(titular));
            });
            var dos = hilos.submit(() -> {
                inicio.await(10, TimeUnit.SECONDS);
                return http(sesion, "POST", accion.equals("revocacion") ? ruta(primero, accion) : "/api/n/a/equipo/salida", null, token(sesion));
            });
            var resultados = List.of(uno.get(25, TimeUnit.SECONDS), dos.get(25, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(r -> r.statusCode() == 200)).hasSize(1);
            assertThat(resultados).allSatisfy(r -> {
                assertThat(r.statusCode()).isIn(200, 403, 409);
                if (r.statusCode() == 409) codigo(r, 409, "ULTIMO_PROPIETARIO");
            });
        }
        assertThat(membresias.contarPropietariosActivos(a)).isEqualTo(1);
    }

    @RepeatedTest(20) void revocacionConDosBloqueos_contraDesactivacionPerfil_sinInterbloqueo() throws Exception {
        var persona = cuenta("profesional"); long id = miembro(a, persona, RolNegocio.BARBERO);
        en("a", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(persona, reloj.instant())));
        long perfil = perfil(persona, "a"); var inicio = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var uno = hilos.submit(() -> { inicio.await(10, TimeUnit.SECONDS);
                return http(titular, "POST", ruta(id, "revocacion"), null, token(titular)); });
            var dos = hilos.submit(() -> { inicio.await(10, TimeUnit.SECONDS);
                return http(gestorLocal, "PATCH", "/api/n/a/barberos/" + perfil + "/estado", "{\"activo\":false}", token(gestorLocal)); });
            assertThat(uno.get(25, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(dos.get(25, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(en("a", () -> jdbc.queryForObject("select activo from barbero where id=?", Boolean.class, perfil))).isFalse();
        assertThat(membresias.findByNegocioIdAndUsuarioId(a, persona.getId()).orElseThrow().getMotivoRevocacion()).isEqualTo(MotivoRevocacion.DECISION_GESTOR);
    }
}
