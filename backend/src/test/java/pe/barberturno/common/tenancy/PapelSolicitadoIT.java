package pe.barberturno.common.tenancy;

import java.net.URI;
import java.net.http.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import pe.barberturno.customers.*;
import pe.barberturno.platform.*;
import static org.assertj.core.api.Assertions.*;

/** V2-DA-42: HTTP real, JWT/CSRF reales y un único enlace de PostgreSQL reutilizado entre peticiones. */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class PapelSolicitadoIT extends PlataformaPruebaBase {
    @Autowired ClienteRepository clientes;
    @Autowired javax.sql.DataSource fuente;

    HttpResponse<String> pedir(Sesion sesion, String metodo, String ruta, String papel, String csrf) throws Exception {
        var p = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta));
        if (papel != null) p.header("X-BT-Papel", papel);
        if (csrf != null) p.header("X-XSRF-TOKEN", csrf);
        return sesion.cliente().send(p.method(metodo, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    void rol(Sesion sesion, String slug, String papel, String esperado) throws Exception {
        var r = pedir(sesion, "GET", "/api/n/" + slug + "/sesion", papel, null);
        assertThat(r.statusCode()).isEqualTo(200);
        var dto = mapper.readTree(r.body());
        if (esperado == null) assertThat(dto.get("rol").isNull()).isTrue();
        else assertThat(dto.get("rol").asString()).isEqualTo(esperado);
        if (papel != null) {
            assertThat(dto.get("barberoId").isNull()).isTrue();
            assertThat(dto.get("rolNegocio").isNull()).isTrue();
        }
    }

    @Test void rolesDePresentacion_sesionGlobalYNegociosAyB_sinElevarPapelCliente() throws Exception {
        var profesional = cuenta("profesional");
        var cliente = cuenta("cliente");
        membresias.save(new Membresia(a, profesional.getId(), RolNegocio.BARBERO, reloj.instant()));
        membresias.save(new Membresia(b, profesional.getId(), RolNegocio.ENCARGADO, reloj.instant()));
        for (String slug : List.of("a", "b")) {
            en(slug, () -> clientes.saveAndFlush(new Cliente(cliente, reloj.instant())));
            en(slug, () -> clientes.saveAndFlush(new Cliente(propietario, reloj.instant())));
        }
        var personal = login("profesional"); var consumidor = login("cliente");
        var actores = List.of(titular, gestorLocal, personal, consumidor, admin);
        for (int i = 0; i < actores.size(); i++) {
            var actor = actores.get(i);
            var global = http(actor, "GET", "/api/auth/sesion", null, null);
            assertThat(global.statusCode()).isEqualTo(200);
            var identidad = mapper.readTree(global.body());
            assertThat(identidad.get("rolNegocio").isNull()).isTrue();
            if (i == 4) assertThat(identidad.get("rolPlataforma").asString()).isEqualTo("ADMIN_PLATAFORMA");
            else assertThat(identidad.get("rolPlataforma").isNull()).isTrue();
            for (String slug : List.of("a", "b")) {
                var local = http(actor, "GET", "/api/n/" + slug + "/sesion", null, null);
                assertThat(local.statusCode()).isEqualTo(200);
                var dto = mapper.readTree(local.body());
                String esperado = switch (i) {
                    case 0 -> "PROPIETARIO";
                    case 1 -> "ENCARGADO";
                    case 2 -> slug.equals("a") ? "BARBERO" : "ENCARGADO";
                    default -> null;
                };
                if (esperado == null) assertThat(dto.get("rolNegocio").isNull()).isTrue();
                else assertThat(dto.get("rolNegocio").asString()).isEqualTo(esperado);
                assertThat(dto.get("rolPlataforma")).isEqualTo(identidad.get("rolPlataforma"));
                var reducida = pedir(actor, "GET", "/api/n/" + slug + "/sesion", "CLIENTE", null);
                assertThat(reducida.statusCode()).isEqualTo(200);
                var vista = mapper.readTree(reducida.body());
                assertThat(vista.get("rolNegocio").isNull()).isTrue();
                assertThat(vista.get("barberoId").isNull()).isTrue();
                assertThat(vista.get("rolPlataforma")).isEqualTo(identidad.get("rolPlataforma"));
            }
        }
    }

    @Test void reducePersonal_yLimpiaElContextoEntrePeticionesAyB_enLaMismaConexion() throws Exception {
        en("a", () -> clientes.saveAndFlush(new Cliente(propietario, reloj.instant())));
        // Un perfil activo demuestra que la reducción también elimina la asignación profesional.
        var perfil = http(titular, "POST", "/api/n/a/barberos",
                "{\"usuarioId\":" + propietario.getId() + ",\"especialidad\":\"Corte\"}", token(titular));
        assertThat(perfil.statusCode()).isEqualTo(201);
        int conexion = new org.springframework.transaction.support.TransactionTemplate(gestor)
                .execute(tx -> jdbc.queryForObject("select pg_backend_pid()", Integer.class));
        for (int i = 0; i < 2; i++) {
            rol(titular, "a", null, "ADMIN");
            rol(titular, "a", "CLIENTE", "CLIENTE");
            assertThat(pedir(titular, "GET", "/api/n/a/equipo", "CLIENTE", null).statusCode()).isEqualTo(403);
            assertThat(pedir(titular, "GET", "/api/n/a/reservas/mias", "CLIENTE", null).statusCode()).isEqualTo(200);
            rol(titular, "b", "CLIENTE", null);
            assertThat(pedir(titular, "GET", "/api/n/b/equipo", "CLIENTE", null).statusCode()).isEqualTo(403);
            assertThat(pedir(titular, "GET", "/api/n/b/servicios", "CLIENTE", null).statusCode()).isEqualTo(200);
            rol(titular, "b", null, "ADMIN");
            assertThat(pedir(titular, "GET", "/api/n/a/equipo", null, null).statusCode()).isEqualTo(200);
        }
        Integer conexionFinal = new org.springframework.transaction.support.TransactionTemplate(gestor)
                .execute(tx -> jdbc.queryForObject("select pg_backend_pid()", Integer.class));
        assertThat(conexionFinal).isEqualTo(conexion);
        assertThat(fuente.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean().getTotalConnections()).isOne();
        assertThat(contexto.clienteSolicitado()).isFalse();
    }

    @Test void sinFichaOConFichaInactiva_nuncaConcedeRol_yNoCambiaLoPublico() throws Exception {
        var ficha = en("a", () -> clientes.saveAndFlush(new Cliente(encargado, reloj.instant())));
        en("a", () -> { var c = clientes.findById(ficha.getId()).orElseThrow(); c.desactivar(reloj.instant()); return clientes.saveAndFlush(c); });
        for (var sesion : List.of(gestorLocal, admin)) {
            rol(sesion, "a", "CLIENTE", null);
            assertThat(pedir(sesion, "GET", "/api/n/a/reservas/mias", "CLIENTE", null).statusCode()).isEqualTo(403);
            assertThat(pedir(sesion, "GET", "/api/n/a/servicios", "CLIENTE", null).statusCode()).isEqualTo(200);
        }
        assertThat(pedir(anonima(), "GET", "/api/n/b/servicios", "CLIENTE", null).statusCode()).isEqualTo(200);
        rol(gestorLocal, "a", null, "ADMIN");
    }

    @Test void valorDesconocidoORutaGlobal_rechazaConValidacion_yLaSiguientePeticionConservaSuPapel() throws Exception {
        for (String papel : List.of("PROPIETARIO", "BARBERO", "ADMIN_PLATAFORMA", "cliente", "", "CLIENTE,CLIENTE")) {
            var r = pedir(titular, "GET", "/api/n/a/sesion", papel, null);
            assertThat(r.statusCode()).isEqualTo(400);
            assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo("VALIDACION");
            rol(titular, "a", null, "ADMIN");
        }
        for (String ruta : List.of("/api/auth/sesion", "/api/auth/registro", "/api/perfil", "/api/mis-negocios",
                "/api/plataforma/negocios", "/api/invitaciones/TokenFicticio", "/api/invitaciones/TokenFicticio/aceptacion",
                "/api/servicios")) {
            for (String metodo : List.of("GET", "POST")) {
                var r = pedir(titular, metodo, ruta, "CLIENTE", token(titular));
                assertThat(r.statusCode()).as(metodo + " " + ruta).isEqualTo(400);
                assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo("VALIDACION");
            }
        }
        rol(titular, "a", null, "ADMIN");
    }

    @Test void csrfAusenteOIncorrecto_sigueDando403ConSesionValida_yRolReducidoNoPuedeEscribirComoGestor() throws Exception {
        en("a", () -> clientes.saveAndFlush(new Cliente(propietario, reloj.instant())));
        var antes = huella("a");
        for (String csrf : java.util.Arrays.asList(null, "incorrecto")) {
            assertThat(pedir(titular, "POST", "/api/n/a/reservas", "CLIENTE", csrf).statusCode()).isEqualTo(403);
        }
        assertThat(pedir(titular, "POST", "/api/n/a/equipo/salida", "CLIENTE", token(titular)).statusCode()).isEqualTo(403);
        assertThat(huella("a")).isEqualTo(antes);
    }

    @Test void clienteConFicha_soloVeLoPropio_yRecursoDeOtroNegocioEs404() throws Exception {
        en("a", () -> clientes.saveAndFlush(new Cliente(propietario, reloj.instant())));
        en("b", () -> clientes.saveAndFlush(new Cliente(propietario, reloj.instant())));
        var datosA = en("a", () -> DatosNegocioPrueba.crear(jdbc, "papel-a"));
        var datosB = en("b", () -> DatosNegocioPrueba.crear(jdbc, "papel-b"));
        assertThat(pedir(titular, "GET", "/api/n/a/reservas/" + datosA.reserva(), null, null).statusCode()).isEqualTo(200);
        assertThat(pedir(titular, "GET", "/api/n/a/reservas/" + datosA.reserva(), "CLIENTE", null).statusCode()).isEqualTo(404);
        for (String papel : java.util.Arrays.asList(null, "CLIENTE")) {
            assertThat(pedir(titular, "GET", "/api/n/a/reservas/" + datosB.reserva(), papel, null).statusCode()).isEqualTo(404);
            assertThat(pedir(titular, "GET", "/api/n/b/reservas/" + datosA.reserva(), papel, null).statusCode()).isEqualTo(404);
        }
        // Una cuenta cliente sin membresía conserva CLIENTE con y sin cabecera: jamás obtiene ADMIN.
        var cliente = cuenta("solo-cliente");
        en("a", () -> clientes.saveAndFlush(new Cliente(cliente, reloj.instant())));
        var sesion = login("solo-cliente");
        rol(sesion, "a", null, "CLIENTE"); rol(sesion, "a", "CLIENTE", "CLIENTE");
        assertThat(pedir(sesion, "GET", "/api/n/a/equipo", "CLIENTE", null).statusCode()).isEqualTo(403);
    }
}
