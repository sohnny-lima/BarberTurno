package pe.barberturno.reservations;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.audit.AuditoriaRepository;
import pe.barberturno.audit.dto.AuditoriaDto;
import pe.barberturno.common.error.*;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.conversation.*;
import pe.barberturno.reporting.ReporteService;
import pe.barberturno.support.*;
import pe.barberturno.whatsapp.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Prueba el bloque X con PostgreSQL real y el procesador de entrega hasta su commit. */
@SpringBootTest(properties = {"barberturno.admin.correo=", "barberturno.admin.password=",
        "barberturno.admin.nombre=", "barberturno.seguridad.cookie-secure=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConversacionOperacionesIT extends ReservaPruebaBase {
    @Autowired OperacionesConversacion operaciones;
    @Autowired ConsultasConversacion consultas;
    @Autowired AuditoriaRepository auditorias;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EjecutorEnNegocio ejecutor;
    @Autowired EntityManager entidades;
    @Autowired SalidaWhatsApp salida;
    @Autowired ProcesadorEventosWhatsApp procesador;
    @Autowired CanalWhatsAppRepository canales;
    @Autowired ReporteService reportes;
    @MockitoBean ReceptorMensajesWhatsApp receptor;
    long ficha;
    long conversacion;
    static final String WA = "51999000001";

    @BeforeEach void prepararConversacion() {
        jdbc.update("update negocio set limite_reservas_activas=3, confirmacion_manual=false where id=1");
        ficha = ficha(WA);
        conversacion = conversacion(ficha, WA);
    }

    @AfterEach void restaurarReglas() {
        jdbc.update("update negocio set limite_reservas_activas=3, confirmacion_manual=false where id=1");
    }

    long ficha(String wa) {
        return jdbc.queryForObject("""
                insert into cliente(nombre, wa_id, privacidad_aceptada_en, creado_en, actualizado_en)
                values ('Cliente WhatsApp ficticio', ?, ?, ?, ?) returning id
                """, Long.class, wa, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
    }

    long conversacion(long fichaId, String wa) {
        return jdbc.queryForObject("""
                insert into conversacion(wa_id, cliente_id, ultima_entrada_en, creado_en, actualizado_en, estado)
                values (?, ?, ?, ?, ?, '{"paso":"RESUMEN"}') returning id
                """, Long.class, wa, fichaId, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
    }

    <T> T tx(Supplier<T> accion) { return new TransactionTemplate(gestor).execute(s -> accion.get()); }
    Propuesta.Creada proponer(String hora) {
        var resultado = tx(() -> operaciones.proponerCreacion(conversacion, ficha, servicio.getId(), barbero.getId(), instante(hora)));
        assertThat(resultado).isInstanceOf(Propuesta.Creada.class);
        return (Propuesta.Creada) resultado;
    }
    ResultadoOperacion confirmar(Propuesta.Creada p) {
        return tx(() -> operaciones.confirmar(conversacion, ficha, p.operacionId(), p.nonce()));
    }
    ResultadoOperacion.Ejecutada ejecutar(String hora) { return (ResultadoOperacion.Ejecutada) confirmar(proponer(hora)); }
    int[] conteos() { return new int[]{cantidad("reserva"), cantidad("auditoria_reserva"), cantidad("notificacion")}; }
    void rechazada(ResultadoOperacion resultado, ErrorCodigo codigo) {
        assertThat(resultado).isEqualTo(new ResultadoOperacion.Rechazada(codigo));
    }

    @Test void crear_repetirDespuesDeCancelar_reproduceInstantaneaSinOtraReserva() {
        var p = proponer("10:00");
        assertThat(p.nonce().matches("[A-Za-z0-9_-]{22}")).isTrue();
        var primera = (ResultadoOperacion.Ejecutada) confirmar(p);
        var fila = jdbc.queryForMap("select canal, creada_por, cliente_id from reserva where id=?", primera.cita().reservaId());
        assertThat(fila).containsEntry("canal", "WHATSAPP").containsEntry("creada_por", null).containsEntry("cliente_id", ficha);
        assertThat(cantidad("notificacion")).isEqualTo(1);
        assertThat(jdbc.queryForMap("select actor_id, actor_cliente_id from auditoria_reserva"))
                .containsEntry("actor_id", null).containsEntry("actor_cliente_id", ficha);
        assertThat(tx(() -> auditorias.buscarHistorial(primera.cita().reservaId()).stream().map(AuditoriaDto::desde).toList()))
                .singleElement().extracting(AuditoriaDto::actorNombre).isEqualTo("Cliente WhatsApp ficticio");
        assertThat(jdbc.queryForObject("select estado::text from conversacion where id=?", String.class, conversacion)).isEqualTo("{}");
        servicioReservas.cancelarFicha(ficha, primera.cita().reservaId(), primera.cita().version());
        var cantidades = conteos();
        var repetida = (ResultadoOperacion.Ejecutada) confirmar(p);
        assertThat(repetida.repetida()).isTrue();
        assertThat(repetida.cita()).isEqualTo(primera.cita());
        assertThat(conteos()).containsExactly(cantidades);
    }

    @Test void dobleConfirmacionConcurrente_unaReservaYUnResultadoRepetido() throws Exception {
        var p = proponer("10:00");
        var resultados = carrera(List.of(() -> confirmar(p), () -> confirmar(p)));
        assertThat(resultados).allMatch(ResultadoOperacion.Ejecutada.class::isInstance);
        assertThat(resultados.stream().map(r -> ((ResultadoOperacion.Ejecutada) r).repetida()).toList())
                .containsExactlyInAnyOrder(false, true);
        assertThat(conteos()).containsExactly(1, 1, 1);
    }

    @Test void caducada_enElLimiteExacto_noEscribeReservaYDespuesEstaUsada() {
        var p = proponer("10:00");
        reloj.adelantar(Duration.ofMinutes(10));
        assertThat(confirmar(p)).isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.CADUCADA));
        assertThat(confirmar(p)).isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.USADA));
        assertThat(conteos()).containsExactly(0, 0, 0);
    }

    @Test void identidadNonceYNegocioAjenos_noRevelanNiCambianOperacionInclusoEjecutada() throws Exception {
        var p = proponer("10:00");
        long otraFicha = ficha("51999000002");
        long otraConversacion = conversacion(otraFicha, "51999000002");
        for (boolean ejecutada : List.of(false, true)) {
            if (ejecutada) confirmar(p);
            int[] antes = conteos();
            var estado = jdbc.queryForObject("select estado from operacion_pendiente where id=?", String.class, p.operacionId());
            assertThat(tx(() -> operaciones.confirmar(otraConversacion, otraFicha, p.operacionId(), p.nonce())))
                    .isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
            assertThat(tx(() -> operaciones.confirmar(conversacion, otraFicha, p.operacionId(), p.nonce())))
                    .isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
            assertThat(tx(() -> operaciones.confirmar(conversacion, ficha, p.operacionId(), "AAAAAAAAAAAAAAAAAAAAAA")))
                    .isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
            assertThat(tx(() -> operaciones.anular(conversacion, ficha, p.operacionId(), "invalido")))
                    .isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
            assertThat(conteos()).containsExactly(antes);
            assertThat(jdbc.queryForObject("select estado from operacion_pendiente where id=?", String.class, p.operacionId())).isEqualTo(estado);
        }
        long reservaId = jdbc.queryForObject("select id from reserva", Long.class);
        long b = negocioB();
        ejecutor.ejecutar(b, () -> tx(() -> {
            assertThat(operaciones.confirmar(conversacion, ficha, p.operacionId(), p.nonce())).isEqualTo(
                    new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
            long fichaB = ficha(WA);
            long conversacionB = conversacion(fichaB, WA);
            assertThat(operaciones.confirmar(conversacionB, fichaB, p.operacionId(), p.nonce())).isEqualTo(
                    new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
            assertThat(consultas.cita(ficha, reservaId)).isEmpty();
            return null;
        }));
    }

    @Test void sustituida_siConYSinVigente_anulacionYPropuestaInvalidaConservaVigente() {
        var p = proponer("10:00");
        var nueva = proponer("11:00");
        assertThat(confirmar(p)).isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.SUSTITUIDA));
        assertThat(tx(() -> operaciones.proponerCreacion(conversacion, ficha, servicio.getId(), barbero.getId(), instante("08:00"))))
                .isEqualTo(new Propuesta.Rechazada(ErrorCodigo.FUERA_DE_HORARIO));
        assertThat(tx(() -> operaciones.confirmarVigente(conversacion, ficha))).isInstanceOf(ResultadoOperacion.Ejecutada.class);
        assertThat(tx(() -> operaciones.confirmarVigente(conversacion, ficha)))
                .isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.NO_ENCONTRADA));
        var otra = proponer("12:00");
        assertThat(tx(() -> operaciones.anular(conversacion, ficha, otra.operacionId(), otra.nonce())))
                .isEqualTo(new ResultadoOperacion.Anulada());
        assertThat(confirmar(otra)).isEqualTo(new ResultadoOperacion.NoVigente(MotivoNoVigente.SUSTITUIDA));
        proponer("13:00");
        assertThat(tx(() -> operaciones.anularVigente(conversacion))).isTrue();
        assertThat(tx(() -> operaciones.anularVigente(conversacion))).isFalse();
        assertThat(nueva.operacionId()).isPositive();
    }

    @ParameterizedTest @ValueSource(strings = {"precio", "duracion", "profesional"})
    void precondicionesDeCreacionCambiadas_rechazaAntesDeEscribir(String cambio) {
        var p = proponer("10:00");
        switch (cambio) {
            case "precio" -> jdbc.update("update servicio set precio=precio+1 where id=?", servicio.getId());
            case "duracion" -> jdbc.update("update servicio set duracion_min=40 where id=?", servicio.getId());
            default -> jdbc.update("update barbero set activo=false where id=?", barbero.getId());
        }
        var r = confirmar(p);
        rechazada(r, cambio.equals("profesional") ? ErrorCodigo.RECURSO_INACTIVO : ErrorCodigo.VERSION_DESACTUALIZADA);
        assertThat(conteos()).containsExactly(0, 0, 0);
        assertThat(jdbc.queryForObject("select estado from operacion_pendiente", String.class)).isEqualTo("RECHAZADA");
    }

    @Test void reprogramacion_cancelacionConVersionYPlazo_preservanReferenciasYCanalWeb() {
        var web = servicioReservas.crear(cmd(barbero, "10:00"), actor(cliente));
        long propia = clientes.buscarIdPorUsuario(cliente.getId()).orElseThrow();
        jdbc.update("update cliente set wa_id='51999000003' where id=?", propia);
        long conv = conversacion(propia, "51999000003");
        var p = (Propuesta.Creada) tx(() -> operaciones.proponerReprogramacion(conv, propia, web.id(), instante("11:00")));
        jdbc.update("update servicio set activo=false, precio=99, duracion_min=60 where id=?", servicio.getId());
        var nueva = (ResultadoOperacion.Ejecutada) tx(() -> operaciones.confirmar(conv, propia, p.operacionId(), p.nonce()));
        assertThat(nueva.cita().inicio()).isEqualTo(instante("11:00"));
        assertThat(nueva.cita().precio()).isEqualByComparingTo(web.precioRef());
        assertThat(jdbc.queryForObject("select canal from reserva", String.class)).isEqualTo("WEB");
        assertThat(tx(() -> consultas.franjasCambio(propia, web.id(), LocalDate.of(2026, 10, 1)))).isNotEmpty();
        var cancelacion = (Propuesta.Creada) tx(() -> operaciones.proponerCancelacion(conv, propia, web.id()));
        servicioReservas.reprogramarFicha(propia, web.id(), nueva.cita().version(), instante("12:00"));
        rechazada(tx(() -> operaciones.confirmar(conv, propia, cancelacion.operacionId(), cancelacion.nonce())), ErrorCodigo.VERSION_DESACTUALIZADA);
        var finalP = (Propuesta.Creada) tx(() -> operaciones.proponerCancelacion(conv, propia, web.id()));
        var finalR = (ResultadoOperacion.Ejecutada) tx(() -> operaciones.confirmar(conv, propia, finalP.operacionId(), finalP.nonce()));
        assertThat(finalR.cita().estado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test void reglasFicha_propiedadSolapeLimiteEstadoYVentana_noEscribenAlRechazar() {
        var cita = ejecutar("10:00").cita();
        long ajena = ficha("51999000004");
        var antes = conteos();
        assertThatThrownBy(() -> servicioReservas.cancelarFicha(ajena, cita.reservaId(), cita.version()))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.NO_ENCONTRADO));
        assertThat(tx(() -> consultas.cita(ajena, cita.reservaId()))).isEmpty();
        assertThat(tx(() -> consultas.franjasCambio(ajena, cita.reservaId(), LocalDate.of(2026, 10, 1)))).isEmpty();
        assertThat(conteos()).containsExactly(antes);
        var otro = perfil("segundo");
        assertThat(tx(() -> operaciones.proponerCreacion(conversacion, ficha, servicio.getId(), otro.getId(), instante("10:00"))))
                .isEqualTo(new Propuesta.Rechazada(ErrorCodigo.CLIENTE_CON_RESERVA_SOLAPADA));
        ejecutar("11:00"); ejecutar("12:00");
        assertThat(tx(() -> operaciones.proponerCreacion(conversacion, ficha, servicio.getId(), barbero.getId(), instante("13:00"))))
                .isEqualTo(new Propuesta.Rechazada(ErrorCodigo.LIMITE_RESERVAS_ACTIVAS));
        antes = conteos();
        reloj.adelantar(Duration.between(reloj.instant(), instante("08:00").plusSeconds(1)));
        assertThat(tx(() -> operaciones.proponerCancelacion(conversacion, ficha, cita.reservaId())))
                .isEqualTo(new Propuesta.Rechazada(ErrorCodigo.FUERA_DE_POLITICA));
        assertThat(tx(() -> operaciones.proponerReprogramacion(conversacion, ficha, cita.reservaId(), instante("14:00"))))
                .isEqualTo(new Propuesta.Rechazada(ErrorCodigo.FUERA_DE_POLITICA));
        assertThat(conteos()).containsExactly(antes);
    }

    @Test void consultas_cualquieraUnaPorInicio_yDiezProximas_conDtoWebYTransicionSinCuenta() throws Exception {
        perfil("segundo");
        var franjas = tx(() -> consultas.franjas(servicio.getId(), OptionalLong.empty(), LocalDate.of(2026, 10, 1)));
        assertThat(franjas).isNotEmpty();
        assertThat(franjas.stream().map(FranjaOpcion::inicio)).doesNotHaveDuplicates();
        jdbc.update("update negocio set limite_reservas_activas=15 where id=1");
        for (int i=0; i<11; i++) {
            servicioReservas.crearFicha(ficha, servicio.getId(), barbero.getId(), instante("09:00").plusSeconds(i*1800),
                    servicio.getDuracionMin(), servicio.getPrecio());
        }
        var citas = tx(() -> consultas.citasFuturas(ficha));
        assertThat(citas).hasSize(10);
        assertThat(citas.stream().map(CitaOpcion::inicio)).isSorted();
        var admin = admin("operador");
        var primera = citas.getFirst();
        reloj.adelantar(Duration.between(reloj.instant(), instante("09:00").minusSeconds(900)));
        var dto = servicioReservas.transicionar(primera.reservaId(),
                new pe.barberturno.reservations.dto.TransicionarReservaDto(EstadoReserva.EN_ATENCION, primera.version()), actor(admin));
        assertThat(reportes.resumen(actor(admin), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null, null).total()).isEqualTo(11);
        try (var libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(
                reportes.exportar(actor(admin), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null, null)))) {
            assertThat(libro.getNumberOfSheets()).isPositive();
        }
        assertThat(dto.cliente().id()).isNull();
        assertThat(dto.cliente().fichaId()).isEqualTo(ficha);
    }

    @Test void v2cp05_diezCreacionesSimultaneasWebYFicha_unExito() throws Exception {
        List<Callable<String>> acciones = new ArrayList<>();
        for (int i=0; i<10; i++) {
            if (i%2 == 0) {
                var cuenta = datos.cliente("hilo"+i);
                acciones.add(() -> resultado(() -> servicioReservas.crear(cmd(barbero, "10:00"), actor(cuenta))));
            } else {
                long id = ficha("5198800000"+i);
                acciones.add(() -> resultado(() -> servicioReservas.crearFicha(id, servicio.getId(), barbero.getId(), instante("10:00"),
                        servicio.getDuracionMin(), servicio.getPrecio())));
            }
        }
        var resultados = carrera(acciones);
        assertThat(resultados.stream().filter("OK"::equals)).hasSize(1);
        assertThat(resultados.stream().filter("FRANJA_NO_DISPONIBLE"::equals)).hasSize(9);
        assertThat(conteos()[0]).isEqualTo(1);
        assertThat(conteos()[1]).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void entregaReal_rechazoConfirmaEventoYOutbox_falloTecnicoRevierteTodo(boolean tecnico) {
        var p = proponer("10:00");
        if (!tecnico) servicioReservas.crear(cmd(barbero, "10:00"), actor(cliente));
        int[] antes = conteos();
        jdbc.update("insert into canal_whatsapp(negocio_id,waba_id,phone_number_id,numero_visible,creado_en,actualizado_en) values (1, '20001', '10001', '+51999000001', ?, ?)", Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
        var propietario = admin("titular");
        // El fixture de ADMIN de v1 crea una membresía de propietario activa.
        assertThat(propietario).isNotNull();
        doAnswer(inv -> {
            MensajeEntrante mensaje = inv.getArgument(0);
            assertThat(mensaje.clienteId().orElseThrow()).isEqualTo(ficha);
            // Este rechazo cruza el proxy de DisponibilidadService y debe permitir confirmar la entrega.
            assertThat(consultas.franjas(Long.MAX_VALUE, OptionalLong.empty(), LocalDate.of(2026, 10, 1))).isEmpty();
            var r = operaciones.confirmar(conversacion, ficha, p.operacionId(), p.nonce());
            if (!tecnico) rechazada(r, ErrorCodigo.FRANJA_NO_DISPONIBLE);
            salida.encolarTexto(conversacion, tecnico ? "Resultado ficticio" : "Franja ocupada", OrigenMensaje.SISTEMA,
                    r instanceof ResultadoOperacion.Ejecutada e ? OptionalLong.of(e.cita().reservaId()) : OptionalLong.empty());
            if (tecnico) throw new IllegalStateException("Fallo técnico ficticio después de la reserva y la outbox");
            return null;
        }).when(receptor).recibir(any());
        long evento = jdbc.queryForObject("""
                insert into evento_whatsapp_entrante(clave_idempotencia,tipo,phone_number_id,cuerpo,proximo_intento,recibido_en)
                values ('m:operacion', 'MENSAJE','10001',cast(? as jsonb),?,?) returning id
                """, Long.class, "{\"dato\":{\"from\":\""+WA+"\",\"id\":\"m:operacion\",\"type\":\"text\"}}",
                Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
        assertThat(procesador.procesarLote()).isEqualTo(tecnico ? 0 : 1);
        assertThat(conteos()).containsExactly(antes);
        assertThat(jdbc.queryForObject("select estado from evento_whatsapp_entrante where id=?", String.class, evento))
                .isEqualTo(tecnico ? "PENDIENTE" : "PROCESADO");
        assertThat(jdbc.queryForObject("select estado from operacion_pendiente where id=?", String.class, p.operacionId()))
                .isEqualTo(tecnico ? "VIGENTE" : "RECHAZADA");
        assertThat(cantidad("mensaje_whatsapp")).isEqualTo(tecnico ? 0 : 1);
        assertThat(cantidad("cliente")).isEqualTo(2);
        if (tecnico) assertThat(jdbc.queryForObject("select estado->>'paso' from conversacion where id=?", String.class, conversacion))
                .isEqualTo("RESUMEN");
    }

    @Test void confirmacionManual_yVentanaExacta_yEstadoParaReprogramar() {
        jdbc.update("update negocio set confirmacion_manual=true where id=1");
        var pendiente = ejecutar("10:00").cita();
        assertThat(pendiente.estado()).isEqualTo(EstadoReserva.PENDIENTE);
        int[] antes = conteos();
        assertThat(tx(() -> operaciones.proponerReprogramacion(conversacion, ficha, pendiente.reservaId(), instante("11:00"))))
                .isEqualTo(new Propuesta.Rechazada(ErrorCodigo.TRANSICION_INVALIDA));
        assertThat(conteos()).containsExactly(antes);
        reloj.adelantar(Duration.between(reloj.instant(), instante("08:00")));
        var cancelar = (Propuesta.Creada) tx(() -> operaciones.proponerCancelacion(conversacion, ficha, pendiente.reservaId()));
        var cancelada = (ResultadoOperacion.Ejecutada) confirmar(cancelar);
        assertThat(cancelada.cita().estado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(jdbc.queryForObject("select version from reserva where id=?", Integer.class, pendiente.reservaId()))
                .isEqualTo(pendiente.version()+1);
    }

    @Test void web_adminConsultaReprogramaYCancelaUnaCitaSinCuenta_sinAvisoAlCliente() throws Exception {
        var cita = ejecutar("10:00").cita();
        var admin = admin("administracion");
        var consulta = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/disponibilidad")
                .param("servicioId", servicio.getId().toString()).param("fecha", "2026-10-01")
                .param("barberoId", barbero.getId().toString()).param("excluirReservaId", Long.toString(cita.reservaId()));
        mvc.perform(consulta.cookie(sesion(admin)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var reprogramada = servicioReservas.reprogramar(cita.reservaId(),
                new pe.barberturno.reservations.dto.ReprogramarReservaDto(
                        pe.barberturno.common.time.TiempoNegocio.aLima(instante("11:00")), null, cita.version(), "Cambio solicitado"), actor(admin));
        assertThat(reprogramada.cliente().id()).isNull();
        assertThat(reprogramada.cliente().fichaId()).isEqualTo(ficha);
        var cancelada = servicioReservas.cancelar(cita.reservaId(),
                new pe.barberturno.reservations.dto.CancelarReservaDto(reprogramada.version(), "Cambio solicitado"), actor(admin));
        assertThat(cancelada.estado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(jdbc.queryForObject("select canal from reserva", String.class)).isEqualTo("WHATSAPP");
        assertThat(conteos()).containsExactly(1, 3, 3);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void ventanaVenceEntreResumenYConfirmacion_rechazoSinEscriturasDeDominio(boolean cancelar) {
        var cita = ejecutar("10:00").cita();
        reloj.adelantar(Duration.between(reloj.instant(), instante("08:00")));
        var p = (Propuesta.Creada) tx(() -> cancelar
                ? operaciones.proponerCancelacion(conversacion, ficha, cita.reservaId())
                : operaciones.proponerReprogramacion(conversacion, ficha, cita.reservaId(), instante("12:00")));
        int[] antes = conteos();
        reloj.adelantar(Duration.ofSeconds(1));
        rechazada(confirmar(p), ErrorCodigo.FUERA_DE_POLITICA);
        assertThat(conteos()).containsExactly(antes);
        assertThat(tx(() -> consultas.cita(ficha, cita.reservaId()).orElseThrow().inicio())).isEqualTo(cita.inicio());
    }

    @Test void fichaPrecargada_desactivadaMientrasEspera_seRefrescaTrasBloqueo() throws Exception {
        CountDownLatch desactivada = new CountDownLatch(1), precargada = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var primero = hilos.submit(() -> tx(() -> {
                jdbc.update("update cliente set activo=false where id=?", ficha);
                desactivada.countDown();
                esperar(liberar);
                return null;
            }));
            assertThat(desactivada.await(10, TimeUnit.SECONDS)).isTrue();
            var segundo = hilos.submit(() -> tx(() -> {
                assertThat(clientes.findById(ficha).orElseThrow().isActivo()).isTrue();
                precargada.countDown();
                return resultado(() -> servicioReservas.crearFicha(ficha, servicio.getId(), barbero.getId(), instante("10:00"),
                        servicio.getDuracionMin(), servicio.getPrecio()));
            }));
            try { assertThat(precargada.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { liberar.countDown(); }
            primero.get(15, TimeUnit.SECONDS);
            assertThat(segundo.get(15, TimeUnit.SECONDS)).isEqualTo("NO_ENCONTRADO");
        } finally { liberar.countDown(); }
        assertThat(conteos()).containsExactly(0, 0, 0);
    }

    private pe.barberturno.users.Usuario admin(String nombre) {
        return DatosPrueba.guardarPersonalV1(usuarios, membresias, clientes,
                new pe.barberturno.users.Usuario("Administrador ficticio", nombre + "@ejemplo.test", null,
                        "hash-ficticio", pe.barberturno.users.Rol.ADMIN, null, reloj.instant()));
    }
    private String resultado(Supplier<?> accion) {
        try { accion.get(); return "OK"; }
        catch (NegocioException e) { return e.codigo().name(); }
    }
    private <T> List<T> carrera(List<Callable<T>> acciones) throws Exception {
        CountDownLatch listos = new CountDownLatch(acciones.size()), salida = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(acciones.size())) {
            List<Future<T>> futuros = new ArrayList<>();
            for (var accion : acciones) futuros.add(hilos.submit(() -> { listos.countDown(); esperar(salida); return accion.call(); }));
            try { assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue(); } finally { salida.countDown(); }
            List<T> resultados = new ArrayList<>();
            for (var f : futuros) resultados.add(f.get(30, TimeUnit.SECONDS));
            return resultados;
        }
    }
    private static void esperar(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Espera agotada"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private long negocioB() throws Exception {
        var entorno = jdbc.entorno();
        try (var c = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"));
                var q = c.createStatement()) {
            assertThat(c.getCatalog()).isEqualTo("barberturno_v2_test");
            try (var r = q.executeQuery("insert into negocio(slug,nombre,creado_en,actualizado_en) values ('b','Negocio B ficticio',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) returning id")) {
                r.next(); return r.getLong(1);
            }
        }
    }
}
