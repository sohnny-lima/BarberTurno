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
 * que no se filtren datos personales ni identificadores internos.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public class ModeloConversacionalFalso implements ModeloConversacional {

    private static final UsoModelo USO_DEFECTO = new UsoModelo("claude-falso", 100, 50, 0, 0, 150);

    private static final Pattern PATRON_TELEFONO_PERU = Pattern.compile("\\b(?:\\+?51)?9\\d{8}\\b");
    private static final Pattern PATRON_CORREO = Pattern.compile("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PATRON_WA_ID = Pattern.compile("\\b519\\d{8}\\b");
    private static final Pattern PATRON_MENSAJE_PERSONAL = Pattern.compile("(?i)\\b(?:PERSONA|MENSAJE_PERSONAL)\\b");

    private final Queue<ElementoProgramado> cola = new ArrayDeque<>();
    private final List<PeticionModelo> peticionesRecibidas = new ArrayList<>();
    private int contadorLlamadasHerramienta = 0;

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
     * Programa una llamada a herramienta con sus argumentos en formato JSON.
     *
     * @param herramienta herramienta invocada
     * @param argumentosJson argumentos en JSON string
     * @return este adaptador falso para encadenar
     */
    public synchronized ModeloConversacionalFalso programarLlamada(HerramientaIA herramienta, String argumentosJson) {
        int idNum = ++contadorLlamadasHerramienta;
        LlamadaHerramienta llamada = new LlamadaHerramienta("call_" + idNum, herramienta, argumentosJson);
        RespuestaModelo resp = new RespuestaModelo(
                Optional.empty(),
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
        Objects.requireNonNull(llamadas, "llamadas");
        RespuestaModelo resp = new RespuestaModelo(
                Optional.empty(),
                llamadas,
                ContinuacionProveedor.VACIA,
                USO_DEFECTO,
                "tool_use"
        );
        cola.add(new ElementoProgramado.Exito(resp));
        return this;
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

    private static String extraerTextoCompleto(PeticionModelo peticion) {
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
     * Reinicia la cola de respuestas programadas y la lista de peticiones recibidas.
     */
    public synchronized void reiniciar() {
        cola.clear();
        peticionesRecibidas.clear();
        contadorLlamadasHerramienta = 0;
    }
}
