package pe.barberturno.perf;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * Comprueba RNF-01/CP-12 con cincuenta usuarios sostenidos y mezcla 90/10 de recorridos.
 * Una creación siempre utiliza una franja recién recibida y cookies/CSRF reales.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class DisponibilidadSimulation extends Simulation {
    private static final LongAdder CREADAS = new LongAdder();
    private static final LongAdder CONFLICTOS = new LongAdder();
    private static final LongAdder LIMITES = new LongAdder();
    private static final LongAdder CONSULTAS = new LongAdder();
    private static final LongAdder RECORRIDOS_CREAR = new LongAdder();
    private static final LongAdder SIN_FRANJA = new LongAdder();

    /**
     * Configura carga cerrada de 300 segundos, drenaje final y aserciones sin relajar umbrales.
     * Las propiedades perf.usuarios/perf.segundos sirven exclusivamente para ensayos breves.
     * @throws IOException si falta el manifiesto creado por DatosCarga
     */
    public DisponibilidadSimulation() throws IOException {
        Properties datos = new Properties();
        try (var entrada = Files.newInputStream(Path.of("target/datos.properties"))) { datos.load(entrada); }
        List<Long> barberos = Arrays.stream(datos.getProperty("barberos").split(",")).map(Long::valueOf).toList();
        List<Long> servicios = Arrays.stream(datos.getProperty("servicios").split(",")).map(Long::valueOf).toList();
        LocalDate fecha = LocalDate.parse(datos.getProperty("fecha"));
        String clave = DatosCarga.requerida("BT_PERF_PASSWORD");
        String baseUrl = System.getenv().getOrDefault("BT_PERF_URL", "http://localhost:8080");
        if (!baseUrl.matches("http://(localhost|127\\.0\\.0\\.1):8080")) {
            throw new IllegalArgumentException("La carga solo se permite sobre el backend local de perf.");
        }
        HttpProtocolBuilder protocolo = http.baseUrl(baseUrl).acceptHeader("application/json")
                .contentTypeHeader("application/json").disableCaching();
        HttpRequestActionBuilder disponibilidad = http("Disponibilidad").get(session -> {
            String url = "/api/disponibilidad?servicioId=" + session.getLong("servicio") + "&fecha=" + session.getString("fecha");
            return session.getLong("barbero") == 0 ? url : url + "&barberoId=" + session.getLong("barbero");
        }).check(status().is(200), jsonPath("$.franjas").ofList().saveAs("franjas"));

        ChainBuilder consulta = exec(session -> { CONSULTAS.increment(); return session; }).exec(disponibilidad);
        ChainBuilder creacion = exec(session -> { RECORRIDOS_CREAR.increment(); return session; })
                .exec(http("XSRF inicial").get("/api/auth/sesion").check(status().is(401)))
                .exec(getCookieValue(CookieKey("XSRF-TOKEN").saveAs("xsrf")))
                .exec(http("Login cliente").post("/api/auth/login").header("X-XSRF-TOKEN", "#{xsrf}")
                        .body(StringBody(session -> "{\"correo\":\"cliente-perf-" + session.getInt("cliente")
                                + "@ejemplo.test\",\"password\":\"" + clave.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"))
                        .check(status().is(200)))
                .exitHereIfFailed()
                .exec(http("XSRF con sesión").get("/api/auth/sesion").check(status().is(200)))
                .exec(getCookieValue(CookieKey("XSRF-TOKEN").saveAs("xsrf")))
                .exec(disponibilidad).exitHereIfFailed()
                .exec(session -> {
                    List<Map<String, Object>> franjas = session.getList("franjas");
                    if (franjas.isEmpty()) { SIN_FRANJA.increment(); return session.set("hayFranja", false); }
                    Random azar = session.get("azar");
                    Map<String, Object> franja = franjas.get(azar.nextInt(franjas.size()));
                    List<?> ids = (List<?>) franja.get("barberoIds");
                    return session.set("hayFranja", true).set("inicio", franja.get("inicio"))
                            .set("destino", ((Number) ids.get(azar.nextInt(ids.size()))).longValue());
                })
                .doIf("#{hayFranja}").then(
                    exec(http("Crear reserva").post("/api/reservas").header("X-XSRF-TOKEN", "#{xsrf}")
                        .body(StringBody("{\"servicioId\":#{servicio},\"barberoId\":#{destino},\"inicio\":\"#{inicio}\"}"))
                        .check(status().in(201, 409, 422).saveAs("estadoReserva"))
                        .checkIf(session -> session.getInt("estadoReserva") != 201).then(
                            jsonPath("$.codigo").validate("rechazo esperado exacto", (codigo, session) -> {
                                if (!ResultadoReserva.esperado(session.getInt("estadoReserva"), codigo)) {
                                    throw new IllegalArgumentException("Código de rechazo inesperado");
                                }
                                return codigo;
                            }))
                        .checkIf(session -> session.getInt("estadoReserva") == 201).then(jsonPath("$.id").exists()))
                    .exec(session -> {
                        if (!session.isFailed()) {
                            switch (session.getInt("estadoReserva")) {
                                case 201 -> CREADAS.increment();
                                case 409 -> CONFLICTOS.increment();
                                case 422 -> LIMITES.increment();
                                default -> { }
                            }
                        }
                        return session;
                    }));
        ScenarioBuilder escenario = scenario("Disponibilidad y reserva")
                .exec(session -> {
                    Random azar = new Random(35L + session.userId());
                    return session.set("azar", azar).set("servicio", servicios.get(azar.nextInt(servicios.size())))
                            .set("barbero", azar.nextBoolean() ? 0L : barberos.get(azar.nextInt(barberos.size())))
                            .set("fecha", fecha.plusDays(azar.nextInt(30)).toString())
                            .set("cliente", 100 + azar.nextInt(100));
                })
                .randomSwitch().on(percent(90).then(consulta), percent(10).then(creacion))
                .pause(1);
        int usuarios = Integer.getInteger("perf.usuarios", 50);
        int segundos = Integer.getInteger("perf.segundos", 300);
        setUp(escenario.injectClosed(constantConcurrentUsers(usuarios).during(segundos))).protocols(protocolo)
                .assertions(details("Disponibilidad").responseTime().percentile(95).lte(2000),
                        details("Disponibilidad").failedRequests().percent().lt(1.0),
                        global().failedRequests().percent().lt(1.0),
                        details("Crear reserva").successfulRequests().count().gt(0L));
    }

    /**
     * Conserva conteos agregados sin sesiones ni credenciales y exige al menos una reserva nueva.
     * @throws UncheckedIOException si no se puede escribir el resumen de negocio
     * @throws IllegalStateException si no se creó ninguna reserva real durante el ensayo
     */
    @Override
    public void after() {
        try {
            Files.writeString(Path.of("target/resultado-negocio.properties"), "creadas=" + CREADAS.sum()
                        + "\nconflictosEsperados=" + CONFLICTOS.sum() + "\nlimitesEsperados=" + LIMITES.sum()
                        + "\nrecorridosConsulta=" + CONSULTAS.sum() + "\nrecorridosCrear=" + RECORRIDOS_CREAR.sum()
                        + "\nsinFranja=" + SIN_FRANJA.sum() + "\n");
        } catch (IOException error) { throw new UncheckedIOException(error); }
        if (CREADAS.sum() == 0) throw new IllegalStateException("La carga no creó ninguna reserva real.");
    }
}
