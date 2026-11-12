package pe.barberturno.whatsapp.twilio;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.barberturno.auth.JwtService;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.customers.ClienteRepository;
import pe.barberturno.platform.Membresia;
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
import pe.barberturno.whatsapp.ProcesadorEventosWhatsApp;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración para soporte de varios números y canales Twilio simultáneos (CT-18, V2-DA-107).
 * Valida que una configuración con números o slugs duplicados impida arrancar la aplicación;
 * que las reservas creadas por webhooks firmados dirigidos al número de barberturno (+14155238886)
 * queden exclusivamente en la agenda de barberturno y las del número de sur (+15005550006) en la de sur;
 * que el mismo BSUID con menús abiertos en ambos negocios mantenga los estados aislados sin cruzarlos;
 * y que el emisor «From» de las solicitudes REST corresponda exactamente al canal del negocio emisor.
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
        "barberturno.whatsapp.twilio.auth-token=token-ficticio-ct18-it",
        "barberturno.whatsapp.twilio.canales[0].numero=+14155238886",
        "barberturno.whatsapp.twilio.canales[0].negocio-slug=barberturno",
        "barberturno.whatsapp.twilio.canales[1].numero=+15005550006",
        "barberturno.whatsapp.twilio.canales[1].negocio-slug=sur",
        "barberturno.whatsapp.twilio.url-publica=https://pruebas.barberturno.test",
        "barberturno.whatsapp.twilio.intervalo-envio=3s"
})
@ActiveProfiles({"test", "twilio"})
@ContextConfiguration(initializers = VariosNumerosTwilioIT.InicializadorNegocios.class)
@Import(RelojAjustable.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VariosNumerosTwilioIT {

    private static final String ACCOUNT_SID = "AC0123456789abcdef0123456789abcdef";
    private static final String AUTH_TOKEN = "token-ficticio-ct18-it";
    private static final String NUMERO_A = "+14155238886";
    private static final String NUMERO_B = "+15005550006";
    private static final String PHONE_ID_A = "14155238886";
    private static final String PHONE_ID_B = "15005550006";
    private static final String URL_PUBLICA = "https://pruebas.barberturno.test";
    private static final String BSUID_CLIENTE = "whatsapp:PE.CT18MULTICANAL01";

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

    @BeforeAll
    static void asegurarNegociosAntesDelContexto() {
        if (!servidorTwilio.estaIniciado()) {
            servidorTwilio.iniciar();
        }
        String url = System.getProperty("BT_DB_URL", System.getenv("BT_DB_URL"));
        if (url == null || url.isBlank()) url = "jdbc:postgresql://localhost:5433/barberturno_v2_test";
        String user = System.getProperty("BT_FLYWAY_USER", System.getenv("BT_FLYWAY_USER"));
        if (user == null || user.isBlank()) user = "barberturno_v2";
        String pass = System.getProperty("BT_FLYWAY_PASSWORD", System.getenv("BT_FLYWAY_PASSWORD"));
        if (pass == null) pass = "";
        try (var conexion = DriverManager.getConnection(url, user, pass);
             var stmt = conexion.prepareStatement("""
                     INSERT INTO negocio (slug, nombre, creado_en, actualizado_en)
                     VALUES ('sur', 'Barbería Sur', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                     ON CONFLICT (slug) DO NOTHING
                     """)) {
            stmt.executeUpdate();
        } catch (Exception ignored) {
            // InicializadorNegocios lo completará con el Environment de Spring
        }
    }

    static class InicializadorNegocios implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            try (var conexion = DriverManager.getConnection(
                    env.getRequiredProperty("spring.datasource.url"),
                    env.getRequiredProperty("spring.flyway.user"),
                    env.getRequiredProperty("spring.flyway.password"));
                 var stmt = conexion.prepareStatement("""
                         INSERT INTO negocio (slug, nombre, creado_en, actualizado_en)
                         VALUES ('sur', 'Barbería Sur', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                         ON CONFLICT (slug) DO NOTHING
                         """)) {
                stmt.executeUpdate();
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo inicializar el negocio sur antes del contexto.", e);
            }
        }
    }

    @LocalServerPort private int puerto;
    @Autowired private DirectorioNegocios directorio;
    @Autowired private EjecutorEnNegocio ejecutor;
    @Autowired private JdbcNegocioPruebas sql;
    @Autowired private RelojAjustable reloj;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private BarberoRepository barberos;
    @Autowired private ServicioRepository servicios;
    @Autowired private ClienteRepository clientes;
    @Autowired private ReservaRepository reservas;
    @Autowired private JornadaRepository jornadas;
    @Autowired private ProcesadorEventosWhatsApp procesador;
    @Autowired private EnviadorSalidaWhatsApp enviador;
    @Autowired private JwtService jwt;
    @Autowired private CorrespondenciaMenusTwilio menus;
    @Autowired private CorrespondenciaDireccionesTwilio direcciones;
    @Autowired private BuzonRespuestasTwilio buzon;
    @Autowired private JsonMapper mapper;
    @Autowired private RegistroCanalTwilioService registroCanal;
    @Autowired private Environment entorno;

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private ExecutorService ejecutorHilos;
    private long negocioAId;
    private long negocioBId;
    private Usuario titularA;
    private Usuario titularB;
    private Map<String, Object> estadoOriginalNegocioA;
    private String ultimoTextoRecibido;

    @BeforeEach
    void preparar() {
        if (!servidorTwilio.estaIniciado()) servidorTwilio.iniciar();
        servidorTwilio.reiniciar();
        ejecutorHilos = Executors.newCachedThreadPool();
        LimpiezaBaseDatos.limpiar(sql);
        reloj.fijar(Instant.parse("2026-10-01T13:00:00Z"));

        // Re-crear negocio 'sur' si fue eliminado por LimpiezaBaseDatos
        try (var conexion = DriverManager.getConnection(
                entorno.getRequiredProperty("spring.datasource.url"),
                entorno.getRequiredProperty("spring.flyway.user"),
                entorno.getRequiredProperty("spring.flyway.password"));
             var stmt = conexion.prepareStatement("""
                     INSERT INTO negocio (slug, nombre, creado_en, actualizado_en)
                     VALUES ('sur', 'Barbería Sur', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                     ON CONFLICT (slug) DO NOTHING
                     """)) {
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo re-crear el negocio sur en la prueba.", e);
        }

        // Registrar canales del Sandbox para barberturno y sur
        registroCanal.registrar();

        negocioAId = directorio.buscar("barberturno").orElseThrow();
        negocioBId = directorio.buscar("sur").orElseThrow();

        // Guardar valores originales de negocio barberturno para restaurarlos en @AfterEach (Item 9)
        estadoOriginalNegocioA = sql.queryForMap(
                "SELECT estado, confirmacion_manual, limite_reservas_activas FROM negocio WHERE id = ?", negocioAId);

        titularA = usuarios.save(new Usuario("Admin A", "admina@ejemplo.test", "999000001", "hash-ficticio-no-utilizable", Rol.ADMIN, reloj.instant(), reloj.instant()));
        titularB = usuarios.save(new Usuario("Admin B", "adminb@ejemplo.test", "999000002", "hash-ficticio-no-utilizable", Rol.ADMIN, reloj.instant(), reloj.instant()));

        ejecutor.ejecutar(negocioAId, () -> {
            sql.update("UPDATE negocio SET limite_reservas_activas = 5, confirmacion_manual = false WHERE id = ?", negocioAId);
            sql.update("INSERT INTO membresia (negocio_id, usuario_id, rol, estado, creado_en, actualizado_en) VALUES (?, ?, 'PROPIETARIO', 'ACTIVA', ?, ?)",
                    negocioAId, titularA.getId(), java.sql.Timestamp.from(reloj.instant()), java.sql.Timestamp.from(reloj.instant()));
            return null;
        });

        ejecutor.ejecutar(negocioBId, () -> {
            sql.update("UPDATE negocio SET limite_reservas_activas = 5, confirmacion_manual = false WHERE id = ?", negocioBId);
            sql.update("INSERT INTO membresia (negocio_id, usuario_id, rol, estado, creado_en, actualizado_en) VALUES (?, ?, 'PROPIETARIO', 'ACTIVA', ?, ?)",
                    negocioBId, titularB.getId(), java.sql.Timestamp.from(reloj.instant()), java.sql.Timestamp.from(reloj.instant()));
            return null;
        });
    }

    @AfterEach
    void restaurarNegocioA() {
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
        if (estadoOriginalNegocioA != null) {
            ejecutor.ejecutar(negocioAId, () -> {
                sql.update("""
                        UPDATE negocio SET estado = ?, confirmacion_manual = ?, limite_reservas_activas = ?
                        WHERE id = ?""",
                        estadoOriginalNegocioA.get("estado"),
                        estadoOriginalNegocioA.get("confirmacion_manual"),
                        estadoOriginalNegocioA.get("limite_reservas_activas"),
                        negocioAId);
                return null;
            });
        }
        LimpiezaBaseDatos.limpiar(sql);
    }

    @AfterAll
    static void cerrarServidor() {
        servidorTwilio.close();
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

    private HttpResponse<String> simularMensajeEntranteTwiml(String sid, String cuerpo, String numeroDestino, String from) throws Exception {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("AccountSid", ACCOUNT_SID);
        datos.put("MessageSid", sid);
        datos.put("From", from);
        datos.put("To", "whatsapp:" + numeroDestino);
        datos.put("ProfileName", "Cliente CT18");
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

        ultimoTextoRecibido = desescaparTwiml(respuesta.body());
        return respuesta;
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

    private List<Reserva> reservasDelNegocio(long negId) {
        return ejecutor.ejecutar(negId, () -> reservas.findAll());
    }

    @Test
    void configuracion_conNumerosOSlugsDuplicados_fallaAlConstruir() {
        // Números duplicados
        assertThatThrownBy(() -> new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_A, URL_PUBLICA, "barberturno",
                Duration.ofSeconds(3), "https://api.twilio.com", true, Duration.ofSeconds(10), Duration.ofMillis(1500),
                List.of(
                        new TwilioPropiedades.Canal("+14155238886", "barberturno"),
                        new TwilioPropiedades.Canal("+14155238886", "sur")
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Configuración de canales Twilio duplicada.");

        // Slugs duplicados
        assertThatThrownBy(() -> new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_A, URL_PUBLICA, "barberturno",
                Duration.ofSeconds(3), "https://api.twilio.com", true, Duration.ofSeconds(10), Duration.ofMillis(1500),
                List.of(
                        new TwilioPropiedades.Canal("+14155238886", "barberturno"),
                        new TwilioPropiedades.Canal("+15005550006", "barberturno")
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Configuración de canales Twilio duplicada.");
    }

    @Test
    void mismoBsuid_conMenusAbiertosEnAyB_noLosCruza() {
        String waIdSintetico = direcciones.identificar(null, BSUID_CLIENTE).orElseThrow();

        // 1. Instalar menú en canal A (barberturno) con opciones del servicio A
        menus.guardar(PHONE_ID_A, waIdSintetico, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:A1", "Servicio Negocio A"),
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:A2", "Otro Servicio Negocio A")
        ), "Menú negocio A");

        // 2. Instalar menú en canal B (sur) con opciones del servicio B
        menus.guardar(PHONE_ID_B, waIdSintetico, List.of(
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:B1", "Servicio Negocio B"),
                new CorrespondenciaMenusTwilio.Seleccion("list_reply", "s:B2", "Otro Servicio Negocio B")
        ), "Menú negocio B");

        // 3. Consumir opción 1 en canal A: resuelve a s:A1
        var consumoA = menus.consumir(PHONE_ID_A, waIdSintetico, "1", "SM_CONSUMO_A");
        assertThat(consumoA).isPresent();
        assertThat(consumoA.get().id()).isEqualTo("s:A1");

        // 4. Consumir opción 1 en canal B: resuelve a s:B1 y no fue alterado por el consumo de A
        var consumoB = menus.consumir(PHONE_ID_B, waIdSintetico, "1", "SM_CONSUMO_B");
        assertThat(consumoB).isPresent();
        assertThat(consumoB.get().id()).isEqualTo("s:B1");

        // 5. El menú de A ya fue consumido (no quedan opciones para opción 1), el de B también
        assertThat(menus.consumir(PHONE_ID_A, waIdSintetico, "1", "SM_CONSUMO_A_BIS")).isEmpty();
        assertThat(menus.consumir(PHONE_ID_B, waIdSintetico, "1", "SM_CONSUMO_B_BIS")).isEmpty();
    }

    @Test
    void fromRest_correspondeAlCanalDelNegocio() {
        TwilioPropiedades propiedadesMulti = new TwilioPropiedades(ACCOUNT_SID, AUTH_TOKEN, NUMERO_A, URL_PUBLICA, "barberturno",
                Duration.ofSeconds(3), servidorTwilio.urlBase(), false, Duration.ofSeconds(10), Duration.ofMillis(1500),
                List.of(
                        new TwilioPropiedades.Canal(NUMERO_A, "barberturno"),
                        new TwilioPropiedades.Canal(NUMERO_B, "sur")
                ));

        ClienteTwilioWhatsApp clienteTwilio = new ClienteTwilioWhatsApp(propiedadesMulti, menus, mapper, direcciones, buzon);

        String e164Destino = "51999123456";
        direcciones.identificar(e164Destino, "whatsapp:+" + e164Destino);

        // Envío para negocio A (phone_number_id = PHONE_ID_A)
        servidorTwilio.encolarExito("SM_REST_FROM_A");
        ObjectNode cuerpoA = mapper.createObjectNode();
        cuerpoA.put("messaging_product", "whatsapp").put("to", e164Destino).put("type", "text");
        cuerpoA.putObject("text").put("body", "Mensaje de barberturno");
        clienteTwilio.enviar(PHONE_ID_A, cuerpoA);

        var peticionA = servidorTwilio.ultimaPeticion();
        assertThat(peticionA.parametros().get("From")).isEqualTo("whatsapp:" + NUMERO_A);

        // Envío para negocio B (phone_number_id = PHONE_ID_B)
        servidorTwilio.encolarExito("SM_REST_FROM_B");
        ObjectNode cuerpoB = mapper.createObjectNode();
        cuerpoB.put("messaging_product", "whatsapp").put("to", e164Destino).put("type", "text");
        cuerpoB.putObject("text").put("body", "Mensaje de sur");
        clienteTwilio.enviar(PHONE_ID_B, cuerpoB);

        var peticionB = servidorTwilio.ultimaPeticion();
        assertThat(peticionB.parametros().get("From")).isEqualTo("whatsapp:" + NUMERO_B);
    }

    @Test
    void dosNumeros_creanReservaEnLaAgendaDeSuPropioNegocio() throws Exception {
        // Preparar catálogo y barbero en negocio A (barberturno)
        ejecutor.ejecutar(negocioAId, () -> {
            Usuario uA = usuarios.save(new Usuario("Barbero A", "barberoa-ct18@ejemplo.test", "999000003", "hash-ficticio", Rol.BARBERO, reloj.instant(), reloj.instant()));
            membresias.save(new Membresia(negocioAId, uA.getId(), RolNegocio.BARBERO, reloj.instant()));
            Barbero bA = barberos.save(new Barbero(uA, "Barbero Carlos A", reloj.instant()));
            servicios.save(new Servicio("Corte clásico A", "Descripción A", (short) 30, new BigDecimal("25.00"), reloj.instant()));
            for (short dia = 1; dia <= 7; dia++) {
                jornadas.save(new Jornada(bA, dia, LocalTime.of(12, 0), LocalTime.of(18, 0)));
            }
            return null;
        });

        // Preparar catálogo y barbero en negocio B (sur)
        ejecutor.ejecutar(negocioBId, () -> {
            Usuario uB = usuarios.save(new Usuario("Barbero B", "barberob-ct18@ejemplo.test", "999000004", "hash-ficticio", Rol.BARBERO, reloj.instant(), reloj.instant()));
            membresias.save(new Membresia(negocioBId, uB.getId(), RolNegocio.BARBERO, reloj.instant()));
            Barbero bB = barberos.save(new Barbero(uB, "Barbero Mateo B", reloj.instant()));
            servicios.save(new Servicio("Corte clásico B", "Descripción B", (short) 30, new BigDecimal("35.00"), reloj.instant()));
            for (short dia = 1; dia <= 7; dia++) {
                jornadas.save(new Jornada(bB, dia, LocalTime.of(12, 0), LocalTime.of(18, 0)));
            }
            return null;
        });

        // 1. Completar reserva de extremo a extremo en negocio A mediante webhooks firmados dirigidos al número A
        String fromClienteA = "whatsapp:PE.CT18CLI01";
        simularMensajeEntranteTwiml("SM_A_000", "hola", NUMERO_A, fromClienteA);
        int pasoA = 1;
        while (reservasDelNegocio(negocioAId).isEmpty()) {
            assertThat(pasoA).as("pasos para reservar en negocio A").isLessThanOrEqualTo(12);
            String texto = ultimoTextoRecibido;
            String eleccion;
            if (tieneOpcion(texto, "Aceptar")) eleccion = opcion(texto, "Aceptar");
            else if (tieneOpcion(texto, "Confirmar")) eleccion = opcion(texto, "Confirmar");
            else if (tieneOpcion(texto, "Reservar")) eleccion = opcion(texto, "Reservar");
            else if (tieneOpcion(texto, "Corte clásico A")) eleccion = opcion(texto, "Corte clásico A");
            else if (tieneOpcion(texto, "Barbero Carlos A")) eleccion = opcion(texto, "Barbero Carlos A");
            else eleccion = opcion(texto, "");
            simularMensajeEntranteTwiml("SM_A_R%02d".formatted(pasoA++), eleccion, NUMERO_A, fromClienteA);
        }

        var reservasA = reservasDelNegocio(negocioAId);
        assertThat(reservasA).as("debe existir exactamente una reserva en negocio A").hasSize(1);
        assertThat(reservasDelNegocio(negocioBId)).as("negocio B no debe tener reservas todavía").isEmpty();
        long resAId = reservasA.getFirst().getId();
        assertThat(reservasA.getFirst().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);

        // 2. Completar reserva de extremo a extremo en negocio B mediante webhooks firmados dirigidos al número B
        String fromClienteB = "whatsapp:PE.CT18CLI02";
        simularMensajeEntranteTwiml("SM_B_000", "hola", NUMERO_B, fromClienteB);
        int pasoB = 1;
        while (reservasDelNegocio(negocioBId).isEmpty()) {
            assertThat(pasoB).as("pasos para reservar en negocio B").isLessThanOrEqualTo(12);
            String texto = ultimoTextoRecibido;
            String eleccion;
            if (tieneOpcion(texto, "Aceptar")) eleccion = opcion(texto, "Aceptar");
            else if (tieneOpcion(texto, "Confirmar")) eleccion = opcion(texto, "Confirmar");
            else if (tieneOpcion(texto, "Reservar")) eleccion = opcion(texto, "Reservar");
            else if (tieneOpcion(texto, "Corte clásico B")) eleccion = opcion(texto, "Corte clásico B");
            else if (tieneOpcion(texto, "Barbero Mateo B")) eleccion = opcion(texto, "Barbero Mateo B");
            else eleccion = opcion(texto, "");
            simularMensajeEntranteTwiml("SM_B_R%02d".formatted(pasoB++), eleccion, NUMERO_B, fromClienteB);
        }

        var reservasB = reservasDelNegocio(negocioBId);
        assertThat(reservasB).as("debe existir exactamente una reserva en negocio B").hasSize(1);
        long resBId = reservasB.getFirst().getId();
        assertThat(reservasB.getFirst().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);

        // Verificar que negocio A sigue teniendo exactamente 1 reserva
        assertThat(reservasDelNegocio(negocioAId)).as("negocio A debe seguir teniendo exactamente 1 reserva").hasSize(1);

        // 3. Comprobar agenda administrativa de negocio A: solo contiene resA, no resB
        String tokenA = jwt.emitir(titularA);
        HttpRequest reqAgendaA = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/n/barberturno/reservas?desde=2026-10-01&hasta=2026-12-31"))
                .header("Cookie", "BT_SESION=" + tokenA)
                .GET().build();
        HttpResponse<String> respA = clienteHttp.send(reqAgendaA, HttpResponse.BodyHandlers.ofString());
        assertThat(respA.statusCode()).isEqualTo(200);
        assertThat(respA.body()).contains("\"id\":" + resAId);
        assertThat(respA.body()).doesNotContain("\"id\":" + resBId);

        // 4. Comprobar agenda administrativa de negocio B: solo contiene resB, no resA
        String tokenB = jwt.emitir(titularB);
        HttpRequest reqAgendaB = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/n/sur/reservas?desde=2026-10-01&hasta=2026-12-31"))
                .header("Cookie", "BT_SESION=" + tokenB)
                .GET().build();
        HttpResponse<String> respB = clienteHttp.send(reqAgendaB, HttpResponse.BodyHandlers.ofString());
        assertThat(respB.statusCode()).isEqualTo(200);
        assertThat(respB.body()).contains("\"id\":" + resBId);
        assertThat(respB.body()).doesNotContain("\"id\":" + resAId);
    }
}
