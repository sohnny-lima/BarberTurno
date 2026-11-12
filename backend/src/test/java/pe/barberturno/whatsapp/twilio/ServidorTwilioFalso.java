package pe.barberturno.whatsapp.twilio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
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

/**
 * Servidor HTTP falso para simular la API REST de Twilio Sandbox en pruebas locales
 * ({@code POST /2010-04-01/Accounts/{AccountSid}/Messages.json}, 20-v2-twilio-sandbox.md §2 y §4.3).
 * Registra cada petición (método, ruta, cabeceras, cuerpo crudo y parámetros del formulario) y permite
 * encolar respuestas de éxito (201 con sid), errores estructurados de Twilio (400, 429, 5xx), demoras y cortes.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
public class ServidorTwilioFalso implements AutoCloseable {

    private HttpServer server;
    private ExecutorService ejecutor;
    private final List<PeticionRegistrada> peticiones = new CopyOnWriteArrayList<>();
    private final Queue<AccionRespuesta> colaRespuestas = new ConcurrentLinkedQueue<>();
    private final AtomicInteger peticionesActivas = new AtomicInteger();
    private final Set<Thread> hilosActivos = ConcurrentHashMap.newKeySet();
    private final AtomicInteger contadorSid = new AtomicInteger(100);
    private volatile AccionRespuesta respuestaPorDefecto;
    private volatile boolean iniciado;

    /**
     * Registro inmutable de una petición HTTP recibida por el servidor falso.
     * @param metodo método HTTP (por ejemplo {@code POST})
     * @param ruta ruta de la petición (por ejemplo {@code /2010-04-01/Accounts/AC.../Messages.json})
     * @param cabeceras cabeceras HTTP recibidas
     * @param cuerpoTexto cuerpo de la petición decodificado como texto UTF-8
     * @param parametros mapa ordenado de los parámetros del formulario urlencoded
     * @author Sohnny Walter Lima Infanzón
     * @version 2.0
     */
    public record PeticionRegistrada(String metodo, String ruta, Map<String, List<String>> cabeceras,
            String cuerpoTexto, Map<String, String> parametros) {

        /**
         * Obtiene el valor de la cabecera Authorization enviada por el cliente.
         * @return valor de la cabecera o cadena vacía si no fue enviada
         */
        public String autorizacion() {
            List<String> valores = cabeceras.get("Authorization");
            return (valores != null && !valores.isEmpty()) ? valores.getFirst() : "";
        }
    }

    @FunctionalInterface
    private interface AccionRespuesta {
        void responder(HttpExchange exchange) throws IOException;
    }

    /**
     * Inicializa el servidor en un puerto efímero de {@code 127.0.0.1}.
     */
    public ServidorTwilioFalso() {
        crearServidor();
    }

    private synchronized void crearServidor() {
        try {
            if (this.ejecutor == null || this.ejecutor.isShutdown()) {
                this.ejecutor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "servidor-twilio-falso");
                    t.setDaemon(true);
                    return t;
                });
            }
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.setExecutor(this.ejecutor);
            this.server.createContext("/", this::atender);
            this.respuestaPorDefecto = (exchange) -> {
                String sid = "SM" + String.format("%032d", contadorSid.incrementAndGet());
                responderJson(exchange, 201, "{\"sid\":\"" + sid + "\",\"status\":\"queued\"}");
            };
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el servidor HTTP falso de Twilio.", e);
        }
    }

    /**
     * Arranca el servidor HTTP en segundo plano si no está iniciado.
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

    /**
     * Comprueba si el servidor falso está en ejecución.
     * @return true si el servidor ha sido iniciado
     */
    public synchronized boolean estaIniciado() {
        return iniciado;
    }

    /**
     * Drena las peticiones HTTP activas interrumpiendo hilos en espera y aguardando a que termine el procesamiento.
     * @param limite tiempo máximo de espera
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
     * Reinicia la lista de peticiones recibidas y vacía la cola de respuestas.
     */
    public synchronized void reiniciar() {
        limpiar();
    }

    /**
     * Limpia peticiones activas, registradas y respuestas programadas.
     */
    public void limpiar() {
        drenarPeticionesActivas(Duration.ofSeconds(2));
        peticiones.clear();
        colaRespuestas.clear();
    }

    /**
     * Obtiene el puerto efímero asignado por el sistema operativo.
     * @return puerto TCP local
     */
    public synchronized int puerto() {
        if (server == null) {
            crearServidor();
        }
        return server.getAddress().getPort();
    }

    /**
     * Obtiene la URL base HTTP para configurar {@code apiBase} en pruebas locales.
     * @return URL base local, por ejemplo {@code http://127.0.0.1:54321}
     */
    public String urlBase() {
        return "http://127.0.0.1:" + puerto();
    }

    /**
     * Obtiene una copia inmutable de las peticiones recibidas hasta el momento.
     * @return lista de peticiones registradas
     */
    public List<PeticionRegistrada> peticiones() {
        return List.copyOf(peticiones);
    }

    /**
     * Obtiene la última petición recibida.
     * @return última petición registrada
     * @throws IllegalStateException si no hay ninguna petición registrada
     */
    public PeticionRegistrada ultimaPeticion() {
        if (peticiones.isEmpty()) {
            throw new IllegalStateException("No hay peticiones registradas en el servidor falso de Twilio.");
        }
        return peticiones.getLast();
    }

    /**
     * Encola una respuesta de éxito 201 con el identificador {@code sid} especificado.
     * @param sid identificador único del mensaje generado por Twilio
     */
    public void encolarExito(String sid) {
        colaRespuestas.add(exchange -> responderJson(exchange, 201,
                "{\"sid\":\"" + sid + "\",\"status\":\"queued\"}"));
    }

    /**
     * Encola una respuesta de error estructurado de Twilio con código numérico propio.
     * @param estadoHttp código de estado HTTP (por ejemplo 400, 429 o 500)
     * @param codigoTwilio código de error de Twilio en el campo {@code code}
     * @param mensaje descripción del error en el campo {@code message}
     */
    public void encolarError(int estadoHttp, int codigoTwilio, String mensaje) {
        String cuerpo = "{\"code\":" + codigoTwilio + ",\"message\":\"" + mensaje
                + "\",\"status\":" + estadoHttp + "}";
        colaRespuestas.add(exchange -> responderJson(exchange, estadoHttp, cuerpo));
    }

    /**
     * Encola una respuesta HTTP genérica con el estado y cuerpo dados.
     * @param estadoHttp código HTTP
     * @param cuerpo contenido del cuerpo como texto UTF-8
     */
    public void encolarRespuesta(int estadoHttp, String cuerpo) {
        colaRespuestas.add(exchange -> responderJson(exchange, estadoHttp, cuerpo));
    }

    /**
     * Encola un corte abrupto de la conexión sin enviar cabeceras ni cuerpo.
     */
    public void encolarCorteConexion() {
        colaRespuestas.add(HttpExchange::close);
    }

    /**
     * Encola una demora antes de responder.
     * @param espera duración de la espera
     * @param estadoHttp código HTTP a enviar tras la demora
     * @param cuerpo contenido del cuerpo
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
            Map<String, String> parametros = parsearFormulario(cuerpoTexto);

            peticiones.add(new PeticionRegistrada(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders(), cuerpoTexto, parametros));

            AccionRespuesta accion = colaRespuestas.poll();
            if (accion == null) {
                accion = respuestaPorDefecto;
            }
            try {
                accion.responder(exchange);
            } catch (IOException e) {
                // Conexión interrumpida por el cliente
            }
        } finally {
            hilosActivos.remove(hiloActual);
            peticionesActivas.decrementAndGet();
        }
    }

    private static Map<String, String> parsearFormulario(String cuerpo) {
        Map<String, String> datos = new LinkedHashMap<>();
        if (cuerpo == null || cuerpo.isBlank()) return datos;
        for (String par : cuerpo.split("&", -1)) {
            String[] partes = par.split("=", 2);
            String nombre = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length == 2 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            datos.put(nombre, valor);
        }
        return datos;
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
