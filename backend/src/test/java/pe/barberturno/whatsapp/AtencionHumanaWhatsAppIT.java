package pe.barberturno.whatsapp;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.error.*;
import pe.barberturno.common.security.UsuarioAutenticado;
import pe.barberturno.conversation.*;
import pe.barberturno.customers.AnonimizacionFichas;
import pe.barberturno.users.Rol;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Ventana, presupuestos, retención, anonimización y carreras reales con PostgreSQL y Graph falso. */
class AtencionHumanaWhatsAppIT extends WhatsAppSalidaPruebaBase {
    @MockitoBean TextosConversacion textos;
    @Autowired DerivacionConversacion derivacion;
    @Autowired BandejaWhatsApp bandeja;
    @Autowired AnonimizacionFichas fichas;
    @Autowired SalidaWhatsApp salida;
    @Autowired EnviadorSalidaWhatsApp enviador;
    @Autowired RegistroEntradaWhatsApp entradas;
    @Autowired RetencionWhatsApp retencion;
    @Autowired ConversacionPropiedades propiedades;
    @Autowired pe.barberturno.reservations.ReservaConsultaService consultasReservas;
    long ficha;
    long c;

    @BeforeEach void prepararAtencion() {
        when(textos.derivada(anyString())).thenReturn("El equipo le atenderá por aquí.");
        when(textos.devuelta()).thenReturn("Puede continuar con el asistente.");
        ficha = ficha(a, WA_CLIENTE);
        c = crearConversacion(a, WA_CLIENTE);
        en(a, () -> sql.update("update conversacion set cliente_id=? where id=?", ficha, c));
    }

