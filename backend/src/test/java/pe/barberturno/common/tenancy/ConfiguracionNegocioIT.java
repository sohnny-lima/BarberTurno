package pe.barberturno.common.tenancy;

import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import pe.barberturno.common.config.ReglasNegocioService;
import pe.barberturno.customers.*;
import pe.barberturno.platform.*;
import pe.barberturno.users.Usuario;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;

/** CF-5: HTTP, CSRF, dos negocios y rol real; V112 y cupo serializado con las altas. */
class ConfiguracionNegocioIT extends PlataformaPruebaBase {
    static final String RUTA = "/api/n/a/configuracion";
    @Autowired ClienteRepository clientes;
    @Autowired ReglasNegocioService reglas;

    ObjectNode validos() {
        return mapper.createObjectNode().put("nombre", "Nombre local")
                .put("anticipacionCambioClienteMin", 120).put("horizonteDias", 30)
                .put("limiteReservasActivas", 3).put("limiteBarberosActivos", 10)
                .put("confirmacionManual", false).put("toleranciaInicioMin", 15);
    }
    HttpResponse<String> guardar(ObjectNode datos) throws Exception {
        return http(titular, "PUT", RUTA, datos.toString(), token(titular));
    }
    JsonNode consultar(String slug) throws Exception {
        var r = http(titular, "GET", "/api/n/" + slug + "/configuracion", null, null);
        assertThat(r.statusCode()).isEqualTo(200); return mapper.readTree(r.body());
    }
    void codigo(HttpResponse<String> r, int estado, String codigo) {
        assertThat(r.statusCode()).isEqualTo(estado);
        assertThat(mapper.readTree(r.body()).get("codigo").asString()).isEqualTo(codigo);
    }
    long perfil(Usuario persona) throws Exception {
        var r = http(titular, "POST", "/api/n/a/barberos", "{\"usuarioId\":" + persona.getId() + ",\"especialidad\":\"Corte\"}", token(titular));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201); return mapper.readTree(r.body()).get("id").asLong();
    }

    @Test void soloPropietario_yEscrituraConCsrfValido_sinEfectosDeOtrosRoles() throws Exception {
        var cliente = cuenta("cliente"); en("a", () -> clientes.saveAndFlush(new Cliente(cliente, reloj.instant())));
        var barbero = cuenta("barbero");
        membresias.save(new Membresia(a, barbero.getId(), RolNegocio.BARBERO, reloj.instant()));
        var antes = jdbc.queryForMap("select * from negocio where id=?", a);
        for (var sesion : List.of(gestorLocal, login("barbero"), login("cliente"), admin)) {
            assertThat(http(sesion, "GET", RUTA, null, null).statusCode()).isEqualTo(403);
            assertThat(http(sesion, "PUT", RUTA, validos().toString(), token(sesion)).statusCode()).isEqualTo(403);
        }
        for (String csrf : Arrays.asList(null, "incorrecto")) {
            assertThat(http(titular, "PUT", RUTA, validos().toString(), csrf).statusCode()).isEqualTo(403);
        }
        assertThat(jdbc.queryForMap("select * from negocio where id=?", a)).isEqualTo(antes);
        assertThat(consultar("a").get("nombre").asString()).isEqualTo("Negocio ficticio");
    }

    @Test void guardarSoloCamposPermitidos_ignoraIdentidadEstadoZonaYRejilla_yBIntacto() throws Exception {
        var original = jdbc.queryForMap("select id,slug,estado,zona_horaria,creado_en from negocio where id=?", a);
        var bAntes = jdbc.queryForMap("select * from negocio where id=?", b); var huellaB = huella("b");
        var datos = validos().put("nombre", "Barbería ficticia A").put("anticipacionCambioClienteMin", 60)
                .put("horizonteDias", 7).put("limiteReservasActivas", 2).put("limiteBarberosActivos", 4)
                .put("confirmacionManual", true).put("toleranciaInicioMin", 30);
        var extras = datos.deepCopy().put("id", b).put("negocioId", b).put("slug", "b")
                .put("estado", "SUSPENDIDO").put("zonaHoraria", "UTC").put("rejillaMin", 20);
        assertThat(guardar(extras).statusCode()).isEqualTo(200);
        assertThat(consultar("a")).isEqualTo(datos);
        assertThat(jdbc.queryForMap("select id,slug,estado,zona_horaria,creado_en from negocio where id=?", a)).isEqualTo(original);
        assertThat(jdbc.queryForMap("select * from negocio where id=?", b)).isEqualTo(bAntes);
        assertThat(huella("b")).isEqualTo(huellaB);
        assertThat(en("a", reglas::reservas).rejillaMin()).isEqualTo(10);
        assertThat(consultar("b").get("horizonteDias").asInt()).isEqualTo(30);
    }

    @ParameterizedTest @CsvSource({
            "anticipacionCambioClienteMin,-1", "anticipacionCambioClienteMin,32768",
            "horizonteDias,0", "horizonteDias,32768", "limiteReservasActivas,0", "limiteReservasActivas,32768",
            "limiteBarberosActivos,0", "limiteBarberosActivos,32768", "toleranciaInicioMin,-1", "toleranciaInicioMin,32768"})
    void fueraDeRango_400Validacion_yFilaSinCambios(String campo, int valor) throws Exception {
        var antes = jdbc.queryForMap("select * from negocio where id=?", a);
        codigo(guardar(validos().put(campo, valor)), 400, "VALIDACION");
        assertThat(jdbc.queryForMap("select * from negocio where id=?", a)).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"nombre", "anticipacionCambioClienteMin", "horizonteDias",
            "limiteReservasActivas", "limiteBarberosActivos", "confirmacionManual", "toleranciaInicioMin"})
    void camposObligatorios_nulosOAusentes_400SinCambios(String campo) throws Exception {
        var antes = jdbc.queryForMap("select * from negocio where id=?", a);
        codigo(guardar(validos().putNull(campo)), 400, "VALIDACION");
        var omitido = validos(); omitido.remove(campo);
        codigo(guardar(omitido), 400, "VALIDACION");
        assertThat(jdbc.queryForMap("select * from negocio where id=?", a)).isEqualTo(antes);
    }

    @Test void nombreInvalido_400_yLimitesDeRangoPermitidos_seConservan() throws Exception {
        for (String nombre : List.of("", "   ", "x".repeat(101))) codigo(guardar(validos().put("nombre", nombre)), 400, "VALIDACION");
        var minimos = validos().put("anticipacionCambioClienteMin", 0).put("horizonteDias", 1)
                .put("limiteReservasActivas", 1).put("limiteBarberosActivos", 1).put("toleranciaInicioMin", 0);
        assertThat(guardar(minimos).statusCode()).isEqualTo(200); assertThat(consultar("a")).isEqualTo(minimos);
        var maximos = validos().put("nombre", "x".repeat(100)).put("anticipacionCambioClienteMin", 32767)
                .put("horizonteDias", 32767).put("limiteReservasActivas", 32767)
                .put("limiteBarberosActivos", 32767).put("toleranciaInicioMin", 32767);
        assertThat(guardar(maximos).statusCode()).isEqualTo(200); assertThat(consultar("a")).isEqualTo(maximos);
    }

    @Test void reducirCupoPorDebajoDeActivos_409_yElCupoNuevoRigeEnLaSiguienteAlta() throws Exception {
        perfil(propietario); perfil(encargado);
        assertThat(guardar(validos().put("limiteBarberosActivos", 2)).statusCode()).isEqualTo(200);
        var antes = jdbc.queryForMap("select * from negocio where id=?", a);
        codigo(guardar(validos().put("limiteBarberosActivos", 1)), 409, "CONFLICTO");
        assertThat(jdbc.queryForMap("select * from negocio where id=?", a)).isEqualTo(antes);
        var tercero = cuenta("tercero"); membresias.save(new Membresia(a, tercero.getId(), RolNegocio.BARBERO, reloj.instant()));
        codigo(http(titular, "POST", "/api/n/a/barberos", "{\"usuarioId\":" + tercero.getId() + ",\"especialidad\":\"Corte\"}", token(titular)), 422, "LIMITE_BARBEROS_ACTIVOS");
        assertThat(en("a", () -> jdbc.queryForObject("select count(*) from barbero where activo", Long.class))).isEqualTo(2);
    }

    @Test void suspension_soloLectura_409AlEscribir_yNegocioInexistente404() throws Exception {
        estado(a, "SUSPENDIDO"); var antes = jdbc.queryForMap("select * from negocio where id=?", a);
        assertThat(consultar("a").get("nombre").asString()).isEqualTo("Negocio ficticio");
        codigo(guardar(validos()), 409, "NEGOCIO_SUSPENDIDO");
        assertThat(jdbc.queryForMap("select * from negocio where id=?", a)).isEqualTo(antes);
        assertThat(http(titular, "PUT", "/api/n/b/configuracion", validos().toString(), token(titular)).statusCode()).isEqualTo(200);
        for (String metodo : List.of("GET", "PUT")) {
            assertThat(http(titular, metodo, "/api/n/inexistente/configuracion", metodo.equals("PUT") ? validos().toString() : null, token(titular)).statusCode()).isEqualTo(404);
        }
    }

    @Test void rolReal_noPuedeModificarSlugIdZonaNiCreacion_porSql42501() {
        ConfiguracionNegocioEsquemaPruebas.comprobarPermisos(jdbc);
        assertThat(jdbc.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user", Boolean.class)).isFalse();
        var antes = jdbc.queryForMap("select * from negocio where id=?", a);
        for (String columna : List.of("id", "slug", "zona_horaria", "creado_en")) {
            assertThatThrownBy(() -> en("a", () -> jdbc.update("update negocio set " + columna + "="
                    + (columna.equals("id") ? "DEFAULT" : columna) + " where id=?", a)))
                    .isInstanceOfSatisfying(DataAccessException.class,
                            e -> assertThat(((org.postgresql.util.PSQLException) e.getMostSpecificCause()).getSQLState()).isEqualTo("42501"));
        }
        assertThat(jdbc.queryForMap("select * from negocio where id=?", a)).isEqualTo(antes);
    }

    DatosNegocioPrueba escenario(String slug, Usuario cliente) {
        return en(slug, () -> {
            var d = DatosNegocioPrueba.crear(jdbc, "config-" + slug);
            jdbc.update("delete from notificacion"); jdbc.update("delete from auditoria_reserva"); jdbc.update("delete from reserva");
            jdbc.update("delete from jornada");
            for (int dia=1; dia<=7; dia++) jdbc.update("insert into jornada(barbero_id,dia_semana,hora_inicio,hora_fin) values (?,?,'09:00','18:00')", d.barbero(), dia);
            clientes.saveAndFlush(new Cliente(cliente, reloj.instant()));
            return d;
        });
    }
    HttpResponse<String> reservar(Sesion sesion, String slug, DatosNegocioPrueba datos, String inicio) throws Exception {
        return http(sesion, "POST", "/api/n/" + slug + "/reservas", "{\"servicioId\":" + datos.servicio()
                + ",\"barberoId\":" + datos.barbero() + ",\"inicio\":\"" + inicio + "\"}", token(sesion));
    }

    @Test void parametrosSeAplicanEnSiguientePeticion_horizonteConfirmacionYMaximo_sinAfectarB() throws Exception {
        var cliente = cuenta("cliente"); var da = escenario("a", cliente); var db = escenario("b", cliente); var sesion = login("cliente");
        assertThat(guardar(validos().put("horizonteDias", 1).put("limiteReservasActivas", 1).put("confirmacionManual", true)).statusCode()).isEqualTo(200);
        for (String slug : List.of("a", "b")) {
            var d = slug.equals("a") ? da : db;
            var r = http(sesion, "GET", "/api/n/" + slug + "/disponibilidad?servicioId=" + d.servicio()
                    + "&barberoId=" + d.barbero() + "&fecha=2026-10-01", null, null);
            assertThat(r.statusCode()).isEqualTo(200);
            if (slug.equals("a")) assertThat(mapper.readTree(r.body()).get("franjas")).isEmpty();
            else assertThat(mapper.readTree(r.body()).get("franjas")).isNotEmpty();
        }
        codigo(reservar(sesion, "a", da, "2026-10-01T11:00:00-05:00"), 422, "FUERA_DE_HORIZONTE");
        var primera = reservar(sesion, "a", da, "2026-09-28T11:00:00-05:00");
        assertThat(primera.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(primera.body()).get("estado").asString()).isEqualTo("PENDIENTE");
        codigo(reservar(sesion, "a", da, "2026-09-28T12:00:00-05:00"), 422, "LIMITE_RESERVAS_ACTIVAS");
        for (String hora : List.of("11:00", "12:00")) {
            var r = reservar(sesion, "b", db, "2026-09-28T" + hora + ":00-05:00");
            assertThat(r.statusCode()).isEqualTo(201);
            assertThat(mapper.readTree(r.body()).get("estado").asString()).isEqualTo("CONFIRMADA");
        }
    }

    @Test void anticipacionYTolerancia_actualizanPermisosEnSiguienteDetalle_sinCambiarB() throws Exception {
        var cliente = cuenta("cliente"); var da = escenario("a", cliente); var db = escenario("b", cliente); var sesion = login("cliente");
        var ids = new LinkedHashMap<String, Long>();
        for (String slug : List.of("a", "b")) {
            var r = reservar(sesion, slug, slug.equals("a") ? da : db, "2026-09-28T10:00:00-05:00");
            assertThat(r.statusCode()).isEqualTo(201); ids.put(slug, mapper.readTree(r.body()).get("id").asLong());
            assertThat(mapper.readTree(r.body()).get("permisos").get("cancelar").asBoolean()).isFalse();
        }
        assertThat(guardar(validos().put("anticipacionCambioClienteMin", 0).put("toleranciaInicioMin", 60)).statusCode()).isEqualTo(200);
        for (String slug : List.of("a", "b")) {
            String detalle = "/api/n/" + slug + "/reservas/" + ids.get(slug);
            var propia = http(sesion, "GET", detalle, null, null); assertThat(propia.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(propia.body()).get("permisos").get("cancelar").asBoolean()).isEqualTo(slug.equals("a"));
            var gestion = http(titular, "GET", detalle, null, null); assertThat(gestion.statusCode()).isEqualTo(200);
            var transiciones = mapper.readTree(gestion.body()).get("permisos").get("transiciones");
            var valores = new ArrayList<String>(); transiciones.forEach(n -> valores.add(n.asString()));
            assertThat(valores.contains("EN_ATENCION")).isEqualTo(slug.equals("a"));
        }
    }

    @RepeatedTest(20) void reducirCupo_contraAlta_sinSuperarLimiteNiInterbloqueo() throws Exception {
        perfil(propietario);
        assertThat(guardar(validos().put("limiteBarberosActivos", 2)).statusCode()).isEqualTo(200);
        var inicio = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var cambio = hilos.submit(() -> { inicio.await(10, TimeUnit.SECONDS); return guardar(validos().put("limiteBarberosActivos", 1)); });
            var alta = hilos.submit(() -> { inicio.await(10, TimeUnit.SECONDS);
                return http(gestorLocal, "POST", "/api/n/a/barberos", "{\"usuarioId\":" + encargado.getId() + ",\"especialidad\":\"Corte\"}", token(gestorLocal)); });
            var rCambio = cambio.get(25, TimeUnit.SECONDS); var rAlta = alta.get(25, TimeUnit.SECONDS);
            if (rCambio.statusCode() == 200) codigo(rAlta, 422, "LIMITE_BARBEROS_ACTIVOS");
            else { codigo(rCambio, 409, "CONFLICTO"); assertThat(rAlta.statusCode()).isEqualTo(201); }
        }
        long activos = en("a", () -> jdbc.queryForObject("select count(*) from barbero where activo", Long.class));
        assertThat(activos).isLessThanOrEqualTo(consultar("a").get("limiteBarberosActivos").asLong());
    }
}
