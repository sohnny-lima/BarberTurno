package pe.barberturno.whatsapp;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.ContextoNegocio;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.customers.ClienteRepository;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.MembresiaRepository;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.support.JdbcNegocioPruebas;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import pe.barberturno.users.UsuarioRepository;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comparte reloj, limpieza y JDBC transaccional con las bases existentes. Estas son de paquete;
 * la prueba necesita además cuatro conexiones para solapar dos entregas reales y sus reclamos.
 */
@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1", "barberturno.admin.correo=",
        "barberturno.admin.password=", "barberturno.admin.nombre=", "barberturno.whatsapp.procesador.lote=1"})
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class ProcesadorEventosWhatsAppIT {
    static final String WA = "51999000001";
    static final String NUMERO_A = "10001";
    @Autowired ProcesadorEventosWhatsApp procesador;
    @Autowired ColaEventosWhatsApp cola;
    @Autowired CanalWhatsAppRepository canales;
    @Autowired DirectorioNegocios directorio;
    @Autowired ContextoNegocio contexto;
    @Autowired PlatformTransactionManager gestor;
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcNegocioPruebas sql;
    @Autowired Environment entorno;
    @Autowired RelojAjustable reloj;
    @Autowired JsonMapper mapper;
    @Autowired UsuarioRepository usuarios;
    @Autowired MembresiaRepository membresias;
    @Autowired ClienteRepository clientes;
    @MockitoSpyBean EjecutorEnNegocio ejecutor;
    @MockitoBean ReceptorMensajesWhatsApp mensajes;
    @MockitoBean ReceptorEstadosWhatsApp estados;
    long a;
    long b;

    @BeforeEach void preparar() throws Exception {
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();
        // El propietario solo prepara las filas de negocio, igual que PeticionNegocioPruebaBase.
        try (var conexion = DriverManager.getConnection(entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"), entorno.getRequiredProperty("spring.flyway.password"));
                var sentencia = conexion.prepareStatement("""
                        INSERT INTO negocio(slug,nombre,creado_en,actualizado_en) VALUES (?, ?, ?, ?)
                        """)) {
            assertThat(conexion.getCatalog()).isEqualTo("barberturno_v2_test");
            for (String slug : List.of("a", "b")) {
                sentencia.setString(1, slug); sentencia.setString(2, "Negocio ficticio " + slug);
                sentencia.setTimestamp(3, Timestamp.from(reloj.instant()));
                sentencia.setTimestamp(4, Timestamp.from(reloj.instant())); sentencia.executeUpdate();
            }
        }
        a = directorio.buscar("a").orElseThrow(); b = directorio.buscar("b").orElseThrow();
        var titular = usuarios.saveAndFlush(new Usuario("Titular ficticio", "titular@ejemplo.test", "999000001",
                "hash-ficticio-no-utilizable", Rol.CLIENTE, reloj.instant(), reloj.instant()));
        for (long id : new long[]{a, b}) {
            membresias.save(new Membresia(id, titular.getId(), RolNegocio.PROPIETARIO, reloj.instant()));
        }
        canales.save(new CanalWhatsApp(a, "20001", NUMERO_A, "+51999000001", reloj.instant()));
        canales.save(new CanalWhatsApp(b, "20002", "10002", "+51999000002", reloj.instant()));
        clearInvocations(ejecutor);
    }

    @AfterEach void limpiar() {
        assertThat(contexto.delimitado()).isFalse();
        LimpiezaBaseDatos.limpiar(sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {"noRegistrado", "desconectado", "suspendido", "sinPropietario", "cuentaInactiva"})
    void descarte_noOperativo_noAbreContextoNiLlamaPuertos(String caso) {
        String numero = caso.equals("noRegistrado") ? "99999" : NUMERO_A;
        if (caso.equals("desconectado")) sql.update("update canal_whatsapp set estado='DESCONECTADO' where negocio_id=?", a);
        if (caso.equals("suspendido")) sql.update("update negocio set estado='SUSPENDIDO' where id=?", a);
        if (caso.equals("sinPropietario")) sql.update("""
                update membresia set estado='REVOCADA', motivo_revocacion='PLATAFORMA', actualizado_en=? where negocio_id=?
                """, Timestamp.from(reloj.instant()), a);
        if (caso.equals("cuentaInactiva")) sql.update("update usuario set activo=false where correo='titular@ejemplo.test'");
        long id = mensaje("m:descarte", numero, WA, "123");
        assertThat(procesador.procesarLote()).isEqualTo(1);
        var fila = fila(id);
        assertThat(fila.get("estado")).isEqualTo("DESCARTADO");
        assertThat(fila.get("motivo")).isEqualTo(switch (caso) {
            case "noRegistrado" -> "NUMERO_NO_REGISTRADO";
            case "desconectado" -> "CANAL_DESCONECTADO";
            default -> "NEGOCIO_NO_OPERATIVO";
        });
        assertThat(fila.get("negocio_id")).isEqualTo(caso.equals("noRegistrado") ? null : a);
        assertThat(((Timestamp) fila.get("procesado_en")).toInstant()).isEqualTo(reloj.instant());
        verifyNoInteractions(mensajes, estados, ejecutor);
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"+51999000001", "123456789012345678901", "abc", " 123", "١٢٣"})
    void remitenteInvalido_enMensajeYEstado_descartaSinContexto(String waId) {
        long mensaje = mensaje("m:invalido", NUMERO_A, waId, null);
        long estado = estado("s:invalido:read", waId, "read", null);
        assertThat(procesador.procesarLote() + procesador.procesarLote()).isEqualTo(2);
        for (long id : new long[]{mensaje, estado}) {
            assertThat(fila(id)).containsEntry("estado", "DESCARTADO").containsEntry("motivo", "REMITENTE_INVALIDO")
                    .containsEntry("negocio_id", a);
        }
        verifyNoInteractions(mensajes, estados, ejecutor);
    }

    @Test void mismoWaIdEnDosNegocios_entregaSoloLaFichaDelNumeroReceptor() {
        long fichaA = ficha(a, WA, "999000001");
        long fichaB = ficha(b, WA, "999000002");
        long id = mensaje("m:aislado", NUMERO_A, WA, "123");
        clearInvocations(ejecutor);
        doAnswer(invocacion -> {
            MensajeEntrante recibido = invocacion.getArgument(0);
            assertThat(contexto.negocioActual()).hasValue(a);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(sql.queryForObject("select current_user", String.class)).isEqualTo("barberturno_v2_app");
            assertThat(recibido.negocioId()).isEqualTo(a);
            assertThat(recibido.clienteId()).hasValue(fichaA).isNotEqualTo(OptionalLong.of(fichaB));
            assertThat(recibido.nombrePerfil()).isEqualTo("Perfil ficticio privado");
            assertThat(recibido.wamid()).isEqualTo("wamid.ficticio");
            assertThat(recibido.tipo()).isEqualTo("text");
            assertThat(recibido.enviadoEn()).isEqualTo(Instant.ofEpochSecond(123));
            assertThat(recibido.dato().path("text").path("body").asString()).isEqualTo("Texto ficticio privado");
            return null;
        }).when(mensajes).recibir(any());
        assertThat(procesador.procesarLote()).isEqualTo(1);
        assertThat(fila(id)).containsEntry("estado", "PROCESADO").containsEntry("negocio_id", a);
        verify(mensajes).recibir(any()); verifyNoInteractions(estados);
    }

    @Test void sinWaId_noVinculaPorTelefonoNiCreaOModificaFichas() {
        ficha(a, null, WA);
        ficha(b, WA, "999000002");
        var antesA = fichas(a); var antesB = fichas(b);
        mensaje("m:sinFicha", NUMERO_A, WA, "123");
        assertThat(procesador.procesarLote()).isEqualTo(1);
        var captura = org.mockito.ArgumentCaptor.forClass(MensajeEntrante.class);
        verify(mensajes).recibir(captura.capture());
        assertThat(captura.getValue().clienteId()).isEmpty();
        assertThat(fichas(a)).isEqualTo(antesA); assertThat(fichas(b)).isEqualTo(antesB);
    }

    @Test void claveDuplicada_soloInsertaUnaFilaYEntregaUnaVez() {
        long id = mensaje("m:duplicado", NUMERO_A, WA, "123");
        assertThat(insertar("m:duplicado", "MENSAJE", NUMERO_A, cuerpo(WA, "123", false))).isZero();
        assertThat(procesador.procesarLote()).isEqualTo(1);
        assertThat(procesador.procesarLote()).isZero();
        assertThat(sql.queryForObject("select count(*) from evento_whatsapp_entrante", Integer.class)).isEqualTo(1);
        verify(mensajes).recibir(argThat(m -> m.eventoId() == id));
    }

    @Test void procesadoresConcurrentes_conBarreraYReceptorLento_entreganCadaEventoUnaVez() throws Exception {
        long primero = mensaje("m:concurrente1", NUMERO_A, WA, "123");
        long segundo = mensaje("m:concurrente2", NUMERO_A, WA, "123");
        var entregas = new CopyOnWriteArrayList<Long>();
        var barrera = new CyclicBarrier(2);
        doAnswer(invocacion -> {
            MensajeEntrante recibido = invocacion.getArgument(0);
            entregas.add(recibido.eventoId());
            barrera.await(15, TimeUnit.SECONDS);
            return null;
        }).when(mensajes).recibir(any());
        try (var hilos = Executors.newFixedThreadPool(2)) {
            var uno = hilos.submit(() -> { int n = procesador.procesarLote(); assertThat(contexto.delimitado()).isFalse(); return n; });
            var dos = hilos.submit(() -> { int n = procesador.procesarLote(); assertThat(contexto.delimitado()).isFalse(); return n; });
            assertThat(uno.get(30, TimeUnit.SECONDS) + dos.get(30, TimeUnit.SECONDS)).isEqualTo(2);
        }
        assertThat(entregas).containsExactlyInAnyOrder(primero, segundo);
        assertThat(procesador.procesarLote()).isZero();
        assertThat(fila(primero)).containsEntry("estado", "PROCESADO").containsEntry("intentos", 1);
        assertThat(fila(segundo)).containsEntry("estado", "PROCESADO").containsEntry("intentos", 1);
    }

    @Test void reclamoVencido_elProcesadorLoRecuperaConSegundoIntento() {
        long id = mensaje("m:recuperacion", NUMERO_A, WA, "123");
        assertThat(reclamar()).singleElement().satisfies(e -> assertThat(e.intentos()).isEqualTo((short) 1));
        assertThat(procesador.procesarLote()).isZero();
        reloj.adelantar(Duration.ofSeconds(61));
        assertThat(procesador.procesarLote()).isEqualTo(1);
        assertThat(fila(id)).containsEntry("estado", "PROCESADO").containsEntry("intentos", 2);
        verify(mensajes).recibir(any());
    }

    @Test void reclamoVencido_conEntregaBloqueada_skipLockedEvitaOtraEntrega() throws Exception {
        long id = mensaje("m:bloqueado", NUMERO_A, WA, "123");
        var dentro = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        doAnswer(invocacion -> {
            dentro.countDown();
            assertThat(continuar.await(15, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(mensajes).recibir(any());
        try (var hilo = Executors.newSingleThreadExecutor()) {
            var entrega = hilo.submit(procesador::procesarLote);
            try {
                assertThat(dentro.await(15, TimeUnit.SECONDS)).isTrue();
                reloj.adelantar(Duration.ofSeconds(61));
                assertThat(procesador.procesarLote()).isZero();
            } finally { continuar.countDown(); }
            assertThat(entrega.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(fila(id)).containsEntry("estado", "PROCESADO").containsEntry("intentos", 1);
        verify(mensajes).recibir(any());
    }

    @Test void fichaAntigua_noBloqueaNiTerminaNiReprogramaElSegundoReclamo() {
        long id = mensaje("m:ficha", NUMERO_A, WA, "123");
        var primero = reclamar().getFirst();
        reloj.adelantar(Duration.ofSeconds(61));
        var segundo = reclamar().getFirst();
        assertThat(segundo.intentos()).isEqualTo((short) 2);
        var antes = fila(id);
        sql.enTransaccion(() -> {
            assertThat(cola.bloquear(id, primero.intentos())).isEmpty();
            assertThat(cola.procesar(id, primero.intentos(), a, reloj.instant())).isFalse();
            assertThat(cola.descartar(id, primero.intentos(), a, MotivoEvento.REMITENTE_INVALIDO, reloj.instant())).isFalse();
            assertThat(cola.fallar(id, primero.intentos(), reloj.instant(), reloj.instant(), false)).isFalse();
            assertThat(cola.fallar(id, primero.intentos(), reloj.instant(), reloj.instant(), true)).isFalse();
            return null;
        });
        assertThat(fila(id)).isEqualTo(antes);
        sql.enTransaccion(() -> {
            assertThat(cola.bloquear(id, segundo.intentos())).isPresent();
            assertThat(cola.procesar(id, segundo.intentos(), a, reloj.instant())).isTrue(); return null;
        });
    }

    @Test void falloDelPuerto_revierteEfectosYReintentaHastaError_sinDatosPersonalesEnLogs(CapturedOutput salida) {
        long ficha = ficha(a, WA, "999000001");
        var antes = fichas(a);
        long id = mensaje("m:fallo", NUMERO_A, WA, "123");
        doAnswer(invocacion -> {
            jdbc.update("update cliente set nombre='Cambio que debe revertirse' where id=?", ficha);
            throw new IllegalStateException("Texto ficticio privado " + WA + " Perfil ficticio privado");
        }).when(mensajes).recibir(any());
        for (int intento = 1; intento <= 5; intento++) {
            Instant falloEn = reloj.instant();
            assertThat(procesador.procesarLote()).isEqualTo(intento == 5 ? 1 : 0);
            assertThat(fichas(a)).isEqualTo(antes);
            var fila = fila(id);
            assertThat(fila.get("intentos")).isEqualTo(intento);
            var espera = PoliticaReintentos.espera(intento, Duration.ofSeconds(5), Duration.ofMinutes(5));
            assertThat(((Timestamp) fila.get("proximo_intento")).toInstant()).isEqualTo(falloEn.plus(espera));
            assertThat(procesador.procesarLote()).isZero();
            if (intento < 5) {
                assertThat(fila).containsEntry("estado", "PENDIENTE").containsEntry("motivo", null).containsEntry("procesado_en", null);
                reloj.adelantar(espera);
            } else {
                assertThat(fila).containsEntry("estado", "ERROR").containsEntry("motivo", "FALLO_PROCESAMIENTO");
                assertThat(((Timestamp) fila.get("procesado_en")).toInstant()).isEqualTo(falloEn);
            }
        }
        verify(mensajes, times(5)).recibir(any());
        assertThat(salida.getAll()).contains("Evento WhatsApp " + id + ": FALLO_PROCESAMIENTO.")
                .doesNotContain(WA, "Texto ficticio privado", "Perfil ficticio privado", "Cambio que debe revertirse");
    }

    @Test void eventoConProximoIntentoFuturo_noSeReclama() {
        long id = mensaje("m:futuro", NUMERO_A, WA, "123");
        sql.update("update evento_whatsapp_entrante set proximo_intento=? where id=?", Timestamp.from(reloj.instant().plusSeconds(1)), id);
        assertThat(procesador.procesarLote()).isZero();
        assertThat(fila(id)).containsEntry("intentos", 0);
        verifyNoInteractions(mensajes, estados);
    }

    @Test void avisoDeEstado_entregaWamidEstadoWaIdYFechaAlPuertoCorrecto() {
        long id = estado("s:estado:read", WA, "read", "123");
        doAnswer(invocacion -> {
            EstadoEntrante aviso = invocacion.getArgument(0);
            assertThat(contexto.negocioActual()).hasValue(a);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(aviso.eventoId()).isEqualTo(id); assertThat(aviso.negocioId()).isEqualTo(a);
            assertThat(aviso.wamid()).isEqualTo("wamid.ficticio"); assertThat(aviso.estado()).isEqualTo("read");
            assertThat(aviso.waId()).isEqualTo(WA); assertThat(aviso.enEstadoDesde()).isEqualTo(Instant.ofEpochSecond(123));
            assertThat(aviso.dato().path("status").asString()).isEqualTo("read"); return null;
        }).when(estados).recibir(any());
        assertThat(procesador.procesarLote()).isEqualTo(1);
        verify(estados).recibir(any()); verifyNoInteractions(mensajes);
        assertThat(fila(id)).containsEntry("estado", "PROCESADO").containsEntry("negocio_id", a);
    }

    @Test void avisoDeEstado_conCanalDesconectadoOSuspendido_seEntregaSinDescartar() {
        sql.update("update canal_whatsapp set estado='DESCONECTADO' where negocio_id=?", a);
        sql.update("update negocio set estado='SUSPENDIDO' where id=?", a);
        long id = estado("s:estado:desconectado", WA, "delivered", "123");

        assertThat(procesador.procesarLote()).isEqualTo(1);

        assertThat(fila(id)).containsEntry("estado", "PROCESADO").containsEntry("negocio_id", a);
        verify(estados).recibir(argThat(e -> e.eventoId() == id && e.negocioId() == a));
    }

    @Test void avisoDeEstado_conNumeroNoRegistrado_descartaSinContexto() {
        var cuerpo = cuerpo(WA, "123", true);
        ((tools.jackson.databind.node.ObjectNode) cuerpo.path("dato")).put("status", "delivered");
        insertar("s:estado:noRegistrado", "ESTADO", "99999", cuerpo);
        long id = sql.queryForObject("select id from evento_whatsapp_entrante where clave_idempotencia='s:estado:noRegistrado'", Long.class);

        assertThat(procesador.procesarLote()).isEqualTo(1);

        assertThat(fila(id)).containsEntry("estado", "DESCARTADO").containsEntry("motivo", "NUMERO_NO_REGISTRADO");
        verifyNoInteractions(estados);
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"abc", "1.5", "999999999999999999999999999999", "9223372036854775807"})
    void timestampAusenteOInvalido_enAmbosPuertos_usaRecibidoEn(String timestamp) {
        mensaje("m:fecha", NUMERO_A, WA, timestamp);
        estado("s:fecha:read", WA, "read", timestamp);
        reloj.adelantar(Duration.ofMinutes(3));
        assertThat(procesador.procesarLote() + procesador.procesarLote()).isEqualTo(2);
        verify(mensajes).recibir(argThat(m -> m.enviadoEn().equals(RelojAjustable.INICIAL)));
        verify(estados).recibir(argThat(e -> e.enEstadoDesde().equals(RelojAjustable.INICIAL)));
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, 1790000000})
    void timestampNumericoEnSegundosUnix_seConvierteSinUsarElRelojDeProcesamiento(long segundos) {
        var cuerpo = cuerpo(WA, null, false);
        ((tools.jackson.databind.node.ObjectNode) cuerpo.path("dato")).put("timestamp", segundos);
        assertThat(insertar("m:fechaNumerica", "MENSAJE", NUMERO_A, cuerpo)).isEqualTo(1);
        assertThat(procesador.procesarLote()).isEqualTo(1);
        verify(mensajes).recibir(argThat(m -> m.enviadoEn().equals(Instant.ofEpochSecond(segundos))));
    }

    private long ficha(long negocio, String waId, String telefono) {
        return ejecutor.ejecutar(negocio, () -> sql.queryForObject("""
                insert into cliente(nombre,telefono,wa_id,activo,creado_en,actualizado_en)
                values ('Ficha ficticia', ?, ?, true, ?, ?) returning id
                """, Long.class, telefono, waId, Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant())));
    }

    private List<Map<String, Object>> fichas(long negocio) {
        return ejecutor.ejecutar(negocio, () -> sql.queryForList("select * from cliente order by id"));
    }

    private List<EventoEntrante> reclamar() {
        return sql.enTransaccion(() -> cola.reclamar(reloj.instant(), reloj.instant().plusSeconds(60), 1));
    }

    private Map<String, Object> fila(long id) {
        return sql.queryForMap("select * from evento_whatsapp_entrante where id=?", id);
    }

    private long mensaje(String clave, String numero, String waId, String timestamp) {
        assertThat(insertar(clave, "MENSAJE", numero, cuerpo(waId, timestamp, false).put("phoneNumberId", numero))).isEqualTo(1);
        return sql.queryForObject("select id from evento_whatsapp_entrante where clave_idempotencia=?", Long.class, clave);
    }

    private long estado(String clave, String waId, String estado, String timestamp) {
        var cuerpo = cuerpo(waId, timestamp, true);
        ((tools.jackson.databind.node.ObjectNode) cuerpo.path("dato")).put("status", estado);
        assertThat(insertar(clave, "ESTADO", NUMERO_A, cuerpo)).isEqualTo(1);
        return sql.queryForObject("select id from evento_whatsapp_entrante where clave_idempotencia=?", Long.class, clave);
    }

    private tools.jackson.databind.node.ObjectNode cuerpo(String waId, String timestamp, boolean estado) {
        var cuerpo = mapper.createObjectNode().put("phoneNumberId", NUMERO_A);
        cuerpo.putObject("contacto").put("waId", waId).put("nombre", "Perfil ficticio privado");
        var dato = cuerpo.putObject("dato").put("id", "wamid.ficticio");
        if (waId != null) dato.put(estado ? "recipient_id" : "from", waId);
        if (timestamp != null) dato.put("timestamp", timestamp);
        if (!estado) { dato.put("type", "text"); dato.putObject("text").put("body", "Texto ficticio privado"); }
        return cuerpo;
    }

    private int insertar(String clave, String tipo, String numero, tools.jackson.databind.JsonNode cuerpo) {
        return sql.update("""
                insert into evento_whatsapp_entrante(clave_idempotencia,tipo,phone_number_id,cuerpo,recibido_en,proximo_intento)
                values (?, ?, ?, cast(? as jsonb), ?, ?) on conflict (clave_idempotencia) do nothing
                """, clave, tipo, numero, mapper.writeValueAsString(cuerpo), Timestamp.from(reloj.instant()), Timestamp.from(reloj.instant()));
    }
}