    @Test void derivarYDevolver_exigenTransaccion_yNoDerivaSinFicha() {
        assertThatThrownBy(() -> derivacion.derivar(c, MotivoDerivacion.PETICION))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> derivacion.devolver(c, OptionalLong.empty()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        long sinFicha = crearConversacion(a, "51999000999");
        assertThatThrownBy(() -> en(a, () -> derivacion.derivar(sinFicha, MotivoDerivacion.PETICION)))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.CONFLICTO));
    }

    @Test void modoHumano_descartaAsistente_sinConsumirPresupuestos_yEnviaSistemaYPersona() {
        long automatico = en(a, () -> salida.encolarTexto(c, "Menú anterior", OrigenMensaje.ASISTENTE, OptionalLong.empty()));
        en(a, () -> sql.update("update mensaje_whatsapp set intentos=2,inciertos=1 where id=?", automatico));
        en(a, () -> derivacion.derivar(c, MotivoDerivacion.PETICION));
        long personal = en(a, () -> bandeja.responder(c, "Respuesta ficticia", titular.getId()).id());
        servidorGraph.encolarExito("wamid.acuse");
        servidorGraph.encolarExito("wamid.persona");
        enviador.enviarLote();
        var descartado = filaMensaje(a, automatico);
        assertThat(descartado).containsEntry("estado", "FALLIDO").containsEntry("error_codigo", "MODO_HUMANO")
                .containsEntry("intentos", 2).containsEntry("inciertos", 1);
        assertThat(filaMensaje(a, personal)).containsEntry("estado", "ENVIADO").containsEntry("enviado_por", titular.getId());
        assertThat(en(a, () -> sql.queryForObject("select count(*) from mensaje_whatsapp where origen='SISTEMA' and estado='ENVIADO'", Integer.class))).isEqualTo(1);
        assertThat(en(a, () -> sql.queryForObject("select count(*) from notificacion", Integer.class))).isEqualTo(2);
    }

    @Test void cierreEntreEncoladoYEnvio_fallaPersona_sinAvisosAdicionales() {
        en(a, () -> sql.update("update conversacion set ultima_entrada_en=? where id=?",
                Timestamp.from(reloj.instant().minus(Duration.ofHours(24)).plusSeconds(360)), c));
        en(a, () -> derivacion.derivar(c, MotivoDerivacion.PETICION));
        long personal = en(a, () -> bandeja.responder(c, "Respuesta ficticia", titular.getId()).id());
        reloj.adelantar(Duration.ofMinutes(1));
        enviador.enviarLote();
        assertThat(filaMensaje(a, personal)).containsEntry("estado", "FALLIDO").containsEntry("error_codigo", "VENTANA_CERRADA");
        assertThat(en(a, () -> sql.queryForObject("select count(*) from notificacion", Integer.class))).isEqualTo(2);
    }

    @Test void metodosGenerales_rechazanPersona_paraNoPerderElActor() {
        assertThatThrownBy(() -> en(a, () -> salida.encolarTexto(c, "Respuesta", OrigenMensaje.PERSONA, OptionalLong.empty())))
                .isInstanceOfSatisfying(NegocioException.class, e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.VALIDACION));
        var interactivo = mapper.createObjectNode().put("type", "button");
        interactivo.putObject("body").put("text", "Respuesta");
        assertThatThrownBy(() -> en(a, () -> salida.encolarInteractivo(c, interactivo, OrigenMensaje.PERSONA, OptionalLong.empty())))
                .isInstanceOf(NegocioException.class);
    }

    @Test void retornoAutomatico_igualdadNoRetorna_respuestaRecienteImpide_yAislaDosNegocios() {
        long fb = ficha(b, WA_CLIENTE);
        long cb = crearConversacion(b, WA_CLIENTE);
        en(b, () -> sql.update("update conversacion set cliente_id=? where id=?", fb, cb));
        en(a, () -> derivacion.derivar(c, MotivoDerivacion.PETICION));
        en(b, () -> derivacion.derivar(cb, MotivoDerivacion.PETICION));
        reloj.adelantar(Duration.ofHours(24));
        assertThat(derivacion.devolverInactivas()).isZero();
        en(a, () -> sql.update("update conversacion set ultima_respuesta_personal_en=? where id=?", ahora(), c));
        reloj.adelantar(Duration.ofSeconds(1));
        assertThat(derivacion.devolverInactivas()).isEqualTo(1);
        assertThat(en(a, () -> sql.queryForObject("select modo from conversacion where id=?", String.class, c))).isEqualTo("HUMANO");
        assertThat(en(b, () -> sql.queryForObject("select modo from conversacion where id=?", String.class, cb))).isEqualTo("ASISTENTE");
        assertThat(en(b, () -> sql.queryForObject("select count(*) from mensaje_whatsapp", Integer.class))).isEqualTo(1);
    }

    @Test void retencion_contenidoSoloVencidoConFicha_yOperacionesTerminadas_noTocaPendientes() {
        long fb = ficha(b, WA_CLIENTE);
        long cb = crearConversacion(b, WA_CLIENTE);
        en(b, () -> sql.update("update conversacion set cliente_id=? where id=?", fb, cb));
        long viejoB = mensaje(b, cb, "RECIBIDO", reloj.instant().minus(Duration.ofDays(91)));
        long terminadaB = operacion(b, cb, fb, "ANULADA", Duration.ofDays(31));
        long sinFicha = crearConversacion(a, "51999000999");
        long viejo = mensaje(a, c, "RECIBIDO", reloj.instant().minus(Duration.ofDays(91)));
        long reciente = mensaje(a, c, "RECIBIDO", reloj.instant().minus(Duration.ofDays(89)));
        long pendiente = mensaje(a, c, "PENDIENTE", reloj.instant().minus(Duration.ofDays(91)));
        long ajenoSinFicha = mensaje(a, sinFicha, "RECIBIDO", reloj.instant().minus(Duration.ofDays(91)));
        long terminada = operacion(a, c, ficha, "ANULADA", Duration.ofDays(31));
        long vencida = operacion(a, c, ficha, "VIGENTE", Duration.ofDays(31));
        retencion.purgar();
        assertThat(filaMensaje(a, viejo).get("contenido").toString()).isEqualTo("{\"retirado\": true}");
        for (long id : List.of(reciente, pendiente, ajenoSinFicha)) assertThat(filaMensaje(a, id).get("contenido").toString()).contains("texto");
        assertThat(filaMensaje(a, pendiente)).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 0);
        assertThat(en(a, () -> sql.queryForObject("select count(*) from operacion_pendiente where id=?", Integer.class, terminada))).isZero();
        assertThat(en(a, () -> sql.queryForObject("select estado from operacion_pendiente where id=?", String.class, vencida))).isEqualTo("CADUCADA");
        assertThat(filaMensaje(b, viejoB).get("contenido").toString()).isEqualTo("{\"retirado\": true}");
        assertThat(en(b, () -> sql.queryForObject("select count(*) from operacion_pendiente where id=?", Integer.class, terminadaB))).isZero();
    }

    @Test void retencion_sinPlazos_noRetiraContenidoNiOperaciones_aunqueSeanAntiguos() {
        var sinPlazos = new ConversacionPropiedades(propiedades.privacidad(), propiedades.inactividad(), propiedades.vigenciaOperacion(),
                new ConversacionPropiedades.Retencion(false, Duration.ofHours(1), 200, Duration.ofDays(30), null, null, null));
        var desactivada = new RetencionWhatsApp(entityManager, canales, ejecutor, gestor, sinPlazos, reloj);
        long viejo = mensaje(a, c, "RECIBIDO", reloj.instant().minus(Duration.ofDays(400)));
        long terminada = operacion(a, c, ficha, "ANULADA", Duration.ofDays(400));
        desactivada.purgar();
        assertThat(filaMensaje(a, viejo).get("contenido").toString()).contains("texto");
        assertThat(en(a, () -> sql.queryForObject("select count(*) from operacion_pendiente where id=?", Integer.class, terminada))).isEqualTo(1);
    }

    @Test void retencion_caducaEnLaIgualdadDeExpiracion_yNoPurgaHastaElPlazo() {
        long id = operacion(a, c, ficha, "VIGENTE", Duration.ZERO);
        retencion.purgar();
        assertThat(en(a, () -> sql.queryForObject("select estado from operacion_pendiente where id=?", String.class, id))).isEqualTo("CADUCADA");
        assertThat(en(a, () -> sql.queryForObject("select actualizado_en from operacion_pendiente where id=?", Timestamp.class, id)).toInstant()).isEqualTo(reloj.instant());
    }

    @Test void retornoAutomatico_omiteBloqueadas_yRevalidaActividadDelPersonal() throws Exception {
        en(a, () -> derivacion.derivar(c, MotivoDerivacion.PETICION));
        reloj.adelantar(Duration.ofHours(25));
        CountDownLatch bloqueada = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var hilos = Executors.newSingleThreadExecutor()) {
            var personal = hilos.submit(() -> en(a, () -> {
                entityManager.createNativeQuery("select id from conversacion where id=:id for update", Long.class)
                        .setParameter("id", c).getSingleResult();
                bloqueada.countDown(); esperar(liberar);
                return sql.update("update conversacion set ultima_respuesta_personal_en=? where id=?", ahora(), c);
            }));
            assertThat(bloqueada.await(5, TimeUnit.SECONDS)).isTrue();
            try { assertThat(derivacion.devolverInactivas()).isZero(); }
            finally { liberar.countDown(); }
            personal.get(5, TimeUnit.SECONDS);
        } finally { liberar.countDown(); }
        assertThat(derivacion.devolverInactivas()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"PENDIENTE", "CONFIRMADA", "EN_ATENCION"})
    void anonimizar_cualquierReservaActivaInclusoPasada_rechazaSinCambios(String estado) {
        long reserva = crearReserva(a, WA_CLIENTE);
        en(a, () -> sql.update("update reserva set estado=?,inicio=?,fin=? where id=?", estado,
                Timestamp.from(reloj.instant().minus(Duration.ofDays(1))),
                Timestamp.from(reloj.instant().minus(Duration.ofDays(1)).plusSeconds(1800)), reserva));
        assertThatThrownBy(() -> anonimizar()).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.CONFLICTO_CON_RESERVAS));
        assertThat(en(a, () -> sql.queryForObject("select wa_id from cliente where id=?", String.class, ficha))).isEqualTo(WA_CLIENTE);
    }

    @Test void anonimizar_conCuentaOConPendiente409_sinBorrarNiCambiarFicha() {
        en(a, () -> sql.update("update cliente set usuario_id=? where id=?", titular.getId(), ficha));
        assertThatThrownBy(this::anonimizar).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.CONFLICTO));
        // El vínculo de cuenta es inmutable desde Java; el fixture usa otra ficha sin cuenta para el pendiente.
        en(a, () -> sql.update("update cliente set usuario_id=null where id=?", ficha));
        mensaje(a, c, "PENDIENTE", reloj.instant());
        assertThatThrownBy(this::anonimizar).isInstanceOfSatisfying(NegocioException.class,
                e -> assertThat(e.codigo()).isEqualTo(ErrorCodigo.CONFLICTO));
        assertThat(en(a, () -> sql.queryForObject("select anonimizada_en from cliente where id=?", Timestamp.class, ficha))).isNull();
    }

    @Test void anonimizar_borraAvisosOperacionesYMensajesConReserva_peroConservaReservaAuditoriaYCuerpoCrudo() {
        long reserva = crearReserva(a, WA_CLIENTE);
        en(a, () -> sql.update("update reserva set estado='CANCELADA' where id=?", reserva));
        en(a, () -> sql.update("""
                insert into auditoria_reserva(reserva_id,actor_cliente_id,accion,estado_nuevo,datos_nuevos,creado_en)
                values (?,?,'CANCELAR','CANCELADA','{}',?)
                """, reserva, ficha, ahora()));
        en(a, () -> derivacion.derivar(c, MotivoDerivacion.PETICION));
        en(a, () -> sql.update("update mensaje_whatsapp set estado='FALLIDO',error_codigo='VENTANA_CERRADA' where conversacion_id=?", c));
        // Mensaje de resultado terminado, ligado al historial de la reserva.
        long ligado = en(a, () -> salida.encolarTexto(c, "Resultado", OrigenMensaje.SISTEMA, OptionalLong.of(reserva)));
        en(a, () -> sql.update("update mensaje_whatsapp set estado='FALLIDO',error_codigo='VENTANA_CERRADA' where id=?", ligado));
        operacion(a, c, ficha, "VIGENTE", Duration.ZERO);
        long evento = jdbc.queryForObject("""
                insert into evento_whatsapp_entrante(clave_idempotencia,tipo,phone_number_id,cuerpo,estado,proximo_intento,recibido_en)
                values ('evento-privacidad','MENSAJE',?,'{"dato":"crudo ficticio"}','PENDIENTE',?,?) returning id
                """, Long.class, NUMERO_A, ahora(), ahora());
        anonimizar(); anonimizar();
        for (String tabla : List.of("conversacion", "operacion_pendiente", "mensaje_whatsapp", "notificacion")) {
            assertThat(en(a, () -> sql.queryForObject("select count(*) from " + tabla, Integer.class))).as(tabla).isZero();
        }
        assertThat(en(a, () -> sql.queryForObject("select count(*) from reserva where id=?", Integer.class, reserva))).isEqualTo(1);
        assertThat(en(a, () -> sql.queryForObject("select count(*) from auditoria_reserva where reserva_id=?", Integer.class, reserva))).isEqualTo(1);
        var historial = en(a, () -> consultasReservas.auditoria(
                new UsuarioAutenticado(titular.getId(), Rol.ADMIN, Optional.empty(), false), reserva));
        assertThat(historial).hasSize(1);
        assertThat(historial.getFirst().actorNombre()).isEqualTo("Cliente eliminado");
        assertThat(jdbc.queryForObject("select cuerpo::text from evento_whatsapp_entrante where id=?", String.class, evento)).contains("crudo ficticio");
        // Nueva entrada del mismo remitente obtiene conversación nueva, sin ficha.
        long nueva = en(a, () -> entradas.registrar(entrada()).conversacionId());
        assertThat(nueva).isNotEqualTo(c);
        assertThat(en(a, () -> sql.queryForObject("select cliente_id from conversacion where id=?", Long.class, nueva))).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"responder", "devolver", "anonimizar"})
    void entregaBloqueada_serializaLasEscrituras_sinInterbloqueo(String accion) throws Exception {
        en(a, () -> derivacion.derivar(c, MotivoDerivacion.PETICION));
        // Termina el acuse para que anonimizar pueda finalizar después de la entrega.
        en(a, () -> sql.update("update mensaje_whatsapp set estado='FALLIDO',error_codigo='VENTANA_CERRADA' where conversacion_id=?", c));
        CountDownLatch retenida = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        var pid = new java.util.concurrent.atomic.AtomicInteger();
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var entrega = hilos.submit(() -> en(a, () -> {
                entradas.registrar(entrada());
                retenida.countDown(); esperar(liberar);
                return null;
            }));
            assertThat(retenida.await(5, TimeUnit.SECONDS)).isTrue();
            var escritura = hilos.submit(() -> {
                if (accion.equals("anonimizar")) { anonimizar(pid); return null; }
                return en(a, () -> {
                    pid.set(sql.queryForObject("select pg_backend_pid()", Integer.class));
                    if (accion.equals("responder")) return bandeja.responder(c, "Respuesta", titular.getId());
                    bandeja.devolver(c, titular.getId()); return null;
                });
            });
            try {
                esperarBloqueo(pid);
            } finally { liberar.countDown(); }
            entrega.get(10, TimeUnit.SECONDS); escritura.get(10, TimeUnit.SECONDS);
        } finally { liberar.countDown(); }
        if (accion.equals("anonimizar")) assertThat(en(a, () -> sql.queryForObject("select count(*) from conversacion where id=?", Integer.class, c))).isZero();
        else assertThat(en(a, () -> sql.queryForObject("select modo from conversacion where id=?", String.class, c)))
                .isEqualTo(accion.equals("devolver") ? "ASISTENTE" : "HUMANO");
    }

    @Test void anonimizar_frenteAEnviador_conPendienteRechaza_yTrasEnvioPuedeBorrar() throws Exception {
        long mensaje = en(a, () -> salida.encolarTexto(c, "Respuesta sistema", OrigenMensaje.SISTEMA, OptionalLong.empty()));
        CountDownLatch bloqueado = new CountDownLatch(1), liberar = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var envio = hilos.submit(() -> en(a, () -> {
                entityManager.createNativeQuery("select id from mensaje_whatsapp where id=:id for update", Long.class)
                        .setParameter("id", mensaje).getSingleResult();
                bloqueado.countDown(); esperar(liberar); return null;
            }));
            assertThat(bloqueado.await(5, TimeUnit.SECONDS)).isTrue();
            var borrado = hilos.submit(() -> assertThatThrownBy(this::anonimizar).isInstanceOf(NegocioException.class));
            borrado.get(5, TimeUnit.SECONDS); liberar.countDown(); envio.get(5, TimeUnit.SECONDS);
        } finally { liberar.countDown(); }
        enviador.enviarLote();
        assertThat(filaMensaje(a, mensaje)).containsEntry("estado", "ENVIADO");
        anonimizar();
    }

    @ParameterizedTest @ValueSource(strings = {"responder", "devolver", "anonimizar"})
    void membresiaRevocadaDuranteEspera_seRevalidaAntesDeEscribir(String accion) throws Exception {
        en(a, () -> derivacion.derivar(c, MotivoDerivacion.PETICION));
        en(a, () -> sql.update("update mensaje_whatsapp set estado='FALLIDO',error_codigo='VENTANA_CERRADA' where conversacion_id=?", c));
        CountDownLatch bloqueada = new CountDownLatch(1), liberar = new CountDownLatch(1);
        var pid = new java.util.concurrent.atomic.AtomicInteger();
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var entrega = hilos.submit(() -> en(a, () -> {
                entradas.registrar(entrada()); bloqueada.countDown(); esperar(liberar); return null;
            }));
            assertThat(bloqueada.await(5, TimeUnit.SECONDS)).isTrue();
            var escritura = hilos.submit(() -> {
                if (accion.equals("anonimizar")) { anonimizar(pid); return null; }
                return en(a, () -> {
                    pid.set(sql.queryForObject("select pg_backend_pid()", Integer.class));
                    if (accion.equals("responder")) return bandeja.responder(c, "Respuesta", titular.getId());
                    bandeja.devolver(c, titular.getId()); return null;
                });
            });
            try {
                esperarBloqueo(pid);
                en(a, () -> sql.update("update membresia set estado='REVOCADA',motivo_revocacion='DECISION_GESTOR' where negocio_id=? and usuario_id=?", a, titular.getId()));
            } finally { liberar.countDown(); }
            entrega.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> escritura.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(NegocioException.class).satisfies(e ->
                        assertThat(((NegocioException) e.getCause()).codigo()).isEqualTo(ErrorCodigo.PROHIBIDO));
        } finally { liberar.countDown(); }
        assertThat(en(a, () -> sql.queryForObject("select modo from conversacion where id=?", String.class, c))).isEqualTo("HUMANO");
        assertThat(en(a, () -> sql.queryForObject("select wa_id from cliente where id=?", String.class, ficha))).isEqualTo(WA_CLIENTE);
        assertThat(en(a, () -> sql.queryForObject("select count(*) from mensaje_whatsapp where origen='PERSONA'", Integer.class))).isZero();
    }

    private <T> T en(long negocio, Supplier<T> accion) { return ejecutor.ejecutar(negocio, () -> new TransactionTemplate(gestor).execute(tx -> accion.get())); }
    private long ficha(long negocio, String wa) { return en(negocio, () -> sql.queryForObject("insert into cliente(nombre,wa_id,creado_en,actualizado_en) values ('Ficha',?,?,?) returning id", Long.class, wa, ahora(), ahora())); }
    private Timestamp ahora() { return Timestamp.from(reloj.instant()); }
    private long mensaje(long negocio, long conversacion, String estado, java.time.Instant creado) {
        return en(negocio, () -> sql.queryForObject("""
                insert into mensaje_whatsapp(conversacion_id,direccion,origen,tipo,contenido,estado,proximo_intento,creado_en,actualizado_en)
                values (?,?,?,'TEXTO','{"texto":"Mensaje ficticio"}',?,?,?,?) returning id
                """, Long.class, conversacion, estado.equals("RECIBIDO") ? "ENTRANTE" : "SALIENTE",
                estado.equals("RECIBIDO") ? "CLIENTE" : "SISTEMA", estado, ahora(), Timestamp.from(creado), Timestamp.from(creado)));
    }
    private long operacion(long negocio, long conversacion, long f, String estado, Duration edad) {
        Timestamp fecha = Timestamp.from(reloj.instant().minus(edad));
        return en(negocio, () -> sql.queryForObject("""
                insert into operacion_pendiente(conversacion_id,cliente_id,tipo,datos,nonce,estado,expira_en,creado_en,actualizado_en)
                values (?,?,'CREAR','{}',?,?,?,?,?) returning id
                """, Long.class, conversacion, f, new byte[16], estado, fecha, fecha, fecha));
    }
    private void anonimizar() { anonimizar(null); }
    private void anonimizar(java.util.concurrent.atomic.AtomicInteger pid) {
        var anterior = SecurityContextHolder.getContext();
        var sesion = SecurityContextHolder.createEmptyContext();
        sesion.setAuthentication(new UsernamePasswordAuthenticationToken(
                new UsuarioAutenticado(titular.getId(), Rol.ADMIN, Optional.empty(), false), "", List.of()));
        SecurityContextHolder.setContext(sesion);
        try { en(a, () -> {
            if (pid != null) pid.set(sql.queryForObject("select pg_backend_pid()", Integer.class));
            fichas.anonimizar(ficha); return null;
        }); }
        finally { SecurityContextHolder.setContext(anterior); }
    }
    private void esperarBloqueo(java.util.concurrent.atomic.AtomicInteger pid) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (pid.get() > 0 && Boolean.TRUE.equals(sql.queryForObject(
                    "select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid.get()))) return;
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("PostgreSQL no registró la espera por la conversación.");
    }
    private MensajeEntrante entrada() { return new MensajeEntrante(1L, a, WA_CLIENTE, OptionalLong.of(ficha), "Perfil ficticio",
            "wamid.entrada-ficticia", "text", reloj.instant(), mapper.createObjectNode().put("body", "Mensaje ficticio")); }
    private static void esperar(CountDownLatch cerrojo) {
        try { if (!cerrojo.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Se agotó la espera de prueba."); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
