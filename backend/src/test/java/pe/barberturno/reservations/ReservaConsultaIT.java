package pe.barberturno.reservations;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.common.time.TiempoNegocio;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.support.*;
import pe.barberturno.users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PostgreSQL y HTTP sin transacción envolvente; datos ficticios de corrida y prototipo. */
@SpringBootTest(properties = {
        "barberturno.admin.correo=", "barberturno.admin.password=", "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=pe.barberturno.support.SqlPruebas"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservaConsultaIT extends ReservaPruebaBase {
    @Autowired ReservaAutorizacion autorizacion;
    @Autowired ReservaConsultaService consultas;
    Usuario luis;
    Usuario admin;
    Barbero miguel;
    Servicio barba;
    Reserva r101;
    Reserva r102;
    Reserva ajenaMiguel;
    Reserva historial;

    @BeforeEach void escenarioCorridaYPrototipo() {
        reloj.adelantar(Duration.ofHours(70)); // 01/10/2026 07:00 Lima, tres horas antes de R101.
        cliente.actualizarPerfil("Ana demo", "999000001", reloj.instant());
        usuarios.saveAndFlush(cliente);
        barbero.getUsuario().actualizarPerfil("Carlos", null, reloj.instant());
        usuarios.saveAndFlush(barbero.getUsuario());
        miguel = perfil("miguel");
        miguel.getUsuario().actualizarPerfil("Miguel", null, reloj.instant());
        usuarios.saveAndFlush(miguel.getUsuario());
        luis = datos.cliente("luis");
        luis.actualizarPerfil("Luis demo", "999000002", reloj.instant());
        usuarios.saveAndFlush(luis);
        admin = usuario("Administrador", Rol.ADMIN);
        servicio.editar("Corte clásico", "Corte y acabado", (short) 30, new BigDecimal("25.00"), reloj.instant());
        servicios.saveAndFlush(servicio);
        barba = servicios.saveAndFlush(new Servicio("Barba", "Perfilado de barba", (short) 20,
                new BigDecimal("20.00"), reloj.instant()));
        historial = insertar(cliente, miguel, servicio, "2026-09-27", "10:00", EstadoReserva.COMPLETADA);
        r101 = insertar(cliente, barbero, servicio, "2026-10-01", "10:00", EstadoReserva.CONFIRMADA);
        r102 = insertar(luis, barbero, barba, "2026-10-01", "10:30", EstadoReserva.PENDIENTE);
        ajenaMiguel = insertar(luis, miguel, servicio, "2026-10-01", "11:00", EstadoReserva.CONFIRMADA);
    }

    private org.springframework.test.web.servlet.ResultMatcher lista(String ruta, List<?> esperados) {
        return resultado -> {
            var nodo = json.readTree(resultado.getResponse().getContentAsString());
            for (String campo : ruta.split("[.]")) nodo = nodo.get(campo);
            assertThat(nodo).isEqualTo(json.valueToTree(esperados));
        };
    }

    private Usuario usuario(String nombre, Rol rol) {
        return usuarios.saveAndFlush(new Usuario(nombre, nombre.toLowerCase(Locale.ROOT) + "@ejemplo.test",
                null, "hash-ficticio-no-utilizable", rol, null, reloj.instant()));
    }

    private Reserva insertar(Usuario propietario, Barbero perfil, Servicio contratado,
            String fecha, String hora, EstadoReserva estado) {
        var inicio = TiempoNegocio.inicioDelDia(LocalDate.parse(fecha)).plusSeconds(LocalTime.parse(hora).toSecondOfDay());
        return reservas.saveAndFlush(new Reserva(propietario, perfil, contratado, inicio, estado, propietario, reloj.instant()));
    }

    private MockHttpServletRequestBuilder agenda(Usuario actor) {
        return get("/api/reservas").cookie(sesion(actor)).param("desde", "2026-09-21").param("hasta", "2026-10-04");
    }

    private MockHttpServletRequestBuilder detalle(Usuario actor, long id) {
        return get("/api/reservas/{id}", id).cookie(sesion(actor));
    }

    @Test void cp02_clienteSoloPropiasY404Uniforme() throws Exception {
        var ajena = mvc.perform(detalle(cliente, r102.getId())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse();
        var inexistente = mvc.perform(detalle(cliente, 999999)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse();
        assertThat(json.readTree(ajena.getContentAsString()).get("detail"))
                .isEqualTo(json.readTree(inexistente.getContentAsString()).get("detail"));
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].id").value(historial.getId()))
                .andExpect(jsonPath("$.contenido[1].id").value(r101.getId()))
                .andExpect(jsonPath("$.contenido[0].cliente.id").value(cliente.getId()))
                .andExpect(jsonPath("$.contenido[1].cliente.id").value(cliente.getId()))
                .andExpect(jsonPath("$.contenido[*].cliente.telefono").isEmpty());
    }

    @Test void cp02_carlosNoVeMiguelYFiltroForzado() throws Exception {
        var carlos = barbero.getUsuario();
        mvc.perform(detalle(carlos, ajenaMiguel.getId())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(agenda(carlos)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].barbero.id").value(barbero.getId()))
                .andExpect(jsonPath("$.contenido[1].barbero.id").value(barbero.getId()));
        mvc.perform(agenda(carlos).param("barberoId", miguel.getId().toString())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(agenda(carlos).param("barberoId", barbero.getId().toString())).andExpect(status().isOk());
        mvc.perform(agenda(carlos).param("clienteId", cliente.getId().toString())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(agenda(usuario("Sinperfil", Rol.BARBERO))).andExpect(status().isForbidden());
    }

    @Test void cp02_adminVeTodasYFiltraDimensionesYCombinacion() throws Exception {
        mvc.perform(agenda(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(4));
        mvc.perform(agenda(admin).param("barberoId", barbero.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(agenda(admin).param("servicioId", barba.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(r102.getId())).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(agenda(admin).param("estado", "CONFIRMADA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(agenda(admin).param("clienteId", luis.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(agenda(admin).param("clienteId", luis.getId().toString()).param("servicioId", barba.getId().toString())
                .param("estado", "PENDIENTE").param("barberoId", barbero.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(r102.getId()));
        mvc.perform(detalle(admin, ajenaMiguel.getId())).andExpect(status().isOk());
    }

    @Test void adminConPerfilSigueAdminYAutorizacionUsaUsuarioAsignado() throws Exception {
        var perfilAdmin = barberos.saveAndFlush(new Barbero(admin, "Corte", reloj.instant()));
        mvc.perform(agenda(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(4));
        mvc.perform(agenda(admin).param("barberoId", miguel.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(detalle(admin, r101.getId())).andExpect(status().isOk());
        assertThat(autorizacion.puedeVer(new UsuarioAutenticado(admin.getId(), Rol.ADMIN,
                Optional.of(perfilAdmin.getId()), false), r101)).isTrue();
        assertThat(autorizacion.puedeVer(new UsuarioAutenticado(miguel.getUsuario().getId(), Rol.BARBERO,
                Optional.of(barbero.getId()), false), r101)).isFalse();
        assertThat(autorizacion.puedeVer(actor(barbero.getUsuario()), r101)).isTrue();
        assertThat(autorizacion.puedeVer(actor(luis), r101)).isFalse();
        assertThat(autorizacion.puedeVer(actor(cliente), r101)).isTrue();
    }

    @Test void agendaSemanalLunesDomingoYFronteraLima() throws Exception {
        var lunes = insertar(cliente, barbero, servicio, "2026-09-28", "00:00", EstadoReserva.CANCELADA);
        var domingo = insertar(cliente, barbero, servicio, "2026-10-04", "23:30", EstadoReserva.CONFIRMADA);
        insertar(cliente, barbero, servicio, "2026-10-05", "00:00", EstadoReserva.CONFIRMADA);
        mvc.perform(get("/api/reservas").cookie(sesion(admin)).param("desde", "2026-09-28").param("hasta", "2026-10-04"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.contenido[0].id").value(lunes.getId()))
                .andExpect(jsonPath("$.contenido[4].id").value(domingo.getId()));
        var tarde = insertar(cliente, barbero, servicio, "2026-10-01", "23:30", EstadoReserva.CONFIRMADA);
        insertar(cliente, barbero, servicio, "2026-10-02", "00:00", EstadoReserva.CONFIRMADA);
        for (String ruta : List.of("/api/reservas", "/api/reservas/mias")) {
            mvc.perform(get(ruta).cookie(sesion(ruta.endsWith("mias") ? cliente : admin))
                    .param("desde", "2026-10-01").param("hasta", "2026-10-01"))
                    .andExpect(status().isOk()).andExpect(jsonPath(ruta.endsWith("mias") ? "$.contenido[1].id" : "$.contenido[3].id").value(tarde.getId()))
                    .andExpect(jsonPath(ruta.endsWith("mias") ? "$.contenido[1].inicio" : "$.contenido[3].inicio").value("2026-10-01T23:30:00-05:00"));
        }
    }

    @ParameterizedTest @CsvSource({"0,true", "60,true", "61,false", "120,false", "180,false"})
    void permisosClienteDosHoras(long minutos, boolean permitido) throws Exception {
        reloj.adelantar(Duration.ofMinutes(minutos));
        mvc.perform(detalle(cliente, r101.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos.cancelar").value(permitido))
                .andExpect(jsonPath("$.permisos.reprogramar").value(permitido))
                .andExpect(jsonPath("$.permisos.transiciones").isEmpty());
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)).param("estado", "CONFIRMADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].permisos.cancelar").value(permitido));
    }

    @ParameterizedTest @CsvSource({"164,false", "165,true", "179,true"})
    void transicionesBarberoConfirmadaAntesInicio(long minutos, boolean iniciar) throws Exception {
        reloj.adelantar(Duration.ofMinutes(minutos));
        mvc.perform(detalle(barbero.getUsuario(), r101.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.permisos.cancelar").value(false))
                .andExpect(jsonPath("$.permisos.reprogramar").value(false))
                .andExpect(lista("permisos.transiciones", iniciar ? List.of("EN_ATENCION") : List.of()));
        mvc.perform(detalle(barbero.getUsuario(), r102.getId())).andExpect(status().isOk())
                .andExpect(lista("permisos.transiciones", List.of("CONFIRMADA")));
    }

    @Test void telefonoSoloPersonalYDatosMinimos() throws Exception {
        mvc.perform(detalle(cliente, r101.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.telefono").doesNotExist()).andExpect(jsonPath("$.cliente.correo").doesNotExist())
                .andExpect(jsonPath("$.cliente.passwordHash").doesNotExist()).andExpect(jsonPath("$.codigo").value("BT-101"))
                .andExpect(jsonPath("$.duracionMin").value(30)).andExpect(jsonPath("$.precioRef").value(25.00));
        for (var personal : List.of(admin, barbero.getUsuario())) {
            mvc.perform(detalle(personal, r101.getId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cliente.telefono").value("999000001"));
            mvc.perform(agenda(personal)).andExpect(status().isOk())
                    .andExpect(jsonPath(personal.getRol() == Rol.ADMIN ? "$.contenido[1].cliente.telefono" : "$.contenido[0].cliente.telefono").value("999000001"));
        }
    }

    @Test void miasFiltrosOpcionalesEstadoDesdeHasta() throws Exception {
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)).param("estado", "COMPLETADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(historial.getId()));
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)).param("desde", "2026-10-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)).param("hasta", "2026-09-27"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test void paginacionOrdenTotalesYPaginaVacia() throws Exception {
        for (int pagina = 0; pagina < 3; pagina++) {
            mvc.perform(agenda(admin).param("pagina", "" + pagina).param("tamano", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.pagina").value(pagina))
                    .andExpect(jsonPath("$.tamano").value(2)).andExpect(jsonPath("$.totalElementos").value(4))
                    .andExpect(jsonPath("$.totalPaginas").value(2))
                    .andExpect(jsonPath("$.contenido.length()").value(pagina < 2 ? 2 : 0));
        }
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)).param("tamano", "1").param("pagina", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2)).andExpect(jsonPath("$.contenido[0].id").value(r101.getId()));
        mvc.perform(agenda(admin).param("clienteId", "999999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0)).andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @ParameterizedTest @CsvSource({"2026-01-01,2027-01-02", "2026-10-02,2026-10-01"})
    void rangoInvalido_400(String desde, String hasta) throws Exception {
        mvc.perform(get("/api/reservas").cookie(sesion(admin)).param("desde", desde).param("hasta", hasta))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @ParameterizedTest @ValueSource(strings = {"desde", "hasta", "ambas"})
    void faltanFechasObligatorias_400(String omitir) throws Exception {
        var peticion = get("/api/reservas").cookie(sesion(admin));
        if (omitir.equals("desde")) peticion.param("hasta", "2026-10-01");
        if (omitir.equals("hasta")) peticion.param("desde", "2026-10-01");
        mvc.perform(peticion).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @Test void limite366InclusivoSoloOperativoYRangoPropioInvertido() throws Exception {
        mvc.perform(get("/api/reservas").cookie(sesion(admin)).param("desde", "2026-01-01").param("hasta", "2027-01-01"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)).param("desde", "2026-01-01").param("hasta", "2027-01-02"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reservas/mias").cookie(sesion(cliente)).param("desde", "2026-10-02").param("hasta", "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("RANGO_FECHAS_INVALIDO"));
    }

    @ParameterizedTest @CsvSource({"pagina,-1", "tamano,0", "tamano,101", "pagina,texto", "estado,OTRO", "desde,01-10-2026", "hasta,2026-02-30"})
    void parametrosInvalidos_400(String parametro, String valor) throws Exception {
        for (String ruta : List.of("/api/reservas", "/api/reservas/mias")) {
            var peticion = get(ruta).cookie(sesion(ruta.endsWith("mias") ? cliente : admin));
            if (!parametro.equals("desde")) peticion.param("desde", "2026-10-01");
            if (!parametro.equals("hasta")) peticion.param("hasta", "2026-10-01");
            mvc.perform(peticion.param(parametro, valor)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"pagina", "tamano", "estado", "desde", "hasta", "barberoId", "servicioId", "clienteId"})
    void parametrosPresentesVacios_400(String parametro) throws Exception {
        var peticion = get("/api/reservas").cookie(sesion(admin));
        if (!parametro.equals("desde")) peticion.param("desde", "2026-10-01");
        if (!parametro.equals("hasta")) peticion.param("hasta", "2026-10-01");
        mvc.perform(peticion.param(parametro, " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @ParameterizedTest @ValueSource(strings = {"/api/reservas", "/api/reservas/mias", "/api/reservas/101"})
    void sinSesion_401(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test void matrizRolesYPasswordTemporal() throws Exception {
        mvc.perform(agenda(cliente)).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        for (var personal : List.of(admin, barbero.getUsuario())) {
            mvc.perform(get("/api/reservas/mias").cookie(sesion(personal))).andExpect(status().isForbidden());
        }
        cliente.cambiarPassword("hash-ficticio-no-utilizable", true, reloj.instant());
        usuarios.saveAndFlush(cliente);
        mvc.perform(detalle(cliente, r101.getId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CAMBIO_PASSWORD_REQUERIDO"));
    }

    @Test void pagina20SinNMasUno_conteoSeparadoYCuatroSelectHttp() throws Exception {
        // Relaciones distintas para que la caché de primer nivel no oculte N+1.
        for (int i = 0; i < 21; i++) {
            var propietario = datos.cliente("pagina" + i);
            var perfil = datos.barbero("pagina" + i);
            var contratado = servicios.saveAndFlush(new Servicio("Servicio " + i, "Ficticio", (short) 30,
                    BigDecimal.TEN, reloj.instant()));
            insertar(propietario, perfil, contratado, "2026-10-01", "12:00", EstadoReserva.CONFIRMADA);
        }
        for (var peticion : List.of(agenda(admin), agenda(barbero.getUsuario()),
                get("/api/reservas/mias").cookie(sesion(cliente)))) {
            SqlPruebas.limpiar();
            mvc.perform(peticion).andExpect(status().isOk());
            assertThat(SqlPruebas.sentencias()).hasSizeLessThanOrEqualTo(4);
        }
        SqlPruebas.limpiar();
        mvc.perform(agenda(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(20))
                .andExpect(jsonPath("$.totalElementos").value(25)).andExpect(jsonPath("$.totalPaginas").value(2));
        var sql = SqlPruebas.sentencias();
        assertThat(sql).hasSize(4); // Identidad, perfil de sesión, contenido con joins y count separado.
        assertThat(sql.stream().filter(s -> s.contains("from reserva") && !s.contains("count("))).singleElement()
                .satisfies(s -> assertThat(s).contains("join usuario", "join barbero", "join servicio", "offset", "fetch first"));
        assertThat(sql.stream().filter(s -> s.contains("count(") && s.contains("from reserva"))).hasSize(1);
        SqlPruebas.limpiar();
        mvc.perform(detalle(admin, r101.getId())).andExpect(status().isOk());
        assertThat(SqlPruebas.sentencias()).hasSize(3);
    }

    @Test void servicioMantieneGuardiasDeRolYLimiteDeFecha() {
        assertThatThrownBy(() -> consultas.mias(actor(admin), null, null, null, 0, 20))
                .isInstanceOf(pe.barberturno.common.error.NegocioException.class)
                .extracting(e -> ((pe.barberturno.common.error.NegocioException) e).codigo())
                .isEqualTo(pe.barberturno.common.error.ErrorCodigo.PROHIBIDO);
        assertThatThrownBy(() -> consultas.listar(actor(cliente), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1), null, null, null, null, 0, 20))
                .isInstanceOf(pe.barberturno.common.error.NegocioException.class)
                .extracting(e -> ((pe.barberturno.common.error.NegocioException) e).codigo())
                .isEqualTo(pe.barberturno.common.error.ErrorCodigo.PROHIBIDO);
        assertThatThrownBy(() -> consultas.mias(actor(cliente), null, null, LocalDate.MAX, 0, 20))
                .isInstanceOf(pe.barberturno.common.error.NegocioException.class)
                .extracting(e -> ((pe.barberturno.common.error.NegocioException) e).codigo())
                .isEqualTo(pe.barberturno.common.error.ErrorCodigo.RANGO_FECHAS_INVALIDO);
    }
}
