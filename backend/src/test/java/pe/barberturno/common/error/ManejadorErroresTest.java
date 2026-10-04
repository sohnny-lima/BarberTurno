package pe.barberturno.common.error;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManejadorErroresTest {
    private final ControladorPrueba controlador = new ControladorPrueba();
    private MockMvc mvc;

    @BeforeEach
    void prepararMvc_sinFiltrosDeSeguridad() {
        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new ManejadorErrores()).build();
    }

    @ParameterizedTest
    @EnumSource(ErrorCodigo.class)
    void negocio_cadaCodigo_conservaEstadoTituloYDetalle(ErrorCodigo codigo) throws Exception {
        controlador.error = new NegocioException(codigo, "Rechazo previsto.");
        comprobar(mvc.perform(get("/prueba/error")), codigo, "/prueba/error")
                .andExpect(jsonPath("$.title").value(codigo.titulo()))
                .andExpect(jsonPath("$.detail").value("Rechazo previsto."));
    }

    @Test
    void negocio_conReservas_incluyeIdsPublicos() throws Exception {
        controlador.error = new NegocioException(ErrorCodigo.CONFLICTO_CON_RESERVAS,
                "El cambio cruza reservas.", Map.of("reservas", List.of(100L, 101L)));
        comprobar(mvc.perform(get("/prueba/error")), ErrorCodigo.CONFLICTO_CON_RESERVAS, "/prueba/error")
                .andExpect(jsonPath("$.reservas[0]").value(100))
                .andExpect(jsonPath("$.reservas[1]").value(101));
    }

    @Test
    void validarCuerpo_invalido_incluyeCampoYMensajeSinValor() throws Exception {
        ResultActions respuesta = comprobar(mvc.perform(post("/prueba/cuerpo")
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\"}")),
                ErrorCodigo.VALIDACION, "/prueba/cuerpo")
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El nombre es obligatorio."));
        assertThat(respuesta.andReturn().getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class);
    }

    @Test
    void validarParametro_invalido_usaValidacionNativaMvc() throws Exception {
        ResultActions respuesta = comprobar(mvc.perform(get("/prueba/parametro").param("cantidad", "0")),
                ErrorCodigo.VALIDACION, "/prueba/parametro")
                .andExpect(jsonPath("$.errores[0].campo").value("cantidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La cantidad debe ser positiva."));
        assertThat(respuesta.andReturn().getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
    }

    @Test
    void validarRestricciones_invalido_incluyeCampos() throws Exception {
        try (var fabrica = Validation.buildDefaultValidatorFactory()) {
            controlador.error = new ConstraintViolationException(fabrica.getValidator().validate(new Entrada("")));
        }
        comprobar(mvc.perform(get("/prueba/error")), ErrorCodigo.VALIDACION, "/prueba/error")
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El nombre es obligatorio."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"nombre\":{\"SQL\":\"org.Exception\"}}"})
    void leerJson_malFormadoOTipoIncorrecto_devuelveMensajeGenerico(String cuerpo) throws Exception {
        comprobar(mvc.perform(post("/prueba/cuerpo").contentType(MediaType.APPLICATION_JSON).content(cuerpo)),
                ErrorCodigo.VALIDACION, "/prueba/cuerpo").andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    void leerParametro_tipoIncorrecto_devuelveMensajeGenerico() throws Exception {
        comprobar(mvc.perform(get("/prueba/parametro").param("cantidad", "SQL org.Exception")),
                ErrorCodigo.VALIDACION, "/prueba/parametro").andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    void leerParametro_obligatorioAusente_devuelveValidacion() throws Exception {
        comprobar(mvc.perform(get("/prueba/parametro")), ErrorCodigo.VALIDACION, "/prueba/parametro")
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    void metodoNoPermitido_enMvc_devuelve405ConProblemaHabitual() throws Exception {
        mvc.perform(post("/prueba/parametro"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(ErrorCodigo.VALIDACION.titulo()))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.detail").value("El método solicitado no está permitido."))
                .andExpect(jsonPath("$.instance").value("/prueba/parametro"))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("rechazosMvc")
    void clienteMvc_conEstado4xx_registraWarnSinTrazaNiDatos(Exception error, int estado) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ManejadorErrores.class);
        ListAppender<ILoggingEvent> captura = new ListAppender<>();
        captura.start();
        logger.addAppender(captura);
        try {
            controlador.error = error;
            var respuesta = mvc.perform(get("/prueba/error")).andExpect(status().is(estado));
            if (estado == 406) {
                respuesta.andExpect(content().string(""));
            } else {
                respuesta.andExpect(jsonPath("$.status").value(estado))
                        .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                        .andExpect(jsonPath("$.errores").isEmpty());
            }
            assertThat(captura.list).hasSize(1);
            var evento = captura.list.getFirst();
            assertThat(evento.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(evento.getThrowableProxy()).isNull();
            assertThat(evento.getFormattedMessage()).contains("HTTP " + estado)
                    .doesNotContain("secreto-ficticio", "persona@ejemplo.test", "Exception");
            assertThat(respuesta.andReturn().getResponse().getContentAsString())
                    .doesNotContain("secreto-ficticio", "persona@ejemplo.test", "Exception");
        } finally {
            logger.detachAppender(captura);
            captura.stop();
        }
    }

    static Stream<Arguments> rechazosMvc() {
        return Stream.of(
                Arguments.of(new HttpMediaTypeNotSupportedException("secreto-ficticio persona@ejemplo.test"), 415),
                Arguments.of(new HttpRequestMethodNotSupportedException("secreto-ficticio persona@ejemplo.test"), 405),
                Arguments.of(new HttpMediaTypeNotAcceptableException("secreto-ficticio persona@ejemplo.test"), 406));
    }

    @Test
    void representacionNoAceptable_conHtml_devuelve406SinCuerpoNiTipo() throws Exception {
        controlador.error = new HttpMediaTypeNotAcceptableException("Detalle interno ficticio");
        mvc.perform(get("/prueba/error").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Content-Type"));
    }

    @Test
    void clienteMvc_conEstado5xx_conservaErrorInterno() {
        var error = org.mockito.Mockito.spy(new HttpMediaTypeNotSupportedException("Detalle ficticio"));
        when(error.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        var respuesta = new ManejadorErrores().clienteMvc(error, new MockHttpServletRequest("POST", "/prueba/error"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody().getProperties()).containsEntry("codigo", "ERROR_INTERNO");
    }

    @Test
    void clienteMvc_sinErrorResponse_conservaErrorInterno() {
        var respuesta = new ManejadorErrores().clienteMvc(new IllegalStateException("Detalle ficticio"),
                new MockHttpServletRequest("GET", "/prueba/error"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody().getProperties()).containsEntry("codigo", "ERROR_INTERNO");
    }
    @ParameterizedTest
    @MethodSource("versiones")
    void versionDesactualizada_springOJpa_devuelveConflicto(Exception error) throws Exception {
        controlador.error = error;
        comprobar(mvc.perform(get("/prueba/error")), ErrorCodigo.VERSION_DESACTUALIZADA, "/prueba/error");
    }

    static Stream<Exception> versiones() {
        return Stream.of(new ObjectOptimisticLockingFailureException(Entrada.class, 100L),
                new OptimisticLockException("SQL org.Exception"));
    }

    @ParameterizedTest
    @MethodSource("integridad")
    void integridad_sqlStateYRestriccionExactos_traduceSinFiltrarDetalles(
            String sqlState, String restriccion, ErrorCodigo codigo) throws Exception {
        controlador.error = new DataIntegrityViolationException("SQL org.Exception " + restriccion,
                new RuntimeException("Causa intermedia", postgres(sqlState, restriccion)));
        ResultActions respuesta = comprobar(mvc.perform(get("/prueba/error")), codigo, "/prueba/error");
        assertThat(respuesta.andReturn().getResponse().getContentAsString()).doesNotContain(restriccion);
    }

    static Stream<Arguments> integridad() {
        return Stream.of(
                Arguments.of("23P01", "reserva_sin_solape_barbero", ErrorCodigo.FRANJA_NO_DISPONIBLE),
                Arguments.of("23P01", "reserva_sin_solape_cliente", ErrorCodigo.CLIENTE_CON_RESERVA_SOLAPADA),
                Arguments.of("23505", "usuario_correo_uk", ErrorCodigo.CORREO_DUPLICADO),
                Arguments.of("23505", "servicio_nombre_uk", ErrorCodigo.NOMBRE_DUPLICADO),
                Arguments.of("23514", "restriccion_desconocida", ErrorCodigo.CONFLICTO),
                Arguments.of("23505", "otra_unicidad", ErrorCodigo.CONFLICTO),
                Arguments.of("23P01", "otro_solape", ErrorCodigo.CONFLICTO),
                Arguments.of("23505", "reserva_sin_solape_barbero", ErrorCodigo.CONFLICTO),
                Arguments.of("23P01", "usuario_correo_uk", ErrorCodigo.CONFLICTO));
    }

    @Test
    void integridad_sinPostgres_noAnalizaTextoNiNombres() throws Exception {
        controlador.error = new DataIntegrityViolationException("SQL reserva_sin_solape_barbero usuario_correo_uk");
        comprobar(mvc.perform(get("/prueba/error")), ErrorCodigo.CONFLICTO, "/prueba/error");
    }

    @Test
    void integridad_sinMetadatosServidor_devuelveConflicto() throws Exception {
        controlador.error = new DataIntegrityViolationException("SQL", new PSQLException("SQL", null));
        comprobar(mvc.perform(get("/prueba/error")), ErrorCodigo.CONFLICTO, "/prueba/error");
    }

    @ParameterizedTest
    @MethodSource("bloqueos")
    void accesoDatos_timeoutODeadlock_enCualquierSubtipo_devuelveRecursoOcupado(Exception error) throws Exception {
        controlador.error = error;
        comprobar(mvc.perform(get("/prueba/error")), ErrorCodigo.RECURSO_OCUPADO, "/prueba/error");
    }

    static Stream<Exception> bloqueos() {
        return Stream.of("55P03", "40P01").flatMap(estado -> Stream.of(
                new DataIntegrityViolationException("SQL", postgres(estado, "no_publicar")),
                new PessimisticLockingFailureException("SQL", new RuntimeException(postgres(estado, "no_publicar"))),
                new DataAccessResourceFailureException("SQL", new SQLException("SQL", estado))));
    }

    @ParameterizedTest
    @MethodSource("otrosErrores")
    void excepcionesRestantes_traduceAlCatalogo(Exception error, ErrorCodigo codigo) throws Exception {
        controlador.error = error;
        comprobar(mvc.perform(get("/prueba/error")), codigo, "/prueba/error");
    }

    static Stream<Arguments> otrosErrores() {
        return Stream.of(
                Arguments.of(new AccessDeniedException("SQL org.Exception"), ErrorCodigo.PROHIBIDO),
                Arguments.of(new BadCredentialsException("SQL org.Exception"), ErrorCodigo.NO_AUTENTICADO),
                Arguments.of(new NoResourceFoundException(HttpMethod.GET, "/prueba/error", "SQL org.Exception"),
                        ErrorCodigo.NO_ENCONTRADO),
                Arguments.of(new IllegalStateException("SQL org.Exception"), ErrorCodigo.ERROR_INTERNO),
                Arguments.of(new DataAccessResourceFailureException("SQL org.Exception"), ErrorCodigo.ERROR_INTERNO));
    }

    @Test
    void errorImprevisto_registraTrazaSinMensajesNiDatosSensibles() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ManejadorErrores.class);
        ListAppender<ILoggingEvent> captura = new ListAppender<>();
        captura.start();
        logger.addAppender(captura);
        try {
            controlador.error = new IllegalStateException("secreto-ficticio JWT cookie persona@ejemplo.test",
                    new IllegalArgumentException("password-ficticio SELECT datos"));
            controlador.error.addSuppressed(new RuntimeException("otro-secreto-ficticio"));
            comprobar(mvc.perform(get("/prueba/error")), ErrorCodigo.ERROR_INTERNO, "/prueba/error");
            assertThat(captura.list).hasSize(1);
            String traza = ThrowableProxyUtil.asString(captura.list.getFirst().getThrowableProxy());
            assertThat(traza).contains("ManejadorErroresTest", "IllegalStateException", "IllegalArgumentException")
                    .doesNotContain("secreto-ficticio", "JWT", "cookie", "persona@ejemplo.test",
                            "password-ficticio", "SELECT datos");
        } finally {
            logger.detachAppender(captura);
            captura.stop();
        }
    }

    private ResultActions comprobar(ResultActions respuesta, ErrorCodigo codigo, String ruta) throws Exception {
        respuesta.andExpect(status().is(codigo.estado().value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(codigo.estado().value()))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(ruta))
                .andExpect(jsonPath("$.codigo").value(codigo.name()));
        MvcResult resultado = respuesta.andReturn();
        assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("Exception", "org.", "SQL", "reserva_sin_solape_barbero",
                        "reserva_sin_solape_cliente", "usuario_correo_uk", "servicio_nombre_uk", "no_publicar");
        return respuesta;
    }

    private static PSQLException postgres(String estado, String restriccion) {
        ServerErrorMessage servidor = mock(ServerErrorMessage.class);
        when(servidor.getSQLState()).thenReturn(estado);
        when(servidor.getConstraint()).thenReturn(restriccion);
        when(servidor.toString()).thenReturn("SQL org.Exception " + restriccion);
        return new PSQLException(servidor);
    }

    record Entrada(@NotBlank(message = "El nombre es obligatorio.") String nombre) { }

    // El perfil evita registrar el controlador en los contextos de integración.
    @RestController
    @Profile("pruebas-errores-standalone")
    static class ControladorPrueba {
        Exception error;

        @GetMapping("/prueba/error")
        void fallar() throws Exception { throw error; }

        @PostMapping("/prueba/cuerpo")
        void cuerpo(@Valid @RequestBody Entrada entrada) { }

        @GetMapping("/prueba/parametro")
        void parametro(@RequestParam @Min(value = 1, message = "La cantidad debe ser positiva.") int cantidad) { }
    }
}
