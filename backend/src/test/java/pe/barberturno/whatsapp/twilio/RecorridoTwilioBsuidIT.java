package pe.barberturno.whatsapp.twilio;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.auth.JwtService;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.customers.Cliente;
import pe.barberturno.customers.ClienteRepository;
import pe.barberturno.platform.Membresia;
import pe.barberturno.platform.MembresiaRepository;
import pe.barberturno.platform.RolNegocio;
import pe.barberturno.reservations.EstadoReserva;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.reservations.ReservaRepository;
import pe.barberturno.scheduling.Barbero;
import pe.barberturno.scheduling.BarberoRepository;
import pe.barberturno.scheduling.Jornada;
import pe.barberturno.scheduling.JornadaRepository;
import pe.barberturno.support.JdbcNegocioPruebas;
import pe.barberturno.support.LimpiezaBaseDatos;
import pe.barberturno.support.RelojAjustable;
import pe.barberturno.users.Rol;
import pe.barberturno.users.Usuario;
import pe.barberturno.users.UsuarioRepository;
import pe.barberturno.whatsapp.EnviadorSalidaWhatsApp;
import pe.barberturno.whatsapp.OrigenMensaje;
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import pe.barberturno.whatsapp.SalidaWhatsApp;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración de extremo a extremo del canal de Twilio Sandbox con BSUID y respuestas TwiML
 * (CT-11, CT-12 y CT-13, 20-v2-twilio-sandbox.md §10).
 * Simula el recorrido completo con un remitente BSUID (sin WaId) donde todas las respuestas del bot
 * se entregan en el cuerpo XML TwiML del mismo webhook HTTP, sin emitir peticiones a la API REST de Twilio.
 * Verifica la creación de la reserva en la base de datos bajo la ficha del wa_id sintético, su consulta
 * en la agenda administrativa, la confirmación explícita previa a la cancelación, el desvío a REST ante
 * agotamiento del plazo de espera TwiML, y la actualización de estados por MessageSid con To BSUID.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "barberturno.admin.correo=",
        "barberturno.admin.password=",
        "barberturno.admin.nombre=",
        "barberturno.seguridad.cookie-secure=false",
        "barberturno.whatsapp.twilio.account-sid=AC0123456789abcdef0123456789abcdef",
        "barberturno.whatsapp.twilio.auth-token=token-ficticio-twilio-bsuid-it",
        "barberturno.whatsapp.twilio.canales[0].numero=+14155238886",
        "barberturno.whatsapp.twilio.canales[0].negocio-slug=barberturno",
        "barberturno.whatsapp.twilio.numero=+14155238886",
        "barberturno.whatsapp.twilio.negocio-slug=barberturno",
        "barberturno.whatsapp.twilio.respuesta-twiml=true",
        "barberturno.whatsapp.twilio.respuesta-espera=5s",
        "barberturno.whatsapp.twilio.respuesta-silencio=800ms",
        "barberturno.whatsapp.twilio.url-publica=https://pruebas.barberturno.test",
        "barberturno.whatsapp.twilio.intervalo-envio=3s"
})
@ActiveProfiles({"test", "twilio"})
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecorridoTwilioBsuidIT {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "token-ficticio-twilio-bsuid-it";
    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String BSUID_CLIENTE = "whatsapp:PE.0123456789ABCDEF";

    // Jueves 1 de octubre de 2026, 08:00 AM hora de Lima (13:00 UTC)
    private static final Instant INICIO_RELOJ = Instant.parse("2026-10-01T13:00:00Z");

    private static final ServidorTwilioFalso servidorTwilio = new ServidorTwilioFalso();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(servidorTwilio::close));
    }

    @AfterAll
    static void detenerServidorTwilio() {
        servidorTwilio.close();
    }

    @DynamicPropertySource
    static void registrarPropiedades(DynamicPropertyRegistry registry) {
        if (!servidorTwilio.estaIniciado()) {
            servidorTwilio.iniciar();
        }
        registry.add("barberturno.whatsapp.twilio.api-base", servidorTwilio::urlBase);
    }

    @LocalServerPort private int puerto;
    @Autowired private ProcesadorEventosWhatsApp procesador;
    @Autowired private EnviadorSalidaWhatsApp enviador;
    @Autowired private SalidaWhatsApp salida;
    @Autowired private RegistroCanalTwilioService registroCanal;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private MembresiaRepository membresias;
    @Autowired private ClienteRepository clientes;
    @Autowired private BarberoRepository barberos;
    @Autowired private ServicioRepository servicios;
    @Autowired private JornadaRepository jornadas;
    @Autowired private ReservaRepository reservas;
    @Autowired private DirectorioNegocios directorio;
    @Autowired private EjecutorEnNegocio ejecutor;
    @Autowired private JdbcNegocioPruebas sql;
    @Autowired private RelojAjustable reloj;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager gestor;
    @Autowired private CorrespondenciaDireccionesTwilio direcciones;

    private HttpClient clienteHttp;
    private ExecutorService ejecutorHilos;
    private long negocioId;
    private Usuario titular;
    private Map<String, Object> estadoOriginalNegocio;
    private String ultimoTextoRecibido;
    private List<String> ultimosNodosRecibidos = new ArrayList<>();

    @BeforeEach
    void prepararDatos() {
        clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        ejecutorHilos = Executors.newCachedThreadPool();

        LimpiezaBaseDatos.limpiar(sql);
        reloj.fijar(INICIO_RELOJ);
        servidorTwilio.reiniciar();

        negocioId = directorio.buscar("barberturno").orElseThrow();

        // Guardar valores originales de negocio para restaurarlos fielmente en la limpieza
        estadoOriginalNegocio = sql.queryForMap(
                "SELECT estado, confirmacion_manual, limite_reservas_activas FROM negocio WHERE id = ?", negocioId);

        // 1. Configurar negocio activo con confirmación automática
        sql.update("""
                UPDATE negocio SET estado = 'ACTIVO', confirmacion_manual = false
                WHERE id = ?""", negocioId);

        // 2. Propietario con rol ADMIN
        titular = usuarios.saveAndFlush(new Usuario("Carlos Titular", "admin-bsuid@ejemplo.test", "999000001",
                "hash-ficticio", Rol.ADMIN, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(negocioId, titular.getId(), RolNegocio.PROPIETARIO, reloj.instant()));

        // 3. Barbero con jornada activa de 12:00 a 18:00
        Usuario usuarioBarbero = usuarios.saveAndFlush(new Usuario("Profesional Carlos", "barbero-bsuid@ejemplo.test",
                "999000002", "hash-ficticio", Rol.BARBERO, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(negocioId, usuarioBarbero.getId(), RolNegocio.BARBERO, reloj.instant()));

        Barbero barbero = ejecutor.ejecutar(negocioId, () -> barberos.saveAndFlush(
                new Barbero(usuarioBarbero, "Barbero Carlos", reloj.instant())));

        ejecutor.ejecutar(negocioId, () -> {
            for (short dia = 1; dia <= 7; dia++) {
                jornadas.saveAndFlush(new Jornada(barbero, dia, LocalTime.of(12, 0), LocalTime.of(18, 0)));
            }
            return null;
        });

        // 4. Servicio activo: Corte clásico (30 min, S/ 25.00)
        ejecutor.ejecutar(negocioId, () -> servicios.saveAndFlush(
                new Servicio("Corte clásico", "Corte clásico para caballero", (short) 30, BigDecimal.valueOf(25.0), reloj.instant())));

        // 5. Registrar canal del Sandbox
        registroCanal.registrar();
    }

    @AfterEach
    void limpiar() {
        if (ejecutorHilos != null) {
            ejecutorHilos.shutdown();
            try {
                if (!ejecutorHilos.awaitTermination(5, TimeUnit.SECONDS)) {
                    ejecutorHilos.shutdownNow();
                }
            } catch (InterruptedException e) {
                ejecutorHilos.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (estadoOriginalNegocio != null) {
            sql.update("""
                    UPDATE negocio SET estado = ?, confirmacion_manual = ?, limite_reservas_activas = ?
                    WHERE id = ?""",
                    estadoOriginalNegocio.get("estado"),
                    estadoOriginalNegocio.get("confirmacion_manual"),
                    estadoOriginalNegocio.get("limite_reservas_activas"),
                    negocioId);
        }
        LimpiezaBaseDatos.limpiar(sql);
    }

    private String firmar(String url, Map<String, String> parametros) {
        try {
            StringBuilder mensaje = new StringBuilder(url);
            new TreeMap<>(parametros).forEach((k, v) -> mensaje.append(k).append(v == null ? "" : v));
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(AUTH_TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(mensaje.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al firmar petición en la prueba.", e);
        }
    }

    private HttpResponse<String> postTwilio(String ruta, Map<String, String> parametros) throws Exception {
        String urlFirma = URL_PUBLICA + ruta;
        String firma = firmar(urlFirma, parametros);

        StringBuilder form = new StringBuilder();
        for (var entry : parametros.entrySet()) {
            if (!form.isEmpty()) form.append("&");
            form.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header("X-Twilio-Signature", firma)
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        return clienteHttp.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Envía un mensaje POST firmado en otro hilo asíncrono para no bloquear la ejecución mientras
     * el webhook espera respuestas por TwiML. El hilo principal llama a procesarLote() y enviarLote()
     * hasta que la respuesta HTTP se completa.
     * @param sid MessageSid del mensaje entrante
     * @param cuerpo texto enviado por la persona
     * @return cuerpo XML devuelto por el webhook de Twilio
     */
    private HttpResponse<String> simularMensajeEntranteTwiml(String sid, String cuerpo) throws Exception {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", ACCOUNT_SID);
        datos.put("MessageSid", sid);
        datos.put("From", BSUID_CLIENTE);
        datos.put("To", "whatsapp:" + NUMERO_SANDBOX);
        // Omitir WaId intencionadamente para acreditar el flujo por BSUID
        datos.put("ProfileName", "Carlos Bsuid");
        datos.put("Body", cuerpo);

        CompletableFuture<HttpResponse<String>> futuro = CompletableFuture.supplyAsync(() -> {
            try {
                return postTwilio("/api/twilio/webhook", datos);
            } catch (Exception e) {
                throw new IllegalStateException("Fallo al enviar webhook TwiML asíncrono", e);
            }
        }, ejecutorHilos);

        long limiteNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (!futuro.isDone() && System.nanoTime() < limiteNanos) {
            procesador.procesarLote();
            enviador.enviarLote();
            TimeUnit.MILLISECONDS.sleep(15);
        }

        HttpResponse<String> respuesta = futuro.get(5, TimeUnit.SECONDS);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Content-Type").orElse(""))
                .contains(MediaType.TEXT_XML_VALUE);

        ultimosNodosRecibidos = extraerMensajesTwiml(respuesta.body());
        ultimoTextoRecibido = desescaparTwiml(respuesta.body());
        return respuesta;
    }

    @Test
    void recorridoCompleto_bsuidConRespuestasTwiml_reservaYCancelacion() throws Exception {
        String waIdSintetico = direcciones.identificar(null, BSUID_CLIENTE).orElseThrow();
        assertThat(waIdSintetico).hasSize(20).startsWith("9");

        // 1. Mensaje inicial: «hola»
        simularMensajeEntranteTwiml("SM_IN_000", "hola");
        List<String> nodosInicial = ultimosNodos();
        assertThat(nodosInicial)
                .as("el mensaje inicial debe contener exactamente 1 nodo <Message>")
                .hasSize(1);
        assertThat(nodosInicial.getFirst())
                .as("el primer <Message> debe contener la opción Reservar y pedir respuesta numérica")
                .contains("Reservar")
                .contains("Responde con el número");

        // 2. Proceso guiado de reserva: servicio, profesional, día, hora, confirmar
        int paso = 1;
        String resumen = null;
        while (reservasDelNegocio().isEmpty()) {
            assertThat(paso).as("pasos para reservar").isLessThanOrEqualTo(12);
            String texto = ultimoTexto();
            String eleccion;
            if (tieneOpcion(texto, "Aceptar")) eleccion = opcion(texto, "Aceptar");
            else if (tieneOpcion(texto, "Confirmar")) { resumen = texto; eleccion = opcion(texto, "Confirmar"); }
            else if (tieneOpcion(texto, "Reservar")) eleccion = opcion(texto, "Reservar");
            else if (tieneOpcion(texto, "Corte clásico")) eleccion = opcion(texto, "Corte clásico");
            else if (tieneOpcion(texto, "Barbero Carlos")) eleccion = opcion(texto, "Barbero Carlos");
            else eleccion = opcion(texto, "");
            simularMensajeEntranteTwiml("SM_IN_R%02d".formatted(paso++), eleccion);
            List<String> nodosPaso = ultimosNodos();
            assertThat(nodosPaso)
                    .as("cada paso de la reserva debe devolver al menos un nodo <Message>")
                    .isNotEmpty();
            for (String nodo : nodosPaso) {
                assertThat(nodo).as("los nodos <Message> no deben estar en blanco").isNotBlank();
            }
        }

        // El resumen previo a confirmar lo compone el servidor con el servicio y el profesional elegidos
        assertThat(resumen).isNotNull().contains("Corte clásico").contains("Carlos");
        List<String> nodosConfirmacion = ultimosNodos();
        assertThat(nodosConfirmacion)
                .as("tras confirmar, debe recibirse al menos 1 nodo <Message> en orden")
                .hasSize(1);
        assertThat(nodosConfirmacion.getFirst())
                .as("el nodo <Message> posterior a confirmar debe contener la confirmación de la cita y el menú")
                .contains("Listo:")
                .contains("Reservar");

        // 3. Verificación en la base de datos: la reserva existe y tiene la ficha con el wa_id sintético
        var listaReservas = reservasDelNegocio();
        assertThat(listaReservas).hasSize(1);
        Reserva reservaCreada = listaReservas.getFirst();
        assertThat(reservaCreada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);

        Cliente fichaCliente = ejecutor.ejecutar(negocioId, () ->
                clientes.findById(reservaCreada.getCliente().getId()).orElseThrow());
        assertThat(fichaCliente.getWaId()).isEqualTo(waIdSintetico);

        // 4. Verificación en la agenda administrativa: GET /api/n/barberturno/reservas
        String sesionJwt = jwt.emitir(titular);
        HttpRequest peticionAgenda = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/n/barberturno/reservas?desde=2026-10-01&hasta=2026-10-31"))
                .header("Cookie", "BT_SESION=" + sesionJwt)
                .GET()
                .build();
        HttpResponse<String> respuestaAgenda = clienteHttp.send(peticionAgenda, HttpResponse.BodyHandlers.ofString());
        assertThat(respuestaAgenda.statusCode()).isEqualTo(200);
        assertThat(respuestaAgenda.body()).contains("\"id\":" + reservaCreada.getId());

        // 5. Cancelación: «hola» devuelve el menú principal; Mis citas → la cita → Cancelar → confirmación → Confirmar
        simularMensajeEntranteTwiml("SM_IN_C00", "hola");
        List<String> nodosC0 = ultimosNodos();
        assertThat(nodosC0).as("menú inicial de cancelación en <Message>").hasSize(1);
        assertThat(nodosC0.getFirst()).contains("Mis citas").contains("Reservar");

        paso = 1;
        while (!tieneOpcion(ultimoTexto(), "Cancelar")) {
            assertThat(paso).as("pasos para llegar a Cancelar").isLessThanOrEqualTo(8);
            String texto = ultimoTexto();
            String eleccion;
            if (tieneOpcion(texto, "Mis citas")) eleccion = opcion(texto, "Mis citas");
            else if (tieneOpcion(texto, "Corte clásico")) eleccion = opcion(texto, "Corte clásico");
            else eleccion = opcion(texto, "");
            simularMensajeEntranteTwiml("SM_IN_C%02d".formatted(paso++), eleccion);
            assertThat(ultimosNodos()).as("cada respuesta en camino a cancelación debe tener <Message>").isNotEmpty();
        }

        // Elegir opción «Cancelar»
        String textoConCancelar = ultimoTexto();
        assertThat(tieneOpcion(textoConCancelar, "Cancelar")).isTrue();
        simularMensajeEntranteTwiml("SM_IN_C_CAN", opcion(textoConCancelar, "Cancelar"));

        // Comprobar que sigue CONFIRMADA (V2-RN-10: no cancela sin confirmación explícita)
        assertThat(estadoDe(reservaCreada.getId()))
                .as("la reserva debe seguir CONFIRMADA antes de confirmar la cancelación")
                .isEqualTo(EstadoReserva.CONFIRMADA);

        // Exigir que el mensaje recibido pida confirmación explícita con la opción Confirmar en <Message>
        List<String> nodosPideConfirmar = ultimosNodos();
        assertThat(nodosPideConfirmar).hasSize(1);
        String textoConfirmacion = nodosPideConfirmar.getFirst();
        assertThat(textoConfirmacion).as("resumen o aviso de confirmación de cancelación").contains("Confirmar");
        assertThat(tieneOpcion(textoConfirmacion, "Confirmar")).as("opción Confirmar presente").isTrue();

        // Enviar explícitamente Confirmar
        simularMensajeEntranteTwiml("SM_IN_C_CONF", opcion(textoConfirmacion, "Confirmar"));

        // Ahora sí la reserva debe quedar CANCELADA
        assertThat(estadoDe(reservaCreada.getId()))
                .as("la reserva queda CANCELADA tras confirmar")
                .isEqualTo(EstadoReserva.CANCELADA);
        List<String> nodosCancelado = ultimosNodos();
        assertThat(nodosCancelado)
                .as("la respuesta tras cancelación debe incluir exactamente 1 nodo <Message>")
                .hasSize(1);
        assertThat(nodosCancelado.getFirst())
                .as("el mensaje debe confirmar la cancelación de la cita")
                .contains("Cancelamos tu cita")
                .contains("Reservar");

        // 6. Sin REST: verificar que el servidor Twilio simulado no recibió NINGUNA petición HTTP
        assertThat(servidorTwilio.peticiones())
                .as("todas las respuestas del bot deben haber viajado por TwiML sin llamadas REST a Twilio")
                .isEmpty();
    }

    @Test
    void webhook_sinRespuestaEnPlazo_devuelveResponseVacioYEnvioPosteriorSeRetieneParaSiguienteWebhook() throws Exception {
        servidorTwilio.reiniciar();

        String sid = "SM_IN_TIMEOUT_001";
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", ACCOUNT_SID);
        datos.put("MessageSid", sid);
        datos.put("From", BSUID_CLIENTE);
        datos.put("To", "whatsapp:" + NUMERO_SANDBOX);
        datos.put("ProfileName", "Carlos Bsuid");
        datos.put("Body", "hola");

        // 1. Enviar webhook asíncrono SIN procesar la cola en el hilo principal
        CompletableFuture<HttpResponse<String>> futuro = CompletableFuture.supplyAsync(() -> {
            try {
                return postTwilio("/api/twilio/webhook", datos);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, ejecutorHilos);

        // Esperar a que el webhook agote su plazo de respuestaEspera (5 s)
        HttpResponse<String> respuesta = futuro.get(8, TimeUnit.SECONDS);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEqualTo("<Response/>");

        // En este punto aún no ha salido ninguna petición por REST
        assertThat(servidorTwilio.peticiones()).isEmpty();

        // 2. Procesar el lote de la cola tras haberse cerrado la espera de TwiML
        procesador.procesarLote();
        enviador.enviarLote();

        // 3. V2-DA-108: Un sintético sin espera abierta no va por REST, se retiene en memoria
        assertThat(servidorTwilio.peticiones()).as("no debe realizarse petición REST para sintético").isEmpty();

        // 4. El siguiente webhook de esa persona recoge la respuesta previamente retenida
        simularMensajeEntranteTwiml("SM_IN_TIMEOUT_002", "1");
        List<String> nodosSiguiente = ultimosNodos();
        assertThat(nodosSiguiente).as("el siguiente webhook debe recibir las respuestas en TwiML").isNotEmpty();
    }

    @Test
    void estado_conToBsuid_actualizaMensajeSalientePorSid() throws Exception {
        String waIdSintetico = direcciones.identificar(null, BSUID_CLIENTE).orElseThrow();

        long conversacionId = ejecutor.ejecutar(negocioId, () -> {
            long clienteId = sql.queryForObject("""
                    INSERT INTO cliente(negocio_id, nombre, wa_id, activo, privacidad_aceptada_en, creado_en, actualizado_en)
                    VALUES (?, 'Cliente BSUID Estado', ?, true, ?, ?, ?) RETURNING id""", Long.class, negocioId, waIdSintetico,
                    java.sql.Timestamp.from(reloj.instant()), java.sql.Timestamp.from(reloj.instant()),
                    java.sql.Timestamp.from(reloj.instant()));

            return sql.queryForObject("""
                    INSERT INTO conversacion(negocio_id, wa_id, cliente_id, ultima_entrada_en, creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, negocioId, waIdSintetico, clienteId,
                    java.sql.Timestamp.from(reloj.instant()), java.sql.Timestamp.from(reloj.instant()),
                    java.sql.Timestamp.from(reloj.instant()));
        });

        long mensajeId = ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarTexto(conversacionId, "Mensaje saliente para BSUID", OrigenMensaje.SISTEMA, OptionalLong.empty())));
        ejecutor.ejecutar(negocioId, () -> {
            sql.update("UPDATE mensaje_whatsapp SET wamid = 'SM_OUT_BSUID_DELIV_1', estado = 'ENVIADO', enviado_como = 'TEXTO' WHERE id = ?", mensajeId);
            return null;
        });

        // Enviar callback firmado de delivered con To BSUID a /api/twilio/estado
        Map<String, String> datosDelivered = new LinkedHashMap<>();
        datosDelivered.put("AccountSid", ACCOUNT_SID);
        datosDelivered.put("MessageSid", "SM_OUT_BSUID_DELIV_1");
        datosDelivered.put("MessageStatus", "delivered");
        datosDelivered.put("To", BSUID_CLIENTE);

        var respDelivered = postTwilio("/api/twilio/estado", datosDelivered);
        assertThat(respDelivered.statusCode()).isEqualTo(200);
        procesador.procesarLote();

        String estadoDelivered = ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT estado FROM mensaje_whatsapp WHERE wamid = ?", String.class, "SM_OUT_BSUID_DELIV_1"));
        assertThat(estadoDelivered).isEqualTo("ENTREGADO");
    }

    private String ultimoTexto() {
        return ultimoTextoRecibido;
    }

    private List<String> ultimosNodos() {
        return ultimosNodosRecibidos;
    }

    private List<Reserva> reservasDelNegocio() {
        return ejecutor.ejecutar(negocioId, () -> reservas.findAll());
    }

    private EstadoReserva estadoDe(long reservaId) {
        return ejecutor.ejecutar(negocioId, () -> reservas.findById(reservaId).orElseThrow().getEstado());
    }

    private static String desescaparTwiml(String xml) {
        List<String> mensajes = extraerMensajesTwiml(xml);
        return String.join("\n\n", mensajes);
    }

    private static List<String> extraerMensajesTwiml(String xml) {
        List<String> mensajes = new ArrayList<>();
        if (xml == null || !xml.contains("<Message>")) return mensajes;
        Pattern patron = Pattern.compile("<Message>(.*?)</Message>", Pattern.DOTALL);
        Matcher matcher = patron.matcher(xml);
        while (matcher.find()) {
            String contenido = matcher.group(1);
            String desescapado = contenido
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&");
            mensajes.add(desescapado);
        }
        return mensajes;
    }

    private static final Pattern OPCION = Pattern.compile("^(\\d+)\\. (.*)$");

    private static boolean tieneOpcion(String texto, String fragmento) {
        return buscarOpcion(texto, fragmento) != null;
    }

    /** Número de la primera opción numerada cuyo texto contiene el fragmento (vacío = la primera). */
    private static String opcion(String texto, String fragmento) {
        String numero = buscarOpcion(texto, fragmento);
        if (numero == null) throw new AssertionError("Sin opción «" + fragmento + "» en el mensaje:\n" + texto);
        return numero;
    }

    private static String buscarOpcion(String texto, String fragmento) {
        if (texto == null) return null;
        for (String linea : texto.split("\n")) {
            var m = OPCION.matcher(linea.strip());
            if (m.matches() && m.group(2).contains(fragmento)) return m.group(1);
        }
        return null;
    }
}
