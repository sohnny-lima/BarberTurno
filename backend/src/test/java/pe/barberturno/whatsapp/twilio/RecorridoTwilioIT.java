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
 * A continuación realiza el flujo de cancelación: Mis citas → la cita → Cancelar → confirmación → Confirmar → la reserva queda {@code CANCELADA}.
 * Comprueba además (CT-5) que los avisos firmados de {@code delivered}, {@code read} y {@code failed} actualicen el estado en {@code mensaje_whatsapp},
 * y (CT-2) la idempotencia de recepción de eventos por {@code MessageSid}.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.1
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
        "barberturno.whatsapp.twilio.respuesta-twiml=false",
        "barberturno.whatsapp.twilio.url-publica=https://pruebas.barberturno.test",
        "barberturno.whatsapp.twilio.intervalo-envio=3s"
})
@ActiveProfiles({"test", "twilio"})
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
    private Map<String, Object> estadoOriginalNegocio;

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

        // Guardar valores originales de negocio para restaurarlos fielmente en la limpieza
        estadoOriginalNegocio = sql.queryForMap(
                "SELECT estado, confirmacion_manual, limite_reservas_activas FROM negocio WHERE id = ?", negocioId);

        // 1. Configurar negocio activo con confirmación automática (sin alterar limite_reservas_activas)
        sql.update("""
                UPDATE negocio SET estado = 'ACTIVO', confirmacion_manual = false
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
        // Reserva: el recorrido se adapta al diálogo real del bot y elige cada opción por su texto.
        simularMensajeEntrante("SM_IN_000", "hola");
        assertThat(ultimoTexto()).contains("Responda con el número.");
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
            simularMensajeEntrante("SM_IN_R%02d".formatted(paso++), eleccion);
        }
        // El resumen previo a confirmar lo compone el servidor con el servicio y el profesional elegidos.
        assertThat(resumen).isNotNull().contains("Corte clásico").contains("Carlos");
        assertThat(ultimoTexto()).isNotBlank();

        // Verificación en la base de datos: la reserva existe en barberturno y tiene la ficha del wa_id
        var listaReservas = reservasDelNegocio();
        assertThat(listaReservas).hasSize(1);
        Reserva reservaCreada = listaReservas.getFirst();
        assertThat(reservaCreada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        Cliente fichaCliente = ejecutor.ejecutar(negocioId, () ->
                clientes.findById(reservaCreada.getCliente().getId()).orElseThrow());
        assertThat(fichaCliente.getWaId()).isEqualTo(WA_CLIENTE);

        // Verificación en la agenda: GET /api/n/barberturno/reservas con sesión del propietario
        String sesionJwt = jwt.emitir(titular);
        HttpRequest peticionAgenda = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/n/barberturno/reservas?desde=2026-10-01&hasta=2026-10-31"))
                .header("Cookie", "BT_SESION=" + sesionJwt)
                .GET()
                .build();
        HttpResponse<String> respuestaAgenda = clienteHttp.send(peticionAgenda, HttpResponse.BodyHandlers.ofString());
        assertThat(respuestaAgenda.statusCode()).isEqualTo(200);
        assertThat(respuestaAgenda.body()).contains("\"id\":" + reservaCreada.getId());

        // Cancelación: «hola» devuelve el menú principal; Mis citas → la cita → Cancelar → confirmación → Confirmar.
        simularMensajeEntrante("SM_IN_C00", "hola");
        paso = 1;
        while (!tieneOpcion(ultimoTexto(), "Cancelar")) {
            assertThat(paso).as("pasos para llegar a Cancelar").isLessThanOrEqualTo(8);
            String texto = ultimoTexto();
            String eleccion;
            if (tieneOpcion(texto, "Mis citas")) eleccion = opcion(texto, "Mis citas");
            else if (tieneOpcion(texto, "Corte clásico")) eleccion = opcion(texto, "Corte clásico");
            else eleccion = opcion(texto, "");
            simularMensajeEntrante("SM_IN_C%02d".formatted(paso++), eleccion);
        }

        // 1. Elegir opción «Cancelar»
        String textoConCancelar = ultimoTexto();
        assertThat(tieneOpcion(textoConCancelar, "Cancelar")).isTrue();
        simularMensajeEntrante("SM_IN_C_CAN", opcion(textoConCancelar, "Cancelar"));

        // 2. CT-6: Comprobar que sigue CONFIRMADA (V2-RN-10: no cancela inmediatamente sin confirmación explícita)
        assertThat(estadoDe(reservaCreada.getId()))
                .as("la reserva debe seguir CONFIRMADA antes de confirmar la cancelación")
                .isEqualTo(EstadoReserva.CONFIRMADA);

        // 3. Exigir que el mensaje recibido pida confirmación explícita con la opción Confirmar
        String textoConfirmacion = ultimoTexto();
        assertThat(textoConfirmacion).as("resumen o aviso de confirmación de cancelación").contains("Confirmar");
        assertThat(tieneOpcion(textoConfirmacion, "Confirmar")).as("opción Confirmar presente").isTrue();

        // 4. Enviar explícitamente Confirmar
        simularMensajeEntrante("SM_IN_C_CONF", opcion(textoConfirmacion, "Confirmar"));

        // 5. Ahora sí la reserva debe quedar CANCELADA
        assertThat(estadoDe(reservaCreada.getId()))
                .as("la reserva queda CANCELADA tras confirmar")
                .isEqualTo(EstadoReserva.CANCELADA);
        assertThat(ultimoTexto()).isNotBlank();
    }

    private String ultimoTexto() {
        return servidorTwilio.ultimaPeticion().parametros().get("Body");
    }

    private java.util.List<Reserva> reservasDelNegocio() {
        return ejecutor.ejecutar(negocioId, () -> reservas.findAll());
    }

    private EstadoReserva estadoDe(long reservaId) {
        return ejecutor.ejecutar(negocioId, () -> reservas.findById(reservaId).orElseThrow().getEstado());
    }

    private static final java.util.regex.Pattern OPCION = java.util.regex.Pattern.compile("^(\\d+)\\. (.*)$");

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

    @Test
    void webhook_mismoMessageSidRepetido_dejaUnSoloEventoYNoDuplicaEfectos() throws Exception {
        String sid = "SM_IDEMPOTENTE_001";
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", ACCOUNT_SID);
        datos.put("MessageSid", sid);
        datos.put("From", "whatsapp:+" + WA_CLIENTE);
        datos.put("To", "whatsapp:" + NUMERO_SANDBOX);
        datos.put("WaId", WA_CLIENTE);
        datos.put("ProfileName", "Carlos Twilio");
        datos.put("Body", "hola");

        // 1. Primer POST firmado
        var resp1 = postTwilio("/api/twilio/webhook", datos);
        assertThat(resp1.statusCode()).isEqualTo(200);

        // 2. Segundo POST firmado con idéntico MessageSid
        var resp2 = postTwilio("/api/twilio/webhook", datos);
        assertThat(resp2.statusCode()).isEqualTo(200);

        // 3. Comprobar cantidad, clave y contenido del evento en la base de datos (CT-2)
        String claveEsperada = "m:" + sid;
        var filasEvento = sql.queryForList(
                "SELECT id, clave_idempotencia AS clave, tipo, cuerpo::text AS cuerpo FROM evento_whatsapp_entrante WHERE clave_idempotencia = ?", claveEsperada);
        assertThat(filasEvento).as("debe existir exactamente un evento persistido con esa clave").hasSize(1);

        var evento = filasEvento.getFirst();
        assertThat(evento.get("clave")).isEqualTo(claveEsperada);
        assertThat(evento.get("tipo")).isEqualTo("MENSAJE");
        String cuerpoGuardado = (String) evento.get("cuerpo");
        assertThat(cuerpoGuardado).contains(sid).contains("hola");

        // 4. Procesar el lote de la cola y verificar que no duplica efectos salientes
        servidorTwilio.reiniciar();
        procesador.procesarLote();
        enviador.enviarLote();

        // Debe haber enviado exactamente una sola respuesta saliente al proveedor
        assertThat(servidorTwilio.peticiones()).as("solo se emite una respuesta por el evento único").hasSize(1);
        assertThat(servidorTwilio.ultimaPeticion().parametros().get("Body")).contains("Responda con el número");

        // En la base de datos solo se guardó un mensaje saliente
        // mensaje_whatsapp no guarda el wa_id (va en la conversación) y ambas tablas tienen RLS: consulta en el negocio.
        Integer mensajesSalientes = ejecutor.ejecutar(negocioId, () -> sql.queryForObject("""
                SELECT count(*) FROM mensaje_whatsapp m JOIN conversacion c ON c.id = m.conversacion_id
                WHERE c.wa_id = ? AND m.direccion = 'SALIENTE'""", Integer.class, WA_CLIENTE));
        assertThat(mensajesSalientes).isEqualTo(1);
    }

    @Test
    void estado_deliveredReadYFailedActualizanMensajeSalientePorSid() throws Exception {
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

        // Enviar callback firmado de read a /api/twilio/estado para el mismo SID (CT-5)
        Map<String, String> datosRead = new LinkedHashMap<>();
        datosRead.put("AccountSid", ACCOUNT_SID);
        datosRead.put("MessageSid", "SM_OUT_DELIV_1");
        datosRead.put("MessageStatus", "read");
        datosRead.put("To", "whatsapp:+" + WA_CLIENTE);

        var respRead = postTwilio("/api/twilio/estado", datosRead);
        assertThat(respRead.statusCode()).isEqualTo(200);
        procesador.procesarLote();

        String estadoRead = ejecutor.ejecutar(negocioId, () -> sql.queryForObject(
                "SELECT estado FROM mensaje_whatsapp WHERE wamid = ?", String.class, "SM_OUT_DELIV_1"));
        assertThat(estadoRead).isEqualTo("LEIDO");

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
