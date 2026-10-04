package pe.barberturno.reservations;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import pe.barberturno.common.error.*;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas RF-16/17 y CP-18 con PostgreSQL, sesión real y reloj determinista. */
@SpringBootTest(properties = {"barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false", "barberturno.reservas.confirmacion-manual=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditoriaAvisosIT extends ReservaPruebaBase {
    @Autowired ReservaConsultaService consultas;

    private Usuario admin() {
        return usuarios.saveAndFlush(new Usuario("Administrador ficticio", "admin@ejemplo.test", null,
                "hash-ficticio-no-utilizable", Rol.ADMIN, null, reloj.instant()));
    }
    private Usuario usuario(Rol rol) {
        return switch (rol) { case CLIENTE -> cliente; case BARBERO -> barbero.getUsuario(); case ADMIN -> admin(); };
    }
    private long aviso(Usuario usuario) {
        return jdbc.queryForObject("select id from notificacion where usuario_id=? order by id limit 1", Long.class, usuario.getId());
    }
    private void conteo(Usuario usuario, int cantidad) throws Exception {
        mvc.perform(get("/api/notificaciones/conteo").cookie(sesion(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.noLeidas").value(cantidad));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cp18_crearConfirmarCancelar_historialYDestinatariosExactos(boolean confirmaAdmin) throws Exception {
        var administrador = admin();
        var confirmador = confirmaAdmin ? administrador : barbero.getUsuario();
        var pendiente = crearHttp(cliente, cmd(barbero, "10:00"));
        var fechaCreacion = TiempoNegocio.aLima(reloj.instant());
        reloj.adelantar(Duration.ofMinutes(1));
        mvc.perform(conCsrf(post("/api/reservas/{id}/transiciones", pendiente.id()).contentType("application/json")
                .content("{\"estado\":\"CONFIRMADA\",\"version\":0}"), sesion(confirmador))).andExpect(status().isOk());
        var fechaConfirmacion = TiempoNegocio.aLima(reloj.instant());
        reloj.adelantar(Duration.ofMinutes(1));
        mvc.perform(conCsrf(post("/api/reservas/{id}/cancelacion", pendiente.id()).contentType("application/json")
                .content("{\"version\":1,\"motivo\":\"Cambio de planes\"}"), sesion(cliente))).andExpect(status().isOk());
        var fechas = List.of(fechaCreacion, fechaConfirmacion, TiempoNegocio.aLima(reloj.instant()));
        var acciones = List.of("CREAR", "CONFIRMAR", "CANCELAR");
        var nombres = List.of(cliente.getNombre(), confirmador.getNombre(), cliente.getNombre());
        var estados = List.of("PENDIENTE", "CONFIRMADA", "CANCELADA");
        for (var personal : List.of(administrador, barbero.getUsuario())) {
            var historia = json.readTree(mvc.perform(get("/api/reservas/{id}/auditoria", pendiente.id()).cookie(sesion(personal)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(historia.size()).isEqualTo(3);
            for (int i = 0; i < 3; i++) {
                var cambio = historia.get(i);
                assertThat(cambio.get("accion").asString()).isEqualTo(acciones.get(i));
                assertThat(cambio.get("actorNombre").asString()).isEqualTo(nombres.get(i));
                assertThat(OffsetDateTime.parse(cambio.get("creadoEn").asString())).isEqualTo(fechas.get(i));
                assertThat(cambio.get("creadoEn").asString()).endsWith("-05:00");
                assertThat(cambio.get("excepcional").asBoolean()).isFalse();
                assertThat(cambio.get("estadoNuevo").asString()).isEqualTo(estados.get(i));
                assertThat(cambio.get("datosNuevos").get("estado").asString()).isEqualTo(estados.get(i));
                assertThat(cambio.size()).isEqualTo(9);
                if (i > 0) {
                    assertThat(cambio.get("estadoAnterior").asString()).isEqualTo(estados.get(i - 1));
                    assertThat(cambio.get("datosAnteriores").get("estado").asString()).isEqualTo(estados.get(i - 1));
                }
            }
            assertThat(historia.get(0).get("estadoAnterior").isNull()).isTrue();
            assertThat(historia.get(0).get("datosAnteriores").isNull()).isTrue();
            assertThat(historia.get(0).get("datosNuevos").get("inicio").asString()).isEqualTo(pendiente.inicio().toString());
            assertThat(historia.get(0).get("datosNuevos").get("fin").asString()).isEqualTo(pendiente.fin().toString());
            assertThat(historia.get(0).get("datosNuevos").get("barberoId").asLong()).isEqualTo(barbero.getId());
            assertThat(historia.get(2).get("motivo").asString()).isEqualTo("Cambio de planes");
        }
        var tiposCliente = List.of("CANCELAR", "CONFIRMAR", "CREAR");
        var tiposBarbero = confirmaAdmin ? tiposCliente : List.of("CANCELAR", "CREAR");
        for (var destinatario : List.of(cliente, barbero.getUsuario())) {
            var tipos = destinatario == cliente ? tiposCliente : tiposBarbero;
            var contenido = json.readTree(mvc.perform(get("/api/notificaciones").cookie(sesion(destinatario)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(tipos.size()))
                    .andReturn().getResponse().getContentAsString()).get("contenido");
            for (int i = 0; i < tipos.size(); i++) {
                assertThat(contenido.get(i).get("tipo").asString()).isEqualTo(tipos.get(i));
                assertThat(contenido.get(i).get("reservaId").asLong()).isEqualTo(pendiente.id());
                assertThat(contenido.get(i).get("leida").asBoolean()).isFalse();
                assertThat(contenido.get(i).get("mensaje").asString()).contains(pendiente.codigo());
                assertThat(contenido.get(i).get("creadoEn").asString()).endsWith("-05:00");
                assertThat(contenido.get(i).size()).isEqualTo(6);
            }
            conteo(destinatario, tipos.size());
        }
        conteo(administrador, 0); conteo(datos.cliente("ajeno"), 0);
        assertThat(cantidad("notificacion")).isEqualTo(confirmaAdmin ? 6 : 5);
    }

    @ParameterizedTest @CsvSource({"propietario,403,PROHIBIDO", "clienteAjeno,404,NO_ENCONTRADO",
            "barberoAjeno,404,NO_ENCONTRADO", "asignado,200,", "admin,200,"})
    void auditoria_permisosUniformes(String identidad, int http, String codigo) throws Exception {
        var dto = crearHttp(cliente, cmd(barbero, "10:00"));
        var actor = switch (identidad) {
            case "propietario" -> cliente; case "clienteAjeno" -> datos.cliente("ajeno");
            case "barberoAjeno" -> perfil("otro").getUsuario(); case "asignado" -> barbero.getUsuario(); default -> admin();
        };
        var resultado = mvc.perform(get("/api/reservas/{id}/auditoria", dto.id()).cookie(sesion(actor))).andExpect(status().is(http));
        if (codigo != null) {
            resultado.andExpect(jsonPath("$.codigo").value(codigo));
            assertThatThrownBy(() -> consultas.auditoria(actor(actor), dto.id()))
                    .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo().name()).isEqualTo(codigo));
        }
    }
    @ParameterizedTest @EnumSource(Rol.class)
    void auditoria_inexistente404ParaTodos(Rol rol) throws Exception {
        mvc.perform(get("/api/reservas/999999/auditoria").cookie(sesion(usuario(rol))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
    @Test void auditoria_sinHistorialDevuelveListaVacia() throws Exception {
        var dto = reservas.saveAndFlush(new Reserva(cliente, barbero, servicio, instante("10:00"), EstadoReserva.PENDIENTE, cliente, reloj.instant()));
        mvc.perform(get("/api/reservas/{id}/auditoria", dto.getId()).cookie(sesion(barbero.getUsuario())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void auditoria_empateDeFechaOrdenaPorIdentidadYExponeExcepcion() throws Exception {
        var dto = crearHttp(cliente, cmd(barbero, "10:00"));
        reloj.adelantar(Duration.between(reloj.instant(), instante("09:00")));
        var administrador = admin();
        mvc.perform(conCsrf(post("/api/reservas/{id}/cancelacion", dto.id()).contentType("application/json")
                .content("{\"version\":0,\"motivo\":\"Excepción administrativa\"}"), sesion(administrador))).andExpect(status().isOk());
        jdbc.update("update auditoria_reserva set creado_en=? where reserva_id=?", java.sql.Timestamp.from(reloj.instant()), dto.id());
        mvc.perform(get("/api/reservas/{id}/auditoria", dto.id()).cookie(sesion(administrador)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].accion").value("CREAR"))
                .andExpect(jsonPath("$[1].accion").value("CANCELAR"))
                .andExpect(jsonPath("$[1].motivo").value("Excepción administrativa"))
                .andExpect(jsonPath("$[1].excepcional").value(true));
    }

    @Test void cp18_clienteANoPuedeMarcarAvisoDeB_404SinCambios() throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00"));
        var otro = datos.cliente("otro"); crearHttp(otro, cmd(barbero, "11:00"));
        mvc.perform(conCsrf(post("/api/notificaciones/{id}/lectura", aviso(otro)), sesion(cliente)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        conteo(cliente, 1); conteo(otro, 1);
        mvc.perform(get("/api/notificaciones").param("usuarioId", otro.getId().toString()).cookie(sesion(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(aviso(cliente)));
    }
    @ParameterizedTest @EnumSource(Rol.class)
    void avisos_cadaRolLeeSoloLosSuyosYMarcaIdempotentemente(Rol rol) throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00")); crearHttp(cliente, cmd(barbero, "11:00"));
        var destinatario = usuario(rol);
        if (rol == Rol.ADMIN) jdbc.update("update notificacion set usuario_id=? where usuario_id=?", destinatario.getId(), cliente.getId());
        conteo(destinatario, 2);
        var id = aviso(destinatario);
        for (int i = 0; i < 2; i++) mvc.perform(conCsrf(post("/api/notificaciones/{id}/lectura", id), sesion(destinatario)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        conteo(destinatario, 1);
        mvc.perform(get("/api/notificaciones").param("soloNoLeidas", "true").cookie(sesion(destinatario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].leida").value(false));
        mvc.perform(get("/api/notificaciones").param("soloNoLeidas", "false").cookie(sesion(destinatario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
        for (int i = 0; i < 2; i++) mvc.perform(conCsrf(post("/api/notificaciones/lectura"), sesion(destinatario)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        conteo(destinatario, 0); conteo(rol == Rol.BARBERO ? cliente : barbero.getUsuario(), 2);
        mvc.perform(get("/api/notificaciones").param("soloNoLeidas", "true").cookie(sesion(destinatario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isEmpty());
        mvc.perform(get("/api/notificaciones").cookie(sesion(destinatario))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].leida").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }
    @ParameterizedTest @EnumSource(Rol.class)
    void avisos_sinAvisosContadorCeroYLecturaTodas204(Rol rol) throws Exception {
        var destinatario = usuario(rol); conteo(destinatario, 0);
        mvc.perform(get("/api/notificaciones").cookie(sesion(destinatario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isEmpty())
                .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(0)).andExpect(jsonPath("$.totalPaginas").value(0));
        mvc.perform(conCsrf(post("/api/notificaciones/lectura"), sesion(destinatario))).andExpect(status().isNoContent());
        mvc.perform(conCsrf(post("/api/notificaciones/999999/lectura"), sesion(destinatario)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
    @Test void paginacion_ordenDescendenteYDesempateSinDuplicados() throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00")); crearHttp(cliente, cmd(barbero, "11:00"));
        reloj.adelantar(Duration.ofMinutes(1)); crearHttp(cliente, cmd(barbero, "12:00"));
        var ids = jdbc.queryForList("select id from notificacion where usuario_id=? order by creado_en desc,id desc", Long.class, cliente.getId());
        for (int pagina = 0; pagina < 3; pagina++) mvc.perform(get("/api/notificaciones").param("pagina", Integer.toString(pagina))
                .param("tamano", "1").cookie(sesion(cliente))).andExpect(status().isOk()).andExpect(jsonPath("$.pagina").value(pagina))
                .andExpect(jsonPath("$.tamano").value(1)).andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(3)).andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(ids.get(pagina)));
        mvc.perform(get("/api/notificaciones").param("pagina", "3").param("tamano", "1").cookie(sesion(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isEmpty()).andExpect(jsonPath("$.totalElementos").value(3));
        mvc.perform(get("/api/notificaciones").param("tamano", "100").cookie(sesion(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(3));
    }
    @ParameterizedTest @CsvSource({"pagina,-1", "tamano,0", "tamano,101", "pagina,abc", "tamano,abc", "soloNoLeidas,abc",
            "pagina,''", "tamano,''", "soloNoLeidas,''"})
    void paginacion_parametrosInvalidos400(String parametro, String valor) throws Exception {
        mvc.perform(get("/api/notificaciones").param(parametro, valor).cookie(sesion(cliente)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }
    @Test void contador_utilizaCountSinCargarEntidadesYExisteIndice() throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00")); var cookie = sesion(cliente); SqlPruebas.limpiar();
        mvc.perform(get("/api/notificaciones/conteo").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.noLeidas").value(1));
        var sqlAvisos = SqlPruebas.sentencias().stream().filter(s -> s.contains("from notificacion")).toList();
        assertThat(sqlAvisos).hasSize(1); assertThat(sqlAvisos.getFirst()).contains("select count(", "usuario_id", "leida");
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname='public' and indexname='notificacion_usuario_ix'", String.class))
                .contains("usuario_id, leida, creado_en DESC");
    }
    @ParameterizedTest @ValueSource(strings = {"/api/reservas/999999/auditoria", "/api/notificaciones", "/api/notificaciones/conteo",
            "/api/notificaciones/999999/lectura", "/api/notificaciones/lectura"})
    void sinSesion401(String ruta) throws Exception {
        var peticion = ruta.endsWith("lectura") ? conCsrf(post(ruta), new Cookie("BT_SESION", "")) : get(ruta);
        mvc.perform(peticion).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
    @ParameterizedTest @CsvSource({"CLIENTE,false", "CLIENTE,true", "BARBERO,false", "BARBERO,true", "ADMIN,false", "ADMIN,true"})
    void escrituras_sesionValidaSinCsrfOIncorrecto403(Rol rol, boolean incorrecto) throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00")); var destinatario = usuario(rol);
        for (var ruta : List.of("/api/notificaciones/" + aviso(cliente) + "/lectura", "/api/notificaciones/lectura")) {
            var peticion = post(ruta).cookie(sesion(destinatario));
            if (incorrecto) peticion.cookie(new Cookie("XSRF-TOKEN", "valor-correcto")).header("X-XSRF-TOKEN", "valor-incorrecto");
            mvc.perform(peticion).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
            assertThat(jdbc.queryForObject("select count(*) from notificacion where leida", Integer.class)).isZero();
        }
    }
    @ParameterizedTest @EnumSource(Rol.class)
    void contraseñaTemporal_rechazaAmbasEscrituras403(Rol rol) throws Exception {
        crearHttp(cliente, cmd(barbero, "10:00")); var destinatario = usuario(rol);
        destinatario.cambiarPassword("hash-ficticio-temporal", true, reloj.instant()); usuarios.saveAndFlush(destinatario);
        for (var ruta : List.of("/api/notificaciones/" + aviso(cliente) + "/lectura", "/api/notificaciones/lectura"))
            mvc.perform(conCsrf(post(ruta), sesion(destinatario))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
        assertThat(jdbc.queryForObject("select count(*) from notificacion where leida", Integer.class)).isZero();
    }
}
