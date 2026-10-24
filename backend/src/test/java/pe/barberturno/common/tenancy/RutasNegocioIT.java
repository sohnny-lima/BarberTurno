package pe.barberturno.common.tenancy;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.platform.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RutasNegocioIT extends PeticionNegocioPruebaBase {
    @org.springframework.beans.factory.annotation.Autowired tools.jackson.databind.json.JsonMapper json;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean pe.barberturno.users.PerfilService perfiles;

    @Test void mismaSesion_alternaPropietarioEnAYBarberoEnB_sinReutilizarElRolAnterior() throws Exception {
        var cuenta = personal("alterna", "a", RolNegocio.PROPIETARIO);
        membresias.save(new Membresia(b, cuenta.getId(), RolNegocio.BARBERO, reloj.instant()));
        en("b", () -> barberos.saveAndFlush(new pe.barberturno.scheduling.Barbero(cuenta, "Corte", reloj.instant())));
        var cookie = sesion(cuenta);
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/n/a/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").value("ADMIN"));
            mvc.perform(get("/api/n/a/usuarios").cookie(cookie)).andExpect(status().isOk());
            mvc.perform(get("/api/n/b/sesion").cookie(cookie)).andExpect(jsonPath("$.rol").value("BARBERO"));
            mvc.perform(get("/api/n/b/usuarios").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/api/n/b/reservas").cookie(cookie).param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/mis-negocios").cookie(cookie)).andExpect(jsonPath("$[0].rol").value("PROPIETARIO"))
                .andExpect(jsonPath("$[1].rol").value("BARBERO"));
    }

    @Test void identidadGlobal_sinContextoRls_yPasswordPerfilNoDependenDelNegocio() throws Exception {
        servicio("a"); servicio("b");
        var cuenta = personal("global", "a", RolNegocio.PROPIETARIO);
        cuenta.cambiarPassword(passwords.encode("ClaveGlobal123"), false, reloj.instant());
        usuarios.saveAndFlush(cuenta);
        en("a", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(cuenta, reloj.instant())));
        var ficha = en("a", () -> jdbc.queryForMap("select * from cliente where usuario_id=?", cuenta.getId()));
        var observado = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(inv -> {
            assertThat(contexto.negocioActual()).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from servicio", Integer.class)).isZero();
            observado.set(true);
            return inv.callRealMethod();
        }).when(perfiles).obtener(cuenta.getId());
        var cookie = sesion(cuenta);
        mvc.perform(get("/api/perfil").cookie(cookie)).andExpect(status().isOk());
        assertThat(observado).isTrue();
        mvc.perform(csrf(put("/api/perfil").contentType("application/json")
                .content("{\"nombre\":\"Identidad global editada\",\"telefono\":\"999000002\"}"), cookie))
                .andExpect(status().isOk());
        mvc.perform(csrf(put("/api/auth/password").contentType("application/json")
                .content("{\"passwordActual\":\"ClaveGlobal123\",\"passwordNueva\":\"OtraClaveGlobal456\"}"), cookie))
                .andExpect(status().isNoContent());
        assertThat(this.<java.util.Map<String,Object>>en("a", () -> jdbc.queryForMap("select * from cliente where usuario_id=?", cuenta.getId())))
                .isEqualTo(ficha);
        mvc.perform(get("/api/perfil").cookie(cookie)).andExpect(status().isUnauthorized());
        for (String ruta : new String[]{"auth/login", "auth/logout", "auth/password", "auth/registro", "auth/sesion", "perfil", "mis-negocios"}) {
            for (var metodo : new org.springframework.http.HttpMethod[]{org.springframework.http.HttpMethod.GET,
                    org.springframework.http.HttpMethod.POST, org.springframework.http.HttpMethod.PUT}) {
                mvc.perform(request(metodo, "/api/n/a/" + ruta)).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
            }
        }
    }

    @Test void recorridoCompleto_registroReservaAgendaTransicionReporte_todoPorElPrefijo() throws Exception {
        var datos = en("a", () -> DatosNegocioPrueba.crear(jdbc, "recorrido"));
        en("a", () -> jornadas.saveAndFlush(new pe.barberturno.scheduling.Jornada(
                barberos.findById(datos.barbero()).orElseThrow(), (short)1, java.time.LocalTime.of(9,0), java.time.LocalTime.of(18,0))));
        var alta = mvc.perform(csrf(post("/api/n/a/registro").contentType("application/json")
                .content(registro("recorrido@ejemplo.test")), null)).andExpect(status().isCreated()).andReturn().getResponse();
        var cliente = alta.getCookie("BT_SESION");
        var creada = mvc.perform(csrf(post("/api/n/a/reservas").contentType("application/json").content("""
                {"servicioId":%d,"barberoId":%d,"inicio":"2026-09-28T10:00:00-05:00"}
                """.formatted(datos.servicio(), datos.barbero())), cliente)).andExpect(status().isCreated()).andReturn().getResponse();
        long id = json.readTree(creada.getContentAsString()).get("id").asLong();
        assertThat(creada.getHeader("Location")).isEqualTo("/api/n/a/reservas/" + id);
        mvc.perform(get(creada.getHeader("Location")).cookie(cliente)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        var gestor = sesion(personal("recorrido-gestor", "a", RolNegocio.PROPIETARIO));
        mvc.perform(get("/api/n/a/reservas").cookie(gestor).param("desde", "2026-09-28").param("hasta", "2026-09-28"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(id));
        reloj.adelantar(java.time.Duration.ofHours(1));
        mvc.perform(csrf(post("/api/n/a/reservas/" + id + "/transiciones").contentType("application/json")
                .content("{\"estado\":\"EN_ATENCION\",\"version\":0}"), gestor)).andExpect(status().isOk());
        reloj.adelantar(java.time.Duration.ofMinutes(30));
        mvc.perform(csrf(post("/api/n/a/reservas/" + id + "/transiciones").contentType("application/json")
                .content("{\"estado\":\"COMPLETADA\",\"version\":1}"), gestor)).andExpect(status().isOk());
        mvc.perform(get("/api/n/a/reportes/resumen").cookie(gestor).param("desde", "2026-09-28").param("hasta", "2026-09-28"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.porEstado.COMPLETADA").value(1));
        mvc.perform(get("/api/n/a/reservas/mias").cookie(cliente)).andExpect(jsonPath("$.contenido[0].estado").value("COMPLETADA"));
    }

    enum Operacion {
        CATALOGO, SERVICIO_ALTA, SERVICIO_EDITAR, SERVICIO_ESTADO,
        PROFESIONALES, PROFESIONAL_ALTA, PROFESIONAL_EDITAR, PROFESIONAL_ESTADO,
        JORNADAS_LEER, JORNADAS_GUARDAR, BLOQUEOS_LEER, BLOQUEO_ALTA, BLOQUEO_LOTE, BLOQUEO_BAJA,
        DISPONIBILIDAD, RESERVA_ALTA, MIS_RESERVAS, AGENDA, DETALLE, REPROGRAMAR, CANCELAR, TRANSICION,
        AUDITORIA, AVISOS, CONTEO, LECTURA, LECTURA_TODAS, REPORTE, EXCEL, USUARIOS,
        FICHA, FICHA_EDITAR, RESTABLECER, CUENTA_ESTADO, ALTA_PROPIA, REGISTRO, SESION
    }

    static Stream<Arguments> matriz() {
        return Stream.of("PUBLICO", "SIN_ROL", "CLIENTE", "BARBERO", "ENCARGADO", "PROPIETARIO")
                .flatMap(rol -> Stream.of(Operacion.values()).map(op -> Arguments.of(rol, op)));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("matriz")
    void matrizCompleta_conPrefijoSesionCsrfYRolReales(String rol, Operacion operacion) throws Exception {
        var datos = en("a", () -> DatosNegocioPrueba.crear(jdbc, "matriz-a"));
        en("b", () -> DatosNegocioPrueba.crear(jdbc, "matriz-b"));
        pe.barberturno.users.Usuario actor = null;
        if (!rol.equals("PUBLICO")) {
            actor = rol.equals("SIN_ROL") ? persona("sin-papel")
                    : usuarios.findById(rol.equals("CLIENTE") ? datos.cliente() : datos.admin()).orElseThrow();
            if (!rol.equals("CLIENTE") && !rol.equals("SIN_ROL")) {
                membresias.save(new Membresia(a, actor.getId(), RolNegocio.valueOf(rol), reloj.instant()));
            }
            long id = actor.getId();
            en("a", () -> jdbc.update("update notificacion set usuario_id=?", id));
        }
        long bloqueo = en("a", () -> jdbc.queryForObject("select id from bloqueo", Long.class));
        var peticion = peticionMatriz(operacion, datos, bloqueo, rol);
        Cookie cookie = actor == null ? null : sesion(actor);
        if (Set.of(Operacion.CATALOGO, Operacion.PROFESIONALES, Operacion.JORNADAS_LEER, Operacion.BLOQUEOS_LEER,
                Operacion.DISPONIBILIDAD, Operacion.MIS_RESERVAS, Operacion.AGENDA, Operacion.DETALLE,
                Operacion.AUDITORIA, Operacion.AVISOS, Operacion.CONTEO, Operacion.REPORTE, Operacion.EXCEL,
                Operacion.USUARIOS, Operacion.FICHA, Operacion.SESION).contains(operacion)) {
            if (cookie != null) peticion.cookie(cookie);
        } else {
            peticion = csrf(peticion, cookie);
        }
        mvc.perform(peticion.param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                .andExpect(status().is(esperado(rol, operacion)));
    }

    private static int esperado(String rol, Operacion op) {
        if (op == Operacion.REGISTRO) return 201;
        if (Set.of(Operacion.CATALOGO, Operacion.PROFESIONALES, Operacion.DISPONIBILIDAD).contains(op)) return 200;
        if (rol.equals("PUBLICO")) return 401;
        if (op == Operacion.SESION) return 200;
        if (op == Operacion.ALTA_PROPIA) return rol.equals("CLIENTE") ? 409 : 201;
        if (rol.equals("SIN_ROL")) return 403;
        boolean gestion = rol.equals("PROPIETARIO") || rol.equals("ENCARGADO");
        boolean profesional = rol.equals("BARBERO");
        boolean cliente = rol.equals("CLIENTE");
        boolean permitida = switch (op) {
            case DETALLE, AVISOS, CONTEO, LECTURA, LECTURA_TODAS -> true;
            case MIS_RESERVAS -> cliente;
            case RESERVA_ALTA, REPROGRAMAR, CANCELAR -> cliente || gestion;
            case AGENDA, TRANSICION, AUDITORIA, JORNADAS_LEER, BLOQUEOS_LEER -> profesional || gestion;
            default -> gestion;
        };
        if (!permitida) return 403;
        return switch (op) {
            case SERVICIO_ALTA, PROFESIONAL_ALTA, BLOQUEO_ALTA, BLOQUEO_LOTE, RESERVA_ALTA -> 201;
            case BLOQUEO_BAJA, LECTURA, LECTURA_TODAS -> 204;
            case TRANSICION -> 409; // Confirmar otra vez una confirmada alcanza la política de estados.
            default -> 200;
        };
    }

    private MockHttpServletRequestBuilder peticionMatriz(Operacion op, DatosNegocioPrueba d, long bloqueo, String rol) {
        String base = "/api/n/a";
        String catalogo = "{\"nombre\":\"Otro servicio\",\"descripcion\":\"Ficticio\",\"duracionMin\":30,\"precio\":20}";
        String intervalo = "\"inicio\":\"2026-10-01T16:00:00-05:00\",\"fin\":\"2026-10-01T16:30:00-05:00\",\"motivo\":\"Prueba ficticia\"";
        var solicitud = switch (op) {
            case CATALOGO -> get(base + "/servicios");
            case SERVICIO_ALTA -> post(base + "/servicios").content(catalogo);
            case SERVICIO_EDITAR -> put(base + "/servicios/" + d.servicio()).content(catalogo);
            case SERVICIO_ESTADO -> patch(base + "/servicios/" + d.servicio() + "/estado").content("{\"activo\":false}");
            case PROFESIONALES -> get(base + "/barberos");
            case PROFESIONAL_ALTA -> post(base + "/barberos").content("{\"nombre\":\"Profesional ficticio\",\"correo\":\"nuevo-profesional@ejemplo.test\",\"especialidad\":\"Corte\",\"passwordTemporal\":\"Temporal123\"}");
            case PROFESIONAL_EDITAR -> put(base + "/barberos/" + d.barbero()).content("{\"nombre\":\"Perfil local\",\"telefono\":\"999000001\",\"especialidad\":\"Corte\"}");
            case PROFESIONAL_ESTADO -> patch(base + "/barberos/" + d.barbero() + "/estado").content("{\"activo\":false}");
            case JORNADAS_LEER -> get(base + "/barberos/" + d.barbero() + "/jornadas");
            case JORNADAS_GUARDAR -> put(base + "/barberos/" + d.barbero() + "/jornadas").content("[{\"diaSemana\":4,\"horaInicio\":\"09:00\",\"horaFin\":\"18:00\"}]");
            case BLOQUEOS_LEER -> get(base + "/barberos/" + d.barbero() + "/bloqueos");
            case BLOQUEO_ALTA -> post(base + "/barberos/" + d.barbero() + "/bloqueos").content("{" + intervalo + "}");
            case BLOQUEO_LOTE -> post(base + "/bloqueos/lote").content("{\"barberoIds\":[" + d.barbero() + "]," + intervalo + "}");
            case BLOQUEO_BAJA -> delete(base + "/bloqueos/" + bloqueo);
            case DISPONIBILIDAD -> get(base + "/disponibilidad").param("servicioId", Long.toString(d.servicio())).param("fecha", "2026-10-01");
            case RESERVA_ALTA -> post(base + "/reservas").content("{\"servicioId\":" + d.servicio() + ",\"barberoId\":" + d.barbero() + ",\"inicio\":\"2026-10-01T11:00:00-05:00\"" + (rol.equals("CLIENTE") ? "" : ",\"clienteId\":" + d.cliente()) + "}");
            case MIS_RESERVAS -> get(base + "/reservas/mias");
            case AGENDA -> get(base + "/reservas");
            case DETALLE -> get(base + "/reservas/" + d.reserva());
            case REPROGRAMAR -> post(base + "/reservas/" + d.reserva() + "/reprogramacion").content("{\"inicio\":\"2026-10-01T11:00:00-05:00\",\"version\":0,\"motivo\":\"Cambio ficticio\"}");
            case CANCELAR -> post(base + "/reservas/" + d.reserva() + "/cancelacion").content("{\"version\":0,\"motivo\":\"Cambio ficticio\"}");
            case TRANSICION -> post(base + "/reservas/" + d.reserva() + "/transiciones").content("{\"estado\":\"CONFIRMADA\",\"version\":0}");
            case AUDITORIA -> get(base + "/reservas/" + d.reserva() + "/auditoria");
            case AVISOS -> get(base + "/notificaciones");
            case CONTEO -> get(base + "/notificaciones/conteo");
            case LECTURA -> post(base + "/notificaciones/" + en("a", () -> jdbc.queryForObject("select id from notificacion", Long.class)) + "/lectura");
            case LECTURA_TODAS -> post(base + "/notificaciones/lectura");
            case REPORTE -> get(base + "/reportes/resumen");
            case EXCEL -> get(base + "/reportes/resumen.xlsx");
            case USUARIOS -> get(base + "/usuarios");
            case FICHA -> get(base + "/clientes/" + d.ficha());
            case FICHA_EDITAR -> patch(base + "/clientes/" + d.ficha()).content("{\"nombre\":\"Ficha local\",\"telefono\":\"999000001\"}");
            case RESTABLECER -> post(base + "/usuarios/" + d.cliente() + "/restablecer-password");
            case CUENTA_ESTADO -> patch(base + "/usuarios/" + d.cliente() + "/estado").content("{\"activo\":false}");
            case ALTA_PROPIA -> post(base + "/clientes/alta");
            case REGISTRO -> post(base + "/registro").content(registro("matriz-nuevo@ejemplo.test"));
            case SESION -> get(base + "/sesion");
        };
        return solicitud.contentType("application/json");
    }

    static String registro(String correo) {
        return """
                {"nombre":"Cliente ficticio","correo":"%s","telefono":"999000001",
                 "password":"ClaveFicticia123","aceptaPrivacidad":true}
                """.formatted(correo);
    }

    @Test void registro_cuentaYFichaAtomicasEnLaRuta_conCookieRevocada_ySinMembresia() throws Exception {
        var respuesta = mvc.perform(csrf(post("/api/n/a/registro").contentType("application/json")
                .content(registro("registro-a@ejemplo.test")), new Cookie("BT_SESION", "revocada")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andReturn().getResponse();
        var cuenta = usuarios.findByCorreo("registro-a@ejemplo.test").orElseThrow();
        assertThat(en("a", () -> clientes.findByUsuarioId(cuenta.getId()))).isPresent();
        assertThat(en("b", () -> clientes.findByUsuarioId(cuenta.getId()))).isEmpty();
        assertThat(membresias.listarPropiasActivas(cuenta.getId())).isEmpty();
        assertThat(respuesta.getCookie("BT_SESION")).isNotNull();
        assertThat(respuesta.getCookie("XSRF-TOKEN")).isNotNull();
        mvc.perform(get("/api/n/a/sesion").cookie(respuesta.getCookie("BT_SESION")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("CLIENTE"));
        mvc.perform(csrf(post("/api/n/b/registro").contentType("application/json")
                .content(registro("registro-a@ejemplo.test")), null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CORREO_DUPLICADO"));
        assertThat(en("b", () -> clientes.findByUsuarioId(cuenta.getId()))).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"registro", "clientes/alta"})
    void escriturasNuevas_sesionValidaSinCsrfOIncorrecto_403(String ruta) throws Exception {
        var cuenta = persona("csrf");
        Cookie sesion = sesion(cuenta);
        String cuerpo = ruta.equals("registro") ? registro("otro@ejemplo.test") : "{}";
        mvc.perform(post("/api/n/a/" + ruta).cookie(sesion).contentType("application/json").content(cuerpo))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(csrf(post("/api/n/a/" + ruta).contentType("application/json").content(cuerpo), sesion)
                .with(r -> { r.removeHeader("X-XSRF-TOKEN"); r.addHeader("X-XSRF-TOKEN", "incorrecto"); return r; }))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        assertThat(this.<Long>en("a", clientes::count)).isZero();
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test void alta_cuentaSinRolObtieneFicha_sinConcederPersonal_yDuplicada409InclusoInactiva() throws Exception {
        var cuenta = persona("alta");
        var sesion = sesion(cuenta);
        mvc.perform(get("/api/n/a/sesion").cookie(sesion)).andExpect(jsonPath("$.rol").isEmpty());
        mvc.perform(csrf(post("/api/n/a/clientes/alta").contentType("application/json")
                .content("{\"usuarioId\":99999,\"negocioId\":" + b + "}"), sesion))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.activo").value(true));
        mvc.perform(get("/api/n/a/sesion").cookie(sesion)).andExpect(jsonPath("$.rol").value("CLIENTE"));
        assertThat(membresias.listarPropiasActivas(cuenta.getId())).isEmpty();
        assertThat(this.<Long>en("b", clientes::count)).isZero();
        mvc.perform(csrf(post("/api/n/a/clientes/alta"), sesion)).andExpect(status().isConflict());
        en("a", () -> { var ficha = clientes.findByUsuarioId(cuenta.getId()).orElseThrow(); ficha.desactivar(reloj.instant()); return clientes.saveAndFlush(ficha); });
        mvc.perform(csrf(post("/api/n/a/clientes/alta"), sesion)).andExpect(status().isConflict());
        mvc.perform(csrf(post("/api/n/b/clientes/alta"), sesion)).andExpect(status().isCreated());
        mvc.perform(csrf(post("/api/n/b/clientes/alta"), null)).andExpect(status().isUnauthorized());
    }

    @Test void misNegocios_soloPropiosActivos_prioridadPersonal_ySinContextoGlobal() throws Exception {
        var cuenta = personal("propio", "a", RolNegocio.ENCARGADO);
        var ajena = personal("ajena", "b", RolNegocio.PROPIETARIO);
        en("a", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(cuenta, reloj.instant())));
        en("b", () -> clientes.saveAndFlush(new pe.barberturno.customers.Cliente(cuenta, reloj.instant())));
        mvc.perform(get("/api/mis-negocios").cookie(sesion(cuenta)).param("usuarioId", ajena.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("a")).andExpect(jsonPath("$[0].rol").value("ENCARGADO"))
                .andExpect(jsonPath("$[1].slug").value("b")).andExpect(jsonPath("$[1].rol").value("CLIENTE"));
        en("b", () -> jdbc.update("update cliente set activo=false where usuario_id=?", cuenta.getId()));
        var miembro = membresias.findByNegocioIdAndUsuarioId(a, cuenta.getId()).orElseThrow();
        miembro.revocar(reloj.instant()); membresias.save(miembro);
        mvc.perform(get("/api/mis-negocios").cookie(sesion(cuenta)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].rol").value("CLIENTE"));
        en("a", () -> jdbc.update("update cliente set activo=false where usuario_id=?", cuenta.getId()));
        mvc.perform(get("/api/mis-negocios").cookie(sesion(cuenta))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/mis-negocios")).andExpect(status().isUnauthorized());
        assertThat(contexto.delimitado()).isFalse();
    }
}
