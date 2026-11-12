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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import pe.barberturno.auth.JwtService;
import pe.barberturno.catalog.Servicio;
import pe.barberturno.catalog.ServicioRepository;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.customers.Cliente;
import pe.barberturno.customers.ClienteRepository;
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
 * que las reservas creadas por el número de barberturno (+14155238886) queden en la agenda de barberturno
 * y las del número de sur (+15005550006) en la de sur;
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
        "barberturno.whatsapp.twilio.respuesta-twiml=false",
        "barberturno.whatsapp.twilio.url-publica=https://pruebas.barberturno.test",
        "barberturno.whatsapp.twilio.intervalo-envio=3s"
})
@ActiveProfiles({"test", "twilio"})
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

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private long negocioAId;
    private long negocioBId;
    private Usuario titularA;
    private Usuario titularB;

    @BeforeEach
    void preparar() {
        if (!servidorTwilio.estaIniciado()) servidorTwilio.iniciar();
        servidorTwilio.reiniciar();
        LimpiezaBaseDatos.limpiar(sql);
        reloj.reiniciar();

        negocioAId = directorio.buscar("barberturno").orElseThrow();
        negocioBId = directorio.buscar("sur").orElseThrow();

        titularA = usuarios.save(new Usuario("adminA@ejemplo.test", "hash", "Admin A", Rol.CLIENTE));
        titularB = usuarios.save(new Usuario("adminB@ejemplo.test", "hash", "Admin B", Rol.CLIENTE));

        ejecutor.ejecutar(negocioAId, () -> {
            sql.update("UPDATE negocio SET limite_reservas_activas = 5, confirmacion_manual = false WHERE id = ?", negocioAId);
            sql.update("INSERT INTO membresia (negocio_id, usuario_id, rol, estado, creado_en) VALUES (?, ?, 'PROPIETARIO', 'ACTIVA', ?)",
                    negocioAId, titularA.getId(), java.sql.Timestamp.from(reloj.instant()));
            return null;
        });

        ejecutor.ejecutar(negocioBId, () -> {
            sql.update("UPDATE negocio SET limite_reservas_activas = 5, confirmacion_manual = false WHERE id = ?", negocioBId);
            sql.update("INSERT INTO membresia (negocio_id, usuario_id, rol, estado, creado_en) VALUES (?, ?, 'PROPIETARIO', 'ACTIVA', ?)",
                    negocioBId, titularB.getId(), java.sql.Timestamp.from(reloj.instant()));
            return null;
        });
    }

    @AfterAll
    static void cerrarServidor() {
        servidorTwilio.close();
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
        long barberoAId = ejecutor.ejecutar(negocioAId, () -> {
            Usuario u = usuarios.save(new Usuario("barberoA@ejemplo.test", "hash", "Barbero A", Rol.BARBERO));
            Barbero b = barberos.save(new Barbero(u, "Barbero A"));
            Servicio s = servicios.save(new Servicio("Corte A", (short) 30, new BigDecimal("25.00")));
            jornadas.save(new Jornada(b, (short) reloj.instant().atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue(),
                    java.time.LocalTime.of(8, 0), java.time.LocalTime.of(20, 0)));
            return b.getId();
        });

        // Preparar catálogo y barbero en negocio B (sur)
        long barberoBId = ejecutor.ejecutar(negocioBId, () -> {
            Usuario u = usuarios.save(new Usuario("barberoB@ejemplo.test", "hash", "Barbero B", Rol.BARBERO));
            Barbero b = barberos.save(new Barbero(u, "Barbero B"));
            Servicio s = servicios.save(new Servicio("Corte B", (short) 30, new BigDecimal("35.00")));
            jornadas.save(new Jornada(b, (short) reloj.instant().atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue(),
                    java.time.LocalTime.of(8, 0), java.time.LocalTime.of(20, 0)));
            return b.getId();
        });

        // Crear una reserva directamente bajo el contexto del negocio A para el wa_id del cliente
        String waIdCliente = "51988889999";
        Instant inicio = reloj.instant().plus(Duration.ofHours(2));
        Reserva resA = ejecutor.ejecutar(negocioAId, () -> {
            Cliente c = clientes.save(new Cliente("Cliente A", waIdCliente));
            Reserva r = new Reserva(barberos.findById(barberoAId).orElseThrow(),
                    servicios.findAll().getFirst(), c, inicio, inicio.plusSeconds(1800), new BigDecimal("25.00"));
            r.confirmar(reloj.instant());
            return reservas.save(r);
        });

        // Crear una reserva directamente bajo el contexto del negocio B para el mismo wa_id
        Reserva resB = ejecutor.ejecutar(negocioBId, () -> {
            Cliente c = clientes.save(new Cliente("Cliente B", waIdCliente));
            Reserva r = new Reserva(barberos.findById(barberoBId).orElseThrow(),
                    servicios.findAll().getFirst(), c, inicio, inicio.plusSeconds(1800), new BigDecimal("35.00"));
            r.confirmar(reloj.instant());
            return reservas.save(r);
        });

        // Comprobar agenda administrativa de negocio A: solo contiene resA, no resB
        String tokenA = jwt.emitir(titularA);
        HttpRequest reqAgendaA = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/n/barberturno/reservas?desde=2026-10-01&hasta=2026-12-31"))
                .header("Cookie", "BT_SESION=" + tokenA)
                .GET().build();
        HttpResponse<String> respA = clienteHttp.send(reqAgendaA, HttpResponse.BodyHandlers.ofString());
        assertThat(respA.statusCode()).isEqualTo(200);
        assertThat(respA.body()).contains("\"id\":" + resA.getId());
        assertThat(respA.body()).doesNotContain("\"id\":" + resB.getId());

        // Comprobar agenda administrativa de negocio B: solo contiene resB, no resA
        String tokenB = jwt.emitir(titularB);
        HttpRequest reqAgendaB = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + puerto + "/api/n/sur/reservas?desde=2026-10-01&hasta=2026-12-31"))
                .header("Cookie", "BT_SESION=" + tokenB)
                .GET().build();
        HttpResponse<String> respB = clienteHttp.send(reqAgendaB, HttpResponse.BodyHandlers.ofString());
        assertThat(respB.statusCode()).isEqualTo(200);
        assertThat(respB.body()).contains("\"id\":" + resB.getId());
        assertThat(respB.body()).doesNotContain("\"id\":" + resA.getId());
    }
}
