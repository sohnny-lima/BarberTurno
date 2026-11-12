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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
 * Pruebas de integración de extremo a extremo del canal de Twilio Sandbox con PostgreSQL (CT-5 y CT-6).
 * Simula el recorrido completo guiado exclusivamente por números:
 * «hola» → aceptar privacidad → Reservar → servicio → profesional → día → hora → Confirmar.
 * Comprueba que la reserva se crea en la base de datos para el negocio {@code barberturno} con la ficha
 * asociada al {@code wa_id}, que aparece en la agenda administrativa ({@code GET /api/n/barberturno/reservas}),
 * y que el texto final confirma la reserva.
 * A continuación realiza el flujo de cancelación: Mis citas → la cita → Cancelar → Confirmar → la reserva queda {@code CANCELADA}.
 * Comprueba además (CT-5) que los avisos firmados de {@code delivered} y {@code failed} actualicen el estado en {@code mensaje_whatsapp}.
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
        "barberturno.whatsapp.twilio.auth-token=token-ficticio-twilio-it",
        "barberturno.whatsapp.twilio.numero=+14155238886",
        "barberturno.whatsapp.twilio.negocio-slug=barberturno",
        "barberturno.whatsapp.twilio.url-publica=https://pruebas.barberturno.test",
        "barberturno.whatsapp.twilio.intervalo-envio=3s"
})
@ActiveProfiles("twilio")
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecorridoTwilioIT {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "token-ficticio-twilio-it";
    private static final String NUMERO_SANDBOX = "+14155238886";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String WA_CLIENTE = "51999000001";
    // Jueves 1 de octubre de 2026, 08:00 AM hora de Lima (13:00 UTC)
    private static final Instant INICIO_RELOJ = Instant.parse("2026-10-01T13:00:00Z");

    private static final ServidorTwilioFalso servidorTwilio = new ServidorTwilioFalso();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(servidorTwilio::close));
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

    private HttpClient clienteHttp;
    private long negocioId;
    private Usuario titular;

    @BeforeEach
    void prepararDatos() {
        clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        LimpiezaBaseDatos.limpiar(sql);
        reloj.fijar(INICIO_RELOJ);
        servidorTwilio.reiniciar();

        negocioId = directorio.buscar("barberturno").orElseThrow();

        // 1. Configurar negocio activo con confirmación automática
        sql.update("""
                UPDATE negocio SET estado = 'ACTIVO', confirmacion_manual = false, limite_reservas_activas = 5
                WHERE id = ?""", negocioId);

        // 2. Propietario con rol ADMIN
        titular = usuarios.saveAndFlush(new Usuario("Carlos Titular", "admin-barberturno@ejemplo.test", "999000001",
                "hash-ficticio", Rol.ADMIN, reloj.instant(), reloj.instant()));
        membresias.save(new Membresia(negocioId, titular.getId(), RolNegocio.PROPIETARIO, reloj.instant()));

        // 3. Barbero con jornada activa de 12:00 a 18:00 (a las 08:00, la cita de las 12:00 está a 4 horas: cancelable)
        Usuario usuarioBarbero = usuarios.saveAndFlush(new Usuario("Profesional Carlos", "barbero-carlos@ejemplo.test",
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

        // 5. Registrar el canal del Sandbox
        registroCanal.registrar();
    }

    @AfterEach
    void limpiar() {
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
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Twilio-Signature", firma)
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        return clienteHttp.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void simularMensajeEntrante(String sid, String cuerpo) throws Exception {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", ACCOUNT_SID);
        datos.put("MessageSid", sid);
        datos.put("From", "whatsapp:+" + WA_CLIENTE);
        datos.put("To", "whatsapp:" + NUMERO_SANDBOX);
        datos.put("WaId", WA_CLIENTE);
        datos.put("ProfileName", "Carlos Twilio");
        datos.put("Body", cuerpo);

        var respuesta = postTwilio("/api/twilio/webhook", datos);
        assertThat(respuesta.statusCode()).isEqualTo(200);

        // Procesa la entrada en la conversación y despacha la respuesta por la outbox
        procesador.procesarLote();
        enviador.enviarLote();
    }

    @Test
    void recorridoCompleto_reservaYCancelacionPorNumeros() throws Exception {
        // Paso 1: Saludo inicial («hola») -> el bot solicita consentimiento de privacidad
        simularMensajeEntrante("SM_IN_001", "hola");
        var peticion1 = servidorTwilio.ultimaPeticion();
        assertThat(peticion1.parametros().get("Body"))
                .contains("1. Aceptar")
                .contains("2. No, gracias");

        // Paso 2: Opción 1 («1») -> Acepta privacidad, nace la ficha y envía el menú principal
        simularMensajeEntrante("SM_IN_002", "1");
        var peticion2 = servidorTwilio.ultimaPeticion();
        assertThat(peticion2.parametros().get("Body"))
                .contains("1. Reservar")
                .contains("2. Mis citas");

        // Paso 3: Opción 1 («1») -> Reservar, envía la lista de servicios
        simularMensajeEntrante("SM_IN_003", "1");
        var peticion3 = servidorTwilio.ultimaPeticion();
        assertThat(peticion3.parametros().get("Body"))
                .contains("1. Corte clásico — 30 min · S/ 25.00");

        // Paso 4: Opción 1 («1») -> Selecciona Corte clásico, envía la lista de profesionales
        simularMensajeEntrante("SM_IN_004", "1");
        var peticion4 = servidorTwilio.ultimaPeticion();
        assertThat(peticion4.parametros().get("Body"))
                .contains("1. Cualquiera")
                .contains("2. Barbero Carlos");

        // Paso 5: Opción 1 («1») -> Cualquiera, envía los días disponibles
        simularMensajeEntrante("SM_IN_005", "1");
        var peticion5 = servidorTwilio.ultimaPeticion();
        assertThat(peticion5.parametros().get("Body"))
                .contains("1. Hoy")
                .contains("2. Mañana");

        // Paso 6: Opción 1 («1») -> Hoy, envía los horarios libres del día (inicia a las 12:00)
        simularMensajeEntrante("SM_IN_006", "1");
        var peticion6 = servidorTwilio.ultimaPeticion();
        assertThat(peticion6.parametros().get("Body"))
                .contains("1. 12:00");

        // Paso 7: Opción 1 («1») -> Selecciona las 12:00, envía el resumen con Confirmar y Cambiar
        simularMensajeEntrante("SM_IN_007", "1");
        var peticion7 = servidorTwilio.ultimaPeticion();
        assertThat(peticion7.parametros().get("Body"))
                .contains("1. Confirmar")
                .contains("2. Cambiar");

        // Paso 8: Opción 1 («1») -> Confirmar reserva, crea la reserva e informa la confirmación
        simularMensajeEntrante("SM_IN_008", "1");
        var peticion8 = servidorTwilio.ultimaPeticion();
        assertThat(peticion8.parametros().get("Body"))
                .containsIgnoringCase("confirmad");

        // Verificación en la base de datos: la reserva existe en barberturno y tiene la ficha del wa_id
        var listaReservas = ejecutor.ejecutar(negocioId, () -> reservas.findAll());
        assertThat(listaReservas).hasSize(1);
        Reserva reservaCreada = listaReservas.getFirst();
        assertThat(reservaCreada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reservaCreada.getCanal()).isEqualTo("WHATSAPP");

        Cliente fichaCliente = ejecutor.ejecutar(negocioId, () ->
                clientes.findById(reservaCreada.getCliente().getId()).orElseThrow());
        assertThat(fichaCliente.getWaId()).isEqualTo(WA_CLIENTE);
        assertThat(fichaCliente.getNombre()).isEqualTo("Carlos Twilio");

        // Verificación en la API administrativa: GET /api/n/barberturno/reservas con sesión del propietario
        String sesionJwt = jwt.emitir(titular);
        HttpRequest peticionAgenda = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/n/barberturno/reservas?desde=2026-10-01&hasta=2026-10-02"))
                .header("Cookie", "BT_SESION=" + sesionJwt)
                .GET()
                .build();
        HttpResponse<String> respuestaAgenda = clienteHttp.send(peticionAgenda, HttpResponse.BodyHandlers.ofString());
        assertThat(respuestaAgenda.statusCode()).isEqualTo(200);
        assertThat(respuestaAgenda.body()).contains(reservaCreada.getId().toString());

        // Paso 9: Consultar «mis citas» -> envía la lista de citas futuras
        simularMensajeEntrante("SM_IN_009", "mis citas");
        var peticion9 = servidorTwilio.ultimaPeticion();
        assertThat(peticion9.parametros().get("Body"))
                .contains("Corte clásico");

        // Paso 10: Opción 1 («1») -> Selecciona la cita, envía acciones [Cancelar], [Reprogramar], [Menú]
        simularMensajeEntrante("SM_IN_010", "1");
        var peticion10 = servidorTwilio.ultimaPeticion();
        assertThat(peticion10.parametros().get("Body"))
                .contains("1. Cancelar");

        // Paso 11: Opción 1 («1») -> Cancelar, solicita confirmación de cancelación
        simularMensajeEntrante("SM_IN_011", "1");
        var peticion11 = servidorTwilio.ultimaPeticion();
        assertThat(peticion11.parametros().get("Body"))
                .contains("1. Confirmar")
                .contains("2. Cambiar");

        // Paso 12: Opción 1 («1») -> Confirma la cancelación
        simularMensajeEntrante("SM_IN_012", "1");
        var peticion12 = servidorTwilio.ultimaPeticion();
        assertThat(peticion12.parametros().get("Body"))
                .containsIgnoringCase("cancelad");

        // Verificación final en la base de datos: la reserva queda en estado CANCELADA
        Reserva reservaCancelada = ejecutor.ejecutar(negocioId, () ->
                reservas.findById(reservaCreada.getId()).orElseThrow());
        assertThat(reservaCancelada.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void estado_deliveredYFailedActualizanMensajeSalientePorSid() throws Exception {
        // Crear cliente con consentimiento y conversación mediante SQL para respetar las restricciones de RLS
        long conversacionId = ejecutor.ejecutar(negocioId, () -> {
            long clienteId = sql.queryForObject("""
                    INSERT INTO cliente(negocio_id, nombre, wa_id, activo, privacidad_aceptada_en, creado_en, actualizado_en)
                    VALUES (?, 'Cliente Estado', ?, true, ?, ?, ?) RETURNING id""", Long.class, negocioId, WA_CLIENTE,
                    java.sql.Timestamp.from(reloj.instant()), java.sql.Timestamp.from(reloj.instant()),
                    java.sql.Timestamp.from(reloj.instant()));

            return sql.queryForObject("""
                    INSERT INTO conversacion(negocio_id, wa_id, cliente_id, ultima_entrada_en, creado_en, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, negocioId, WA_CLIENTE, clienteId,
                    java.sql.Timestamp.from(reloj.instant()), java.sql.Timestamp.from(reloj.instant()),
                    java.sql.Timestamp.from(reloj.instant()));
        });

        // 1. Encolar y enviar un mensaje para probar delivered
        servidorTwilio.encolarExito("SM_OUT_DELIV_1");
        ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarTexto(conversacionId, "Mensaje para prueba delivered", OrigenMensaje.SISTEMA, OptionalLong.empty())));
        enviador.enviarLote();

        // Enviar callback firmado de delivered a /api/twilio/estado
        Map<String, String> datosDelivered = new LinkedHashMap<>();
        datosDelivered.put("AccountSid", ACCOUNT_SID);
        datosDelivered.put("MessageSid", "SM_OUT_DELIV_1");
        datosDelivered.put("MessageStatus", "delivered");
        datosDelivered.put("To", "whatsapp:+" + WA_CLIENTE);

        var respDelivered = postTwilio("/api/twilio/estado", datosDelivered);
        assertThat(respDelivered.statusCode()).isEqualTo(200);
        procesador.procesarLote();

        String estadoDelivered = ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT estado FROM mensaje_whatsapp WHERE wamid = ?", String.class, "SM_OUT_DELIV_1"));
        assertThat(estadoDelivered).isEqualTo("ENTREGADO");

        // 2. Encolar y enviar un segundo mensaje para probar failed
        servidorTwilio.encolarExito("SM_OUT_FAIL_2");
        ejecutor.ejecutar(negocioId, () -> new TransactionTemplate(gestor).execute(status ->
                salida.encolarTexto(conversacionId, "Mensaje para prueba failed", OrigenMensaje.SISTEMA, OptionalLong.empty())));
        enviador.enviarLote();

        // Enviar callback firmado de failed con ErrorCode 63015 a /api/twilio/estado
        Map<String, String> datosFailed = new LinkedHashMap<>();
        datosFailed.put("AccountSid", ACCOUNT_SID);
        datosFailed.put("MessageSid", "SM_OUT_FAIL_2");
        datosFailed.put("MessageStatus", "failed");
        datosFailed.put("ErrorCode", "63015");
        datosFailed.put("To", "whatsapp:+" + WA_CLIENTE);

        var respFailed = postTwilio("/api/twilio/estado", datosFailed);
        assertThat(respFailed.statusCode()).isEqualTo(200);
        procesador.procesarLote();

        var filaFailed = ejecutor.ejecutar(negocioId, () -> sql.queryForMap(
                "SELECT estado, codigo_meta FROM mensaje_whatsapp WHERE wamid = ?", "SM_OUT_FAIL_2"));
        assertThat(filaFailed.get("estado")).isEqualTo("FALLIDO");
        assertThat(((Number) filaFailed.get("codigo_meta")).intValue()).isEqualTo(63015);
    }
}
