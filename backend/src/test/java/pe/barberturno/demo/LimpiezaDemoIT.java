package pe.barberturno.demo;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import pe.barberturno.BarberTurnoApplication;
import pe.barberturno.auth.*;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.support.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Limpieza real con propietario NOBYPASSRLS, datos multinegocio y sesión HTTP de plataforma. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.tenancy.negocio-inicial-slug=", "BT_DEMO_PASSWORD=",
        "barberturno.seguridad.cookie-secure=false"})
@ActiveProfiles({"test", "demo"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LimpiezaDemoIT {
    static final String CLAVE = "ClaveDemoPrueba32";
    static final String ADMIN = "plataforma@ejemplo.test";
    @Autowired JdbcNegocioPruebas sql;
    @Autowired EjecutorEnNegocio ejecutor;
    @Autowired DatosDemoService demo;
    @Autowired DatosDemoMultinegocioService multi;
    @Autowired AuthService auth;
    @Autowired JsonMapper mapper;
    @LocalServerPort int puerto;
    String negocioOriginal;

    @BeforeEach void preparar() throws Exception {
        LimpiezaBaseDatos.limpiar(sql);
        negocioOriginal = sql.queryForObject("select row_to_json(n)::text from negocio n where id=1", String.class);
        ejecutor.ejecutar("barberturno", () -> { demo.cargar(CLAVE); return null; });
        new DatosDemoMultinegocioRunner(multi, ejecutor, "barberturno", CLAVE)
                .run(new DefaultApplicationArguments());
        try (var c = propietario()) {
            var j = jdbc(c);
            for (long id : j.queryForList("select id from negocio order by id", Long.class)) {
                contexto(c, id);
                completarTablas(j, id);
            }
        }
    }

    @AfterEach void limpiar() throws Exception {
        try (var c = propietario()) {
            jdbc(c).update("insert into negocio overriding system value select * from json_populate_record(null::negocio, ?::json) on conflict(id) do nothing", negocioOriginal);
        }
        LimpiezaBaseDatos.limpiar(sql);
    }

    @Test void ensayo_revierteCadaFilaIncluidaLaPreparacionParaRls() throws Exception {
        try (var c = propietario()) {
            var antes = huella(c);
            String avisos = ejecutar(c, "ensayo", "[\"barberturno\",\"sur\"]");
            assertThat(huella(c)).isEqualTo(antes);
            assertThat(avisos).contains("Ensayo terminado: ROLLBACK", "tabla=mensaje_whatsapp");
        }
    }

    @Test void aplicar_limpiaTodasLasTablas_conservaNegocioRealYAdminOperativo() throws Exception {
        try (var c = propietario()) {
            var j = jdbc(c);
            long real = negocioReal(c);
            var antesReal = huellaNegocio(c, real);
            List<Long> demoIds = j.queryForList("select id from negocio where slug in ('barberturno','sur')", Long.class);
            for (String tabla : tablas(c)) {
                long total = 0;
                for (long id : demoIds) {
                    contexto(c, id);
                    total += j.queryForObject("select count(*) from " + tabla + " where negocio_id=?", Long.class, id);
                }
                assertThat(total).as("La fixture debe cubrir la tabla %s", tabla).isPositive();
            }
            ejecutar(c, "aplicar", "[\"barberturno\",\"sur\"]");
            for (long id : demoIds) {
                contexto(c, id);
                for (String tabla : tablas(c)) {
                    assertThat(j.queryForObject("select count(*) from " + tabla + " where negocio_id=?", Long.class, id))
                            .as(tabla).isZero();
                }
            }
            assertThat(j.queryForList("select slug from negocio", String.class)).containsExactly("real-ficticio");
            assertThat(j.queryForList("select correo from usuario where correo like '%@ejemplo.test'", String.class)).containsExactly(ADMIN);
            assertThat(huellaNegocio(c, real)).isEqualTo(antesReal);
            assertThat(j.queryForObject("select nombre from usuario where correo=?", String.class, ADMIN)).isEqualTo("Administración");
            assertThat(j.queryForObject("select count(*) from membresia where usuario_id=(select id from usuario where correo=?)", Long.class, ADMIN)).isZero();
            comprobarPlataforma();
            var antesSegundo = huella(c);
            assertThatThrownBy(() -> ejecutar(c, "aplicar", "[\"barberturno\",\"sur\"]"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("no existe");
            assertThat(huella(c)).isEqualTo(antesSegundo);
        }
    }

    @Test void baseSinNegocios_arranqueGlobalIdempotente_yAltaConInvitacionPorHttp() throws Exception {
        try (var c = propietario()) {
            ejecutar(c, "aplicar", "[\"barberturno\",\"sur\"]");
            jdbc(c).update("delete from usuario");
            try (var arrancada = new SpringApplicationBuilder(BarberTurnoApplication.class).run(
                    "--spring.profiles.active=test", "--server.port=0", "--logging.level.root=WARN",
                    "--spring.datasource.hikari.maximum-pool-size=2", "--spring.datasource.hikari.minimum-idle=1",
                    "--barberturno.tenancy.negocio-inicial-slug=", "--barberturno.admin.correo=" + ADMIN,
                    "--barberturno.admin.password=" + CLAVE, "--barberturno.admin.nombre=Administración")) {
                assertThat(arrancada.isActive()).isTrue();
            }
            var antes = huella(c);
            new AdminInicialRunner(auth, ejecutor, " ", ADMIN, "OtraClaveFicticia32", "Otro nombre")
                    .run(new DefaultApplicationArguments());
            assertThat(huella(c)).isEqualTo(antes);
            assertThat(jdbc(c).queryForObject("select count(*) from negocio", Long.class)).isZero();
            assertThat(jdbc(c).queryForObject("select count(*) from membresia", Long.class)).isZero();
            assertThat(jdbc(c).queryForObject("select count(*) from usuario", Long.class)).isOne();
            comprobarPlataforma();
        }
    }

    @Test void acreditarCuentaExistente_sinNegocio_noAlteraCredencialesNiBaja() throws Exception {
        try (var c = propietario()) {
            ejecutar(c, "aplicar", "[\"barberturno\",\"sur\"]");
            var j = jdbc(c);
            j.update("update usuario set rol_plataforma='NINGUNO', activo=false where correo=?", ADMIN);
            var antes = j.queryForMap("select password_hash,token_version,activo,nombre from usuario where correo=?", ADMIN);
            new AdminInicialRunner(auth, ejecutor, "", ADMIN, "OtraClaveFicticia32", "Otro nombre")
                    .run(new DefaultApplicationArguments());
            assertThat(j.queryForMap("select password_hash,token_version,activo,nombre from usuario where correo=?", ADMIN)).isEqualTo(antes);
            assertThat(j.queryForObject("select rol_plataforma from usuario where correo=?", String.class, ADMIN)).isEqualTo("ADMIN_PLATAFORMA");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"membresia", "cliente", "reserva"})
    void usuarioRealLigado_abortaTodoSinCambios(String relacion) throws Exception {
        try (var c = propietario()) {
            var j = jdbc(c);
            contexto(c, 1);
            long usuario = switch (relacion) {
                case "membresia" -> j.queryForObject("select usuario_id from membresia where negocio_id=1 limit 1", Long.class);
                case "cliente" -> j.queryForObject("select usuario_id from cliente where negocio_id=1 and usuario_id is not null limit 1", Long.class);
                default -> {
                    long actor = j.queryForObject("insert into usuario(nombre,correo,password_hash,rol,creado_en,actualizado_en) select 'Actor ficticio','actor@ejemplo.test',password_hash,'ADMIN',current_timestamp,current_timestamp from usuario where correo=? returning id", Long.class, ADMIN);
                    j.update("update reserva set creada_por=? where negocio_id=1", actor);
                    yield actor;
                }
            };
            // Dominio reservado; simula una identidad real sin utilizar datos de una persona.
            j.update("update usuario set correo='persona-ficticia@example.org' where id=?", usuario);
            var antes = huella(c);
            assertThatThrownBy(() -> ejecutar(c, "aplicar", "[\"barberturno\",\"sur\"]"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("usuarios reales");
            assertThat(huella(c)).isEqualTo(antes);
        }
    }

    @Test void slugInexistente_abortaAntesDeModificar() throws Exception {
        try (var c = propietario()) {
            var antes = huella(c);
            assertThatThrownBy(() -> ejecutar(c, "aplicar", "[\"barberturno\",\"ausente\"]"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("no existe");
            assertThat(huella(c)).isEqualTo(antes);
        }
    }

    @Test void usuarioFicticioReferenciadoEnOtroNegocio_noSeEliminaPorRls() throws Exception {
        try (var c = propietario()) {
            var j = jdbc(c);
            long real = negocioReal(c);
            contexto(c, real);
            j.update("insert into cliente(usuario_id,nombre,creado_en,actualizado_en) select id,'Ficha compartida',current_timestamp,current_timestamp from usuario where correo='cliente@ejemplo.test'");
            var antes = huellaNegocio(c, real);
            ejecutar(c, "aplicar", "[\"barberturno\",\"sur\"]");
            assertThat(j.queryForObject("select count(*) from usuario where correo='cliente@ejemplo.test'", Long.class)).isOne();
            assertThat(huellaNegocio(c, real)).isEqualTo(antes);
        }
    }

    private Connection propietario() throws SQLException {
        var e = sql.entorno();
        var c = DriverManager.getConnection(e.getRequiredProperty("spring.datasource.url"),
                e.getRequiredProperty("spring.flyway.user"), e.getRequiredProperty("spring.flyway.password"));
        if (!"barberturno_v2_test".equals(c.getCatalog())) {
            c.close(); throw new IllegalStateException("Destino no autorizado");
        }
        var j = jdbc(c);
        assertThat(j.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2");
        assertThat(j.queryForObject("select rolsuper or rolbypassrls from pg_roles where rolname=current_user", Boolean.class)).isFalse();
        return c;
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc(Connection c) {
        return new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
    }

    private void contexto(Connection c, long id) {
        jdbc(c).queryForObject("select set_config('app.negocio_id',?,false)", String.class, Long.toString(id));
    }

    private List<String> tablas(Connection c) {
        return jdbc(c).queryForList("select table_name from information_schema.columns where table_schema='public' and column_name='negocio_id' order by table_name", String.class);
    }

    private Map<String, Object> huella(Connection c) {
        var resultado = new LinkedHashMap<String, Object>();
        var j = jdbc(c);
        for (String tabla : List.of("usuario", "negocio", "flyway_schema_history")) {
            resultado.put(tabla, j.queryForList("select row_to_json(t)::text from " + tabla + " t order by row_to_json(t)::text", String.class));
        }
        for (long id : j.queryForList("select id from negocio order by id", Long.class)) resultado.put("negocio" + id, huellaNegocio(c, id));
        return resultado;
    }

    private Map<String, Object> huellaNegocio(Connection c, long id) {
        contexto(c, id);
        var resultado = new LinkedHashMap<String, Object>();
        for (String tabla : tablas(c)) resultado.put(tabla, jdbc(c).queryForList("select row_to_json(t)::text from " + tabla + " t where negocio_id=? order by row_to_json(t)::text", String.class, id));
        return resultado;
    }

    private String ejecutar(Connection c, String modo, String slugs) throws Exception {
        jdbc(c).queryForObject("select set_config('bt.limpieza_slugs',?,false)", String.class, slugs);
        jdbc(c).queryForObject("select set_config('bt.limpieza_admin',?,false)", String.class, ADMIN);
        jdbc(c).queryForObject("select set_config('bt.limpieza_nombre','Administración',false)", String.class);
        jdbc(c).queryForObject("select set_config('bt.limpieza_modo',?,false)", String.class, modo);
        c.clearWarnings();
        try (var s = c.createStatement()) {
            s.execute(Files.readString(Path.of("../tools/v2/limpiar-demo.sql")));
            var resultado = new StringBuilder();
            for (var aviso = s.getWarnings(); aviso != null; aviso = aviso.getNextWarning()) resultado.append(aviso.getMessage()).append('\n');
            return resultado.toString();
        }
    }

    private long negocioReal(Connection c) {
        var j = jdbc(c);
        long id = j.queryForObject("insert into negocio(slug,nombre,creado_en,actualizado_en) values ('real-ficticio','Negocio ficticio conservado',current_timestamp,current_timestamp) returning id", Long.class);
        contexto(c, id);
        j.update("insert into usuario(nombre,correo,telefono,privacidad_aceptada_en,password_hash,rol,creado_en,actualizado_en) select 'Persona ficticia','persona-ficticia@example.org','999000164',current_timestamp,password_hash,'CLIENTE',current_timestamp,current_timestamp from usuario where correo=?", ADMIN);
        j.update("insert into membresia(negocio_id,usuario_id,rol,creado_en,actualizado_en) select ?,id,'PROPIETARIO',current_timestamp,current_timestamp from usuario where correo='persona-ficticia@example.org'", id);
        j.update("insert into cliente(usuario_id,nombre,creado_en,actualizado_en) select id,'Ficha ficticia conservada',current_timestamp,current_timestamp from usuario where correo='persona-ficticia@example.org'");
        j.update("insert into servicio(nombre,precio,duracion_min,creado_en,actualizado_en) values ('Servicio conservado',20,30,current_timestamp,current_timestamp)");
        return id;
    }

    private void completarTablas(org.springframework.jdbc.core.JdbcTemplate j, long id) {
        j.update("insert into membresia(negocio_id,usuario_id,rol,creado_en,actualizado_en) select ?,id,'PROPIETARIO',current_timestamp,current_timestamp from usuario where correo=?", id, ADMIN);
        j.update("insert into auditoria_plataforma(actor_id,accion,negocio_id,creado_en) select id,'CREAR_NEGOCIO',?,current_timestamp from usuario where correo=?", id, ADMIN);
        j.update("insert into invitacion(negocio_id,correo,rol,token_hash,expira_en,invitada_por,creado_en,actualizado_en) select ?,'invitado@ejemplo.test','PROPIETARIO',decode(md5(?::text),'hex'),current_timestamp+interval '1 day',id,current_timestamp,current_timestamp from usuario where correo=?", id, id, ADMIN);
        j.update("insert into canal_whatsapp(negocio_id,waba_id,phone_number_id,numero_visible,creado_en,actualizado_en) values (?,'123',?, '51999000164',current_timestamp,current_timestamp)", id, Long.toString(id));
        j.update("insert into evento_whatsapp_entrante(clave_idempotencia,tipo,phone_number_id,negocio_id,cuerpo,proximo_intento,recibido_en) values (?,'MENSAJE',?,?,'{}',current_timestamp,current_timestamp)", "limpieza-" + id, Long.toString(id), id);
        long ficha = j.queryForObject("insert into cliente(nombre,wa_id,creado_en,actualizado_en) values ('Ficha ficticia WhatsApp','51999000164',current_timestamp,current_timestamp) returning id", Long.class);
        long conv = j.queryForObject("insert into conversacion(cliente_id,wa_id,creado_en,actualizado_en) values (?,'51999000164',current_timestamp,current_timestamp) returning id", Long.class, ficha);
        j.update("insert into mensaje_whatsapp(conversacion_id,direccion,origen,tipo,contenido,estado,proximo_intento,creado_en,actualizado_en) values (?,'SALIENTE','ASISTENTE','TEXTO','{}','PENDIENTE',current_timestamp,current_timestamp,current_timestamp)", conv);
        j.update("insert into operacion_pendiente(conversacion_id,cliente_id,tipo,datos,nonce,expira_en,creado_en,actualizado_en) values (?,?,'CREAR','{}',decode(repeat('00',16),'hex'),current_timestamp+interval '1 hour',current_timestamp,current_timestamp)", conv, ficha);
        long turno = j.queryForObject("insert into turno_ia(conversacion_id,revision,texto,creado_en,actualizado_en) values (?,0,'Prueba ficticia',current_timestamp,current_timestamp) returning id", Long.class, conv);
        j.update("insert into llamada_ia(turno_id,numero,conversacion_id,dia,modelo,coste_reservado_micro_usd) values (?,1,?,current_date,'ficticio',1)", turno, conv);
        j.update("insert into uso_ia(dia) values (current_date)");
        long barbero = j.queryForObject("select id from barbero where negocio_id=? limit 1", Long.class, id);
        j.update("insert into codigo_vinculo_whatsapp(barbero_id,codigo_hash,caduca_en,creado_en) values (?,?,current_timestamp+interval '1 hour',current_timestamp)", barbero, String.format("%064d", id));
        j.update("insert into vinculo_whatsapp_profesional(barbero_id,wa_id,creado_en) values (?,'51999000165',current_timestamp)", barbero);
    }

    private void comprobarPlataforma() throws Exception {
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        try (var http = HttpClient.newBuilder().cookieHandler(cookies).build()) {
            enviar(http, "GET", "/api/auth/sesion", null, null);
            assertThat(enviar(http, "POST", "/api/auth/login", "{\"correo\":\"" + ADMIN + "\",\"password\":\"" + CLAVE + "\"}", token(cookies)).statusCode()).isEqualTo(200);
            String alta = "{\"nombre\":\"Nuevo negocio ficticio\",\"slug\":\"nuevo-real\",\"correoPropietario\":\"titular@ejemplo.test\"}";
            assertThat(enviar(http, "POST", "/api/plataforma/negocios", alta, null).statusCode()).isEqualTo(403);
            assertThat(enviar(http, "POST", "/api/plataforma/negocios", alta, "incorrecto").statusCode()).isEqualTo(403);
            var respuesta = enviar(http, "POST", "/api/plataforma/negocios", alta, token(cookies));
            assertThat(respuesta.statusCode()).isEqualTo(201);
            assertThat(mapper.readTree(respuesta.body()).get("enlaceInvitacion").asString()).isNotBlank();
            assertThat(enviar(http, "GET", "/api/plataforma/negocios", null, null).statusCode()).isEqualTo(200);
        } finally { cookies.getCookieStore().removeAll(); }
    }

    private String token(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElseThrow();
    }

    private HttpResponse<String> enviar(HttpClient http, String metodo, String ruta, String cuerpo, String csrf) throws Exception {
        var p = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta));
        if (csrf != null) p.header("X-XSRF-TOKEN", csrf);
        if (cuerpo != null) p.header("Content-Type", "application/json");
        return http.send(p.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
