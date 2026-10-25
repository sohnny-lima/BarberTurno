package pe.barberturno.common.tenancy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** CF-2: suspensión transversal, dos negocios simultáneos y compatibilidad del alias. */
class SuspensionNegocioIT extends PlataformaPruebaBase {
    @ParameterizedTest @ValueSource(strings = {"a", "barberturno"})
    void suspension_publicas404_personalLee_escrituras409_reactivacionYOtroNegocioIntacto(String slug) throws Exception {
        var datos = en(slug, () -> DatosNegocioPrueba.crear(jdbc, "suspendido"));
        var otros = en("b", () -> DatosNegocioPrueba.crear(jdbc, "activo"));
        String prefijo = slug.equals("barberturno") ? "/api" : "/api/n/a";
        var aAntes = huella(slug); var bAntes = huella("b");
        var cuentasAntes = jdbc.queryForList("select * from usuario order by id");
        long id = directorio.buscar(slug).orElseThrow();
        estado(id, "SUSPENDIDO");
        for (var sesion : List.of(anonima(), titular, gestorLocal, admin)) {
            for (String ruta : List.of("/servicios", "/barberos", "/disponibilidad?fecha=2026-10-01&servicioId=" + datos.servicio())) {
                var respuesta = http(sesion, "GET", prefijo + ruta, null, null);
                assertThat(respuesta.statusCode()).as("Pública suspendida %s", ruta).isEqualTo(404);
                assertThat(mapper.readTree(respuesta.body()).get("codigo").asString()).isEqualTo("NO_ENCONTRADO");
            }
        }
        for (String ruta : List.of("/registro", "/clientes/alta")) {
            assertThat(http(titular, "POST", prefijo + ruta, "{}", token(titular)).statusCode()).isEqualTo(404);
        }
        if (slug.equals("barberturno")) {
            assertThat(http(titular, "POST", "/api/auth/registro", "{}", token(titular)).statusCode()).isEqualTo(404);
        }
        for (var sesion : List.of(titular, gestorLocal)) {
            for (String ruta : List.of("/usuarios", "/reservas?desde=2026-10-01&hasta=2026-10-01",
                    "/reservas/" + datos.reserva(), "/reservas/" + datos.reserva() + "/auditoria",
                    "/reportes/resumen?desde=2026-10-01&hasta=2026-10-01", "/notificaciones",
                    "/notificaciones/conteo", "/barberos/" + datos.barbero() + "/jornadas",
                    "/barberos/" + datos.barbero() + "/bloqueos?desde=2026-10-01&hasta=2026-10-01",
                    "/clientes/" + datos.ficha())) {
                assertThat(http(sesion, "GET", prefijo + ruta, null, null).statusCode()).as("Lectura del personal %s", ruta).isEqualTo(200);
            }
        }
        // Cada familia de escrituras existente atraviesa la misma guarda; el dominio queda intacto.
        for (var escritura : escrituras(datos)) {
            var respuesta = http(titular, escritura.metodo(), prefijo + escritura.ruta(), escritura.cuerpo(), token(titular));
            assertThat(respuesta.statusCode()).as("Escritura suspendida %s %s", escritura.metodo(), escritura.ruta()).isEqualTo(409);
            assertThat(mapper.readTree(respuesta.body()).get("codigo").asString()).isEqualTo("NEGOCIO_SUSPENDIDO");
        }
        assertThat(huella(slug)).isEqualTo(aAntes); assertThat(huella("b")).isEqualTo(bAntes);
        assertThat(jdbc.queryForList("select * from usuario order by id")).isEqualTo(cuentasAntes);
        assertThat(http(titular, "GET", "/api/n/b/servicios", null, null).statusCode()).isEqualTo(200);
        assertThat(http(titular, "PATCH", "/api/n/b/clientes/" + otros.ficha(),
                "{\"nombre\":\"Cambio local B\",\"telefono\":\"999000002\"}", token(titular)).statusCode()).isEqualTo(200);
        assertThat(huella(slug)).isEqualTo(aAntes);
        var bDespues = huella("b");
        assertThat(en("b", () -> jdbc.queryForObject("select nombre from cliente where id=?", String.class, otros.ficha())))
                .isEqualTo("Cambio local B");
        // Solo plataforma puede reactivar; un permiso ADMIN local no basta.
        assertThat(http(titular, "PATCH", "/api/plataforma/negocios/" + id + "/estado",
                "{\"estado\":\"ACTIVO\"}", token(titular)).statusCode()).isEqualTo(403);
        estado(id, "ACTIVO");
        assertThat(http(anonima(), "GET", prefijo + "/servicios", null, null).statusCode()).isEqualTo(200);
        assertThat(http(titular, "PATCH", prefijo + "/clientes/" + datos.ficha(),
                "{\"nombre\":\"Cambio local A\",\"telefono\":\"999000003\"}", token(titular)).statusCode()).isEqualTo(200);
        assertThat(huella("b")).isEqualTo(bDespues);
    }

