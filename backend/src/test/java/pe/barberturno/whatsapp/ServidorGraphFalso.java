package pe.barberturno.whatsapp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servidor HTTP falso para simular la Graph API oficial de WhatsApp Cloud en pruebas
 * ({@code POST /{versión}/{phone_number_id}/messages}, 12-v2-09-whatsapp-salida.md §2 y §4.1).
 * Permite encolar respuestas de éxito, errores estructurados de Meta, retrasos y cortes de conexión,
 * y registra cada petición recibida sin realizar llamadas reales a Meta.
 * Cuenta con ejecutor multi-hilo propio y drenado de peticiones activas para evitar bloqueos entre pruebas.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class ServidorGraphFalso implements AutoCloseable {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private HttpServer server;
    private ExecutorService ejecutor;
    private final List<PeticionRegistrada> peticiones = new CopyOnWriteArrayList<>();
    private final Queue<AccionRespuesta> colaRespuestas = new ConcurrentLinkedQueue<>();
    private final AtomicInteger peticionesActivas = new AtomicInteger();
    private final Set<Thread> hilosActivos = ConcurrentHashMap.newKeySet();
    private volatile AccionRespuesta respuestaPorDefecto;
    private volatile boolean iniciado;

    /**
     * Registro de una petición HTTP recibida por el servidor falso.
     * @param metodo método HTTP (por ejemplo {@code POST})
     * @param ruta ruta de la petición (por ejemplo {@code /v26.0/10001/messages})
     * @param cabeceras cabeceras HTTP de la petición
     * @param cuerpoTexto cuerpo de la petición como texto UTF-8
     * @param cuerpoJson cuerpo analizado como árbol JSON, o {@code null} si no es JSON válido
     * @author Sohnny Walter Lima Infanzón
     * @version 1.0
     */
    public record PeticionRegistrada(String metodo, String ruta, Map<String, List<String>> cabeceras,
            String cuerpoTexto, JsonNode cuerpoJson) { }

    @FunctionalInterface
    private interface AccionRespuesta {
        void responder(HttpExchange exchange) throws IOException;
    }

    /**
     * Inicializa el servidor en un puerto efímero de {@code 127.0.0.1}.
     */
    public ServidorGraphFalso() {
        crearServidor();
    }

    private synchronized void crearServidor() {
        try {
            if (this.ejecutor == null || this.ejecutor.isShutdown()) {
                this.ejecutor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "servidor-graph-falso");
                    t.setDaemon(true);
                    return t;
                });
            }
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.setExecutor(this.ejecutor);
            this.server.createContext("/", this::atender);
            this.respuestaPorDefecto = (exchange) -> responderJson(exchange, 200,
                    "{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"input\":\"51999000001\",\"wa_id\":\"51999000001\"}],"
                            + "\"messages\":[{\"id\":\"wamid.DEFECTO_FICTICIO\"}]}");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el servidor HTTP falso.", e);
        }
    }

    /**
     * Arranca el servidor HTTP en segundo plano si no está iniciado.
     * Si fue detenido previamente, recrea el servidor antes de arrancarlo.
     */
    public synchronized void iniciar() {
        if (!iniciado) {
            if (server == null) {
                crearServidor();
            }
            server.start();
            iniciado = true;
        }
    }

    /** {@return {@code true} si el servidor ya ha sido iniciado} */
    public synchronized boolean estaIniciado() {
        return iniciado;
    }

    /**
     * Drena las peticiones HTTP activas interrumpiendo los hilos que estén en espera demorada
     * y esperando hasta que el contador de peticiones en curso llegue a cero o venza el plazo.
     * @param limite tiempo máximo de espera para el drenado
     */
    public void drenarPeticionesActivas(Duration limite) {
        for (Thread t : hilosActivos) {
            t.interrupt();
        }
        long fin = System.currentTimeMillis() + limite.toMillis();
        while (peticionesActivas.get() > 0 && System.currentTimeMillis() < fin) {
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Reinicia el estado del servidor falso para una nueva prueba,
     * drenando las peticiones activas, vaciando la lista de peticiones recibidas
     * y la cola de respuestas programadas.
     */
    public synchronized void reiniciar() {
        limpiar();
    }

    /** {@return puerto local en el que escucha el servidor} */
    public synchronized int puerto() {
        if (server == null) {
            crearServidor();
        }
        return server.getAddress().getPort();
    }

    /** {@return URL base con el esquema y puerto efímero, por ejemplo {@code http://127.0.0.1:54321}} */
    public String urlBase() {
        return "http://127.0.0.1:" + puerto();
    }

    /**
     * Limpia la lista de peticiones recibidas y vacía la cola de respuestas programadas
     * tras drenar cualquier petición activa.
     */
    public void limpiar() {
        drenarPeticionesActivas(Duration.ofSeconds(2));
        peticiones.clear();
        colaRespuestas.clear();
    }

    /** {@return lista inmutable de las peticiones recibidas hasta el momento} */
    public List<PeticionRegistrada> peticiones() {
        return List.copyOf(peticiones);
    }

    /**
     * {@return la última petición recibida}
     * @throws IllegalStateException si no se ha recibido ninguna petición
     */
    public PeticionRegistrada ultimaPeticion() {
        if (peticiones.isEmpty()) {
            throw new IllegalStateException("No hay peticiones registradas en el servidor falso.");
        }
        return peticiones.getLast();
    }

    /**
     * Encola una respuesta exitosa con el {@code wamid} indicado.
     * @param wamid identificador devuelto en {@code messages[0].id}
     */
    public void encolarExito(String wamid) {
        colaRespuestas.add(exchange -> responderJson(exchange, 200,
                "{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"input\":\"51999000001\",\"wa_id\":\"51999000001\"}],"
                        + "\"messages\":[{\"id\":\"" + wamid + "\"}]}"));
    }

    /**
     * Encola una respuesta exitosa ejecutando una acción interceptora mientras se procesa la petición HTTP
     * (útil para verificar el estado de la base de datos antes del cierre del mensaje).
     * @param interceptor acción a ejecutar antes de enviar la respuesta HTTP
     * @param wamid identificador devuelto en {@code messages[0].id}
     */
    public void encolarInterceptando(Runnable interceptor, String wamid) {
        colaRespuestas.add(exchange -> {
            if (interceptor != null) {
                interceptor.run();
            }
            responderJson(exchange, 200,
                    "{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"input\":\"51999000001\",\"wa_id\":\"51999000001\"}],"
                            + "\"messages\":[{\"id\":\"" + wamid + "\"}]}");
        });
    }

    /**
     * Encola una respuesta de error estructurada con formato de la Graph API de Meta.
     * @param estadoHttp código HTTP de respuesta
     * @param codigoMeta código de error en {@code error.code}
     * @param mensaje texto en {@code error.message}
     */
    public void encolarErrorMeta(int estadoHttp, int codigoMeta, String mensaje) {
        String cuerpo = "{\"error\":{\"message\":\"" + mensaje
                + "\",\"type\":\"OAuthException\",\"code\":" + codigoMeta
                + ",\"error_data\":{\"details\":\"Detalle ficticio\"},\"fbtrace_id\":\"fbtrace_ficticio\"}}";
        colaRespuestas.add(exchange -> responderJson(exchange, estadoHttp, cuerpo));
    }

    /**
     * Encola una respuesta HTTP genérica con estado y cuerpo dados.
     * @param estadoHttp código de estado HTTP
     * @param cuerpo contenido del cuerpo en texto UTF-8
     */
    public void encolarRespuesta(int estadoHttp, String cuerpo) {
        colaRespuestas.add(exchange -> responderJson(exchange, estadoHttp, cuerpo));
    }

    /**
     * Encola un corte abrupto de conexión sin enviar cabeceras ni cuerpo.
     */
    public void encolarCorteConexion() {
        colaRespuestas.add(HttpExchange::close);
    }

    /**
     * Encola una respuesta de redirección HTTP 3xx con cabecera Location.
     * @param estadoHttp código HTTP de redirección (p. ej. 301 o 302)
     * @param destino URL o ruta hacia la que se redirige
     * @param cuerpo cuerpo HTML o informativo de la redirección
     */
    public void encolarRedireccion(int estadoHttp, String destino, String cuerpo) {
        colaRespuestas.add(exchange -> {
            exchange.getResponseHeaders().set("Location", destino);
            responderJson(exchange, estadoHttp, cuerpo);
        });
    }

    /**
     * Encola una demora antes de responder, útil para forzar tiempos de espera agotados.
     * Si el hilo se interrumpe durante la espera (por ejemplo, al reiniciar o drenar),
     * sale limpiamente sin intentar escribir en la conexión.
     * @param espera tiempo a esperar antes de responder
     * @param estadoHttp código HTTP
     * @param cuerpo cuerpo a enviar tras la demora
     */
    public void encolarDemora(Duration espera, int estadoHttp, String cuerpo) {
        colaRespuestas.add(exchange -> {
            try {
                Thread.sleep(espera.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                try {
                    exchange.close();
                } catch (Exception ignored) {
                }
                return;
            }
            responderJson(exchange, estadoHttp, cuerpo);
        });
    }

    private void atender(HttpExchange exchange) throws IOException {
        peticionesActivas.incrementAndGet();
        Thread hiloActual = Thread.currentThread();
        hilosActivos.add(hiloActual);
        try {
            byte[] cuerpoBytes = leerTodo(exchange.getRequestBody());
            String cuerpoTexto = new String(cuerpoBytes, StandardCharsets.UTF_8);
            JsonNode cuerpoJson = null;
            try {
                cuerpoJson = MAPPER.readTree(cuerpoBytes);
            } catch (Exception ignored) {
                // Cuerpo no JSON o vacío
            }
            peticiones.add(new PeticionRegistrada(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders(), cuerpoTexto, cuerpoJson));

            AccionRespuesta accion = colaRespuestas.poll();
            if (accion == null) {
                accion = respuestaPorDefecto;
            }
            try {
                accion.responder(exchange);
            } catch (IOException e) {
                // El cliente pudo cerrar la conexión por timeout
            }
        } finally {
            hilosActivos.remove(hiloActual);
            peticionesActivas.decrementAndGet();
        }
    }

    private static void responderJson(HttpExchange exchange, int estadoHttp, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(estadoHttp, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static byte[] leerTodo(InputStream entrada) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bloque = new byte[1024];
        int leidos;
        while ((leidos = entrada.read(bloque)) != -1) {
            buffer.write(bloque, 0, leidos);
        }
        return buffer.toByteArray();
    }

    @Override
    public synchronized void close() {
        drenarPeticionesActivas(Duration.ofMillis(500));
        if (iniciado && server != null) {
            server.stop(0);
            server = null;
            iniciado = false;
        }
        if (ejecutor != null) {
            ejecutor.shutdownNow();
            ejecutor = null;
        }
        peticiones.clear();
        colaRespuestas.clear();
    }
}
