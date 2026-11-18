package pe.barberturno.conversation.ia;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Pattern;

/**
 * Adaptador falso de {@link ModeloConversacional} para pruebas deterministas y arnés de evaluación
 * (17-v2-12 §4.1; V2-RNF-05).
 * Permite programar una cola de respuestas exitosas (texto o llamadas a herramientas) o de fallos
 * (con {@link MotivoFalloModelo}), y registra cada {@link PeticionModelo} recibida para verificar
 * que no se filtren datos personales ni identificadores internos. Permite además registrar un gancho
 * de ejecución previo para simular eventos concurrentes durante la llamada HTTP (H-XI-05).
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.2
 */
public class ModeloConversacionalFalso implements ModeloConversacional {

    /**
     * Uso de tokens por defecto para respuestas sintéticas programadas sin métricas explícitas.
     */
    public static final UsoModelo USO_DEFECTO = new UsoModelo("claude-falso", 100, 50, 0, 0, 150);

    private static final Pattern PATRON_TELEFONO_PERU = Pattern.compile("\\b(?:\\+?51)?9\\d{8}\\b");
    private static final Pattern PATRON_CORREO = Pattern.compile("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PATRON_WA_ID = Pattern.compile("\\b519\\d{8}\\b");
    private static final Pattern PATRON_MENSAJE_PERSONAL = Pattern.compile("(?i)\\b(?:PERSONA|MENSAJE_PERSONAL)\\b");

    private final Queue<ElementoProgramado> cola = new ArrayDeque<>();
    private final List<PeticionModelo> peticionesRecibidas = new ArrayList<>();
    private int contadorLlamadasHerramienta = 0;
    private Runnable ganchoAntes = () -> { };

    /**
     * Elemento en la cola programable: o respuesta exitosa o fallo de modelo.
     */
    private sealed interface ElementoProgramado {
        record Exito(RespuestaModelo respuesta) implements ElementoProgramado { }
        record Error(FalloModelo fallo) implements ElementoProgramado { }
    }

    /** Constructor por defecto para pruebas y configuración de Spring. */
    public ModeloConversacionalFalso() { }

    @Override
    public synchronized RespuestaModelo responder(PeticionModelo peticion) {
        Objects.requireNonNull(peticion, "peticion");
        peticionesRecibidas.add(peticion);

        Runnable gancho = this.ganchoAntes;
        this.ganchoAntes = () -> { };
        gancho.run();

        if (cola.isEmpty()) {
            throw new FalloModelo(MotivoFalloModelo.SALIDA_INVALIDA, Optional.empty(), null);
        }

        ElementoProgramado elemento = cola.poll();
        if (elemento instanceof ElementoProgramado.Error err) {
            throw err.fallo();
        } else if (elemento instanceof ElementoProgramado.Exito ex) {
            return ex.respuesta();
        } else {
            throw new IllegalStateException("Tipo de elemento no soportado.");
        }
    }

    /**
     * Establece una acción a ejecutar justo antes de responder a una petición (CI-11; H-XI-05).
     * El gancho se invoca y se limpia en la siguiente llamada a {@link #responder(PeticionModelo)}.
     *
     * @param gancho acción a ejecutar antes de responder, o {@code null} para limpiar
     */
    public synchronized void fijarGanchoAntes(Runnable gancho) {
        this.ganchoAntes = (gancho != null) ? gancho : () -> { };
    }

    /**
     * Programa una respuesta completa en la cola.
     *
     * @param respuesta respuesta a devolver cuando toque su turno
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarRespuesta(RespuestaModelo respuesta) {
        Objects.requireNonNull(respuesta, "respuesta");
        cola.add(new ElementoProgramado.Exito(respuesta));
        return this;
    }

    /**
     * Programa una respuesta de texto simple para el cliente.
     *
     * @param texto contenido textual de la respuesta
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarTexto(String texto) {
        RespuestaModelo resp = new RespuestaModelo(
                Optional.ofNullable(texto),
                List.of(),
                ContinuacionProveedor.VACIA,
                USO_DEFECTO,
                "end_turn"
        );
        cola.add(new ElementoProgramado.Exito(resp));
        return this;
    }

    /**
     * Crea una llamada a herramienta con un identificador correlativo único para esta sesión de prueba.
     *
     * @param herramienta herramienta invocada
     * @param argumentosJson argumentos serializados en JSON
     * @return instancia de {@link LlamadaHerramienta}
     */
    public synchronized LlamadaHerramienta nuevaLlamada(HerramientaIA herramienta, String argumentosJson) {
        Objects.requireNonNull(herramienta, "herramienta");
        Objects.requireNonNull(argumentosJson, "argumentosJson");
        int idNum = ++contadorLlamadasHerramienta;
        return new LlamadaHerramienta("call_" + idNum, herramienta, argumentosJson);
    }

    /**
     * Construye el objeto JSON estricto de argumentos para la herramienta terminal {@code responder_al_cliente}
     * (17-v2-12 §13.2; V2-DA-86).
     *
     * @param intencion intención de respuesta elegida
     * @param falta dato faltante cuando {@code intencion} es {@link IntencionRespuesta#PEDIR_DATO}, o {@code null}
     * @param servicio alias de servicio en sesión (p. ej. {@code "s1"}), o {@code null}
     * @param profesional alias de profesional en sesión (p. ej. {@code "p1"} o {@code "cualquiera"}), o {@code null}
     * @return cadena JSON con las cuatro propiedades requeridas por el esquema estricto
     */
    public static String argumentosResponderAlCliente(
            IntencionRespuesta intencion,
            DatoFaltante falta,
            String servicio,
            String profesional
    ) {
        Objects.requireNonNull(intencion, "intencion");
        return "{"
                + "\"intencion\":" + valorJson(intencion.name()) + ","
                + "\"falta\":" + valorJson(falta != null ? falta.name() : null) + ","
                + "\"servicio\":" + valorJson(servicio) + ","
                + "\"profesional\":" + valorJson(profesional)
                + "}";
    }

    /**
     * Crea una {@link LlamadaHerramienta} para {@link HerramientaIA#RESPONDER_AL_CLIENTE} con los argumentos indicados.
     *
     * @param intencion intención de respuesta elegida
     * @param falta dato faltante si aplica, o {@code null}
     * @param servicio alias de servicio o {@code null}
     * @param profesional alias de profesional ({@code "p1"}, {@code "cualquiera"}) o {@code null}
     * @return llamada lista para incluirse en una {@link RespuestaModelo}
     */
    public synchronized LlamadaHerramienta llamadaResponderAlCliente(
            IntencionRespuesta intencion,
            DatoFaltante falta,
            String servicio,
            String profesional
    ) {
        return nuevaLlamada(
                HerramientaIA.RESPONDER_AL_CLIENTE,
                argumentosResponderAlCliente(intencion, falta, servicio, profesional)
        );
    }

    /**
     * Programa una llamada a herramienta con sus argumentos en formato JSON.
     *
     * @param herramienta herramienta invocada
     * @param argumentosJson argumentos en JSON string
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarLlamada(HerramientaIA herramienta, String argumentosJson) {
        return programarTextoYLlamada(null, herramienta, argumentosJson);
    }

    /**
     * Programa en la misma respuesta un texto libre (p. ej. malicioso o paráfrasis) y una llamada a herramienta
     * (17-v2-12 §13.2; CI-14).
     *
     * @param texto texto libre devuelto por el proveedor en la misma respuesta, o {@code null}
     * @param herramienta herramienta invocada
     * @param argumentosJson argumentos en formato JSON
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarLlamada(
            String texto,
            HerramientaIA herramienta,
            String argumentosJson
    ) {
        return programarTextoYLlamada(texto, herramienta, argumentosJson);
    }

    /**
     * Programa en la misma respuesta un texto libre (p. ej. malicioso o paráfrasis) y una llamada a herramienta
     * (17-v2-12 §13.2; CI-14).
     *
     * @param texto texto libre devuelto por el proveedor en la misma respuesta, o {@code null}
     * @param herramienta herramienta invocada
     * @param argumentosJson argumentos en formato JSON
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarTextoYLlamada(
            String texto,
            HerramientaIA herramienta,
            String argumentosJson
    ) {
        LlamadaHerramienta llamada = nuevaLlamada(herramienta, argumentosJson);
        RespuestaModelo resp = new RespuestaModelo(
                Optional.ofNullable(texto),
                List.of(llamada),
                ContinuacionProveedor.VACIA,
                USO_DEFECTO,
                "tool_use"
        );
        cola.add(new ElementoProgramado.Exito(resp));
        return this;
    }

    /**
     * Programa múltiples llamadas a herramientas en una sola respuesta.
     *
     * @param llamadas lista de herramientas solicitadas en orden
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarLlamadas(List<LlamadaHerramienta> llamadas) {
        return programarTextoYLlamadas(null, llamadas);
    }

    /**
     * Programa en la misma respuesta un texto libre y múltiples llamadas a herramientas (incluida
     * {@code responder_al_cliente}).
     *
     * @param texto texto libre devuelto por el proveedor en la misma respuesta, o {@code null}
     * @param llamadas lista de herramientas solicitadas en orden
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarLlamadas(
            String texto,
            List<LlamadaHerramienta> llamadas
    ) {
        return programarTextoYLlamadas(texto, llamadas);
    }

    /**
     * Programa en la misma respuesta un texto libre y múltiples llamadas a herramientas (incluida
     * {@code responder_al_cliente}).
     *
     * @param texto texto libre devuelto por el proveedor en la misma respuesta, o {@code null}
     * @param llamadas lista de herramientas solicitadas en orden
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarTextoYLlamadas(
            String texto,
            List<LlamadaHerramienta> llamadas
    ) {
        Objects.requireNonNull(llamadas, "llamadas");
        RespuestaModelo resp = new RespuestaModelo(
                Optional.ofNullable(texto),
                List.copyOf(llamadas),
                ContinuacionProveedor.VACIA,
                USO_DEFECTO,
                llamadas.isEmpty() ? "end_turn" : "tool_use"
        );
        cola.add(new ElementoProgramado.Exito(resp));
        return this;
    }

    /**
     * Programa una respuesta con la herramienta terminal {@code responder_al_cliente} sin texto libre.
     *
     * @param intencion intención de respuesta elegida
     * @param falta dato faltante si aplica, o {@code null}
     * @param servicio alias de servicio o {@code null}
     * @param profesional alias de profesional ({@code "p1"}, {@code "cualquiera"}) o {@code null}
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarResponderAlCliente(
            IntencionRespuesta intencion,
            DatoFaltante falta,
            String servicio,
            String profesional
    ) {
        return programarResponderAlCliente(null, intencion, falta, servicio, profesional);
    }

    /**
     * Programa en la misma respuesta un texto libre (que el servidor debe descartar) y la herramienta terminal
     * {@code responder_al_cliente} (17-v2-12 §13.2; V2-DA-86).
     *
     * @param texto texto libre devuelto junto con la herramienta terminal, o {@code null}
     * @param intencion intención de respuesta elegida
     * @param falta dato faltante si aplica, o {@code null}
     * @param servicio alias de servicio o {@code null}
     * @param profesional alias de profesional ({@code "p1"}, {@code "cualquiera"}) o {@code null}
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarResponderAlCliente(
            String texto,
            IntencionRespuesta intencion,
            DatoFaltante falta,
            String servicio,
            String profesional
    ) {
        LlamadaHerramienta llamada = llamadaResponderAlCliente(intencion, falta, servicio, profesional);
        return programarTextoYLlamadas(texto, List.of(llamada));
    }

    private static String valorJson(String valor) {
        if (valor == null) {
            return "null";
        }
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Programa un fallo del modelo con un motivo específico.
     *
     * @param motivo motivo del fallo
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarFallo(MotivoFalloModelo motivo) {
        Objects.requireNonNull(motivo, "motivo");
        cola.add(new ElementoProgramado.Error(new FalloModelo(motivo, Optional.empty(), null)));
        return this;
    }

    /**
     * Programa un {@link FalloModelo} específico con uso facturable si aplica.
     *
     * @param fallo excepción de fallo programada
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarFallo(FalloModelo fallo) {
        Objects.requireNonNull(fallo, "fallo");
        cola.add(new ElementoProgramado.Error(fallo));
        return this;
    }

    /**
     * Obtiene una copia inmutable de todas las peticiones recibidas hasta el momento.
     *
     * @return lista de peticiones en orden de llegada
     */
    public synchronized List<PeticionModelo> peticiones() {
        return Collections.unmodifiableList(new ArrayList<>(peticionesRecibidas));
    }

    /**
     * Obtiene la última petición recibida.
     *
     * @return última {@link PeticionModelo}
     * @throws IllegalStateException si aún no se ha recibido ninguna petición
     */
    public synchronized PeticionModelo ultimaPeticion() {
        if (peticionesRecibidas.isEmpty()) {
            throw new IllegalStateException("No se han recibido peticiones todavía.");
        }
        return peticionesRecibidas.get(peticionesRecibidas.size() - 1);
    }

    /**
     * Número de peticiones recibidas.
     *
     * @return cantidad de invocaciones a {@link #responder(PeticionModelo)}
     */
    public synchronized int cantidadPeticiones() {
        return peticionesRecibidas.size();
    }

    /**
     * Verifica que ninguna de las peticiones recibidas contenga teléfonos peruanos,
     * correos electrónicos, identificadores de WhatsApp ({@code wa_id}) o mensajes del personal
     * (V2-RNF-05).
     *
     * @throws AssertionError si se detecta algún dato privado en el contexto o los mensajes
     */
    public synchronized void verificarSinDatosPrivados() {
        for (PeticionModelo peticion : peticionesRecibidas) {
            verificarPeticionSinDatosPrivados(peticion);
        }
    }

    /**
     * Valida que una petición individual no contenga datos prohibidos por V2-RNF-05.
     *
     * @param peticion petición a examinar
     * @throws AssertionError si contiene datos prohibidos
     */
    public static void verificarPeticionSinDatosPrivados(PeticionModelo peticion) {
        String textoCompleto = extraerTextoCompleto(peticion);

        if (PATRON_TELEFONO_PERU.matcher(textoCompleto).find()) {
            throw new AssertionError("La petición al modelo contiene un teléfono: " + textoCompleto);
        }
        if (PATRON_CORREO.matcher(textoCompleto).find()) {
            throw new AssertionError("La petición al modelo contiene un correo electrónico: " + textoCompleto);
        }
        if (PATRON_WA_ID.matcher(textoCompleto).find()) {
            throw new AssertionError("La petición al modelo contiene un wa_id: " + textoCompleto);
        }
        if (PATRON_MENSAJE_PERSONAL.matcher(textoCompleto).find()) {
            throw new AssertionError("La petición al modelo contiene mensajes del personal: " + textoCompleto);
        }
    }

    /**
     * Extrae el texto acumulado del contexto y de todos los mensajes de una petición para inspecciones de aserción.
     *
     * @param peticion petición a inspeccionar
     * @return texto consolidado
     */
    public static String extraerTextoCompleto(PeticionModelo peticion) {
        StringBuilder sb = new StringBuilder();
        sb.append(peticion.contexto()).append("\n");
        for (MensajeModelo m : peticion.mensajes()) {
            if (m instanceof MensajeModelo.DelCliente c) {
                sb.append(c.texto()).append("\n");
            } else if (m instanceof MensajeModelo.DelAsistente a) {
                a.texto().ifPresent(t -> sb.append(t).append("\n"));
                for (LlamadaHerramienta ll : a.llamadas()) {
                    sb.append(ll.argumentosJson()).append("\n");
                }
            } else if (m instanceof MensajeModelo.ResultadosHerramientas r) {
                for (ResultadoHerramienta rh : r.resultados()) {
                    sb.append(rh.json()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Incluye instrucciones y definiciones además de contexto, argumentos y resultados para buscar canarios.
     * @param peticion petición completa enviada al proveedor
     * @return contenido inspeccionable de todas las partes de la petición
     */
    public static String extraerPeticionCompleta(PeticionModelo peticion) {
        StringBuilder texto = new StringBuilder(peticion.instrucciones()).append('\n').append(extraerTextoCompleto(peticion));
        for (var herramienta : peticion.herramientas()) texto.append(herramienta.descripcion()).append(herramienta.esquemaJson());
        return texto.toString();
    }

    /**
     * Reinicia la cola de respuestas programadas, la lista de peticiones recibidas y el gancho previo.
     */
    public synchronized void reiniciar() {
        cola.clear();
        peticionesRecibidas.clear();
        contadorLlamadasHerramienta = 0;
        ganchoAntes = () -> { };
    }
}