    @Test void clienteYBarbero_escriturasSuspendidas_noAfectanReservas_yRecursoCruzadoActivo404() throws Exception {
        var datos = en("a", () -> DatosNegocioPrueba.crear(jdbc, "a"));
        var otros = en("b", () -> DatosNegocioPrueba.crear(jdbc, "b"));
        var cliente = usuarios.findById(datos.cliente()).orElseThrow();
        var barbero = usuarios.findById(datos.admin()).orElseThrow();
        for (var cuenta : List.of(cliente, barbero)) {
            cuenta.cambiarPassword(passwords.encode(CLAVE), false, reloj.instant()); usuarios.saveAndFlush(cuenta);
        }
        membresias.save(new pe.barberturno.platform.Membresia(a, barbero.getId(), pe.barberturno.platform.RolNegocio.BARBERO, reloj.instant()));
        var clienteSesion = login("cliente-a"); var barberoSesion = login("admin-a");
        estado(a, "SUSPENDIDO");
        var huella = huella("a");
        for (var sesion : List.of(titular, clienteSesion)) {
            assertThat(http(sesion, "POST", "/api/n/a/reservas/" + datos.reserva() + "/cancelacion",
                    "{\"version\":0,\"motivo\":\"Prueba de suspensión\"}", token(sesion)).statusCode()).isEqualTo(409);
        }
        assertThat(http(barberoSesion, "GET", "/api/n/a/reservas?desde=2026-10-01&hasta=2026-10-01", null, null).statusCode()).isEqualTo(200);
        assertThat(http(barberoSesion, "POST", "/api/n/a/reservas/" + datos.reserva() + "/transiciones", "{}", token(barberoSesion)).statusCode()).isEqualTo(409);
        assertThat(huella("a")).isEqualTo(huella);
        assertThat(http(titular, "PATCH", "/api/n/b/clientes/" + datos.ficha(),
                "{\"nombre\":\"Ajeno\"}", token(titular)).statusCode()).isEqualTo(404);
        assertThat(http(titular, "GET", "/api/n/b/reservas/" + datos.reserva(), null, null).statusCode()).isEqualTo(404);
        assertThat(http(titular, "GET", "/api/n/b/reservas/" + otros.reserva(), null, null).statusCode()).isEqualTo(200);
    }

    private List<Escritura> escrituras(DatosNegocioPrueba d) {
        String servicio = "{\"nombre\":\"Cambio\",\"descripcion\":\"Ficticio\",\"duracionMin\":30,\"precio\":20}";
        return List.of(new Escritura("POST", "/servicios", servicio), new Escritura("PUT", "/servicios/" + d.servicio(), servicio),
                new Escritura("PATCH", "/servicios/" + d.servicio() + "/estado", "{\"activo\":false}"),
                new Escritura("POST", "/barberos", "{}"), new Escritura("PUT", "/barberos/" + d.barbero(), "{}"),
                new Escritura("PATCH", "/barberos/" + d.barbero() + "/estado", "{\"activo\":false}"),
                new Escritura("PUT", "/barberos/" + d.barbero() + "/jornadas", "[]"),
                new Escritura("POST", "/barberos/" + d.barbero() + "/bloqueos", "{}"),
                new Escritura("POST", "/bloqueos/lote", "{}"), new Escritura("DELETE", "/bloqueos/1", null),
                new Escritura("POST", "/reservas", "{}"),
                new Escritura("POST", "/reservas/" + d.reserva() + "/cancelacion", "{\"version\":0}"),
                new Escritura("POST", "/reservas/" + d.reserva() + "/reprogramacion", "{}"),
                new Escritura("POST", "/reservas/" + d.reserva() + "/transiciones", "{}"),
                new Escritura("POST", "/notificaciones/1/lectura", null), new Escritura("POST", "/notificaciones/lectura", null),
                new Escritura("PATCH", "/clientes/" + d.ficha(), "{\"nombre\":\"Cambio\"}"),
                new Escritura("POST", "/usuarios/" + d.cliente() + "/restablecer-password", null),
                new Escritura("PATCH", "/usuarios/" + d.cliente() + "/estado", "{\"activo\":false}"));
    }

    private record Escritura(String metodo, String ruta, String cuerpo) { }
}
