package pe.barberturno.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.platform.MembresiaRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Despacha la outbox de uno en uno con ficha: reclamo, preparación y cierre en transacciones separadas
 * del negocio del canal. El POST ocurre sin transacción ni conexión retenida (V2-DA-57 y V2-DA-58).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class EnviadorMensajesWhatsApp implements EnviadorSalidaWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(EnviadorMensajesWhatsApp.class);
    private final ColaSalidaWhatsApp cola;
    private final CanalWhatsAppRepository canales;
    private final DirectorioNegocios directorio;
    private final MembresiaRepository membresias;
    private final EjecutorEnNegocio ejecutor;
    private final ClienteGraphWhatsApp cliente;
    private final AvisoFalloWhatsApp avisos;
    private final SalidaWhatsAppPropiedades opciones;
    private final Clock reloj;
    private final JsonMapper mapper;
    private final TransactionTemplate transaccion;

    EnviadorMensajesWhatsApp(ColaSalidaWhatsApp cola, CanalWhatsAppRepository canales, DirectorioNegocios directorio,
            MembresiaRepository membresias, EjecutorEnNegocio ejecutor, ClienteGraphWhatsApp cliente,
            AvisoFalloWhatsApp avisos, SalidaWhatsAppPropiedades opciones, Clock reloj, JsonMapper mapper,
            PlatformTransactionManager gestor) {
        this.cola = cola;
        this.canales = canales;
        this.directorio = directorio;
        this.membresias = membresias;
        this.ejecutor = ejecutor;
        this.cliente = cliente;
        this.avisos = avisos;
        this.opciones = opciones;
        this.reloj = reloj;
        this.mapper = mapper;
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Recorre los canales en orden y reclama como máximo el lote por negocio, aislando fallos por mensaje.
     * @return número de mensajes reclamados; cero sin token, sin reclamar ninguna fila
     * @throws IllegalStateException si se invoca con una transacción abierta y hay token configurado
     */
    @Override
    public int enviarLote() {
        if (!opciones.envioConfigurado()) return 0;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El envío HTTP exige invocarse fuera de una transacción.");
        }
        int reclamados = 0;
        for (Long negocioId : canales.listarNegociosConCanal()) {
            for (int i = 0; i < opciones.lote(); i++) {
                ColaSalidaWhatsApp.Reclamo reclamo;
                try {
                    var siguiente = enNegocio(negocioId, () -> {
                        Instant ahora = reloj.instant();
                        return cola.reclamar(ahora, ahora.plus(opciones.reclamo()));
                    });
                    if (siguiente.isEmpty()) break;
                    reclamo = siguiente.get();
                } catch (RuntimeException fallo) {
                    LOG.warn("Cola WhatsApp del negocio {}: fallo de reclamo.", negocioId);
                    break;
                }
                reclamados++;
                try {
                    Preparado preparado = enNegocio(negocioId, () -> preparar(negocioId, reclamo));
                    if (preparado == null) continue;
                    ResultadoEnvio resultado = cliente.enviar(preparado.numero(), preparado.cuerpo());
                    enNegocio(negocioId, () -> { cerrar(reclamo, resultado); return null; });
                } catch (RuntimeException fallo) {
                    // Sin excepción ni traza: las bibliotecas pueden incluir cuerpo, URL o destinatario.
                    LOG.warn("Mensaje WhatsApp {}: fallo de procesamiento, ficha {}.", reclamo.id(), reclamo.ficha());
                }
            }
        }
        return reclamados;
    }

    private <T> T enNegocio(long negocioId, Supplier<T> accion) {
        return ejecutor.ejecutar(negocioId, () -> transaccion.execute(tx -> accion.get()));
    }

    private Preparado preparar(long negocioId, ColaSalidaWhatsApp.Reclamo reclamo) {
        var actual = cola.bloquear(reclamo);
        if (actual.isEmpty()) return null;
        var mensaje = actual.get();
        Instant ahora = reloj.instant();
        var canal = canales.findByNegocioId(negocioId).orElseThrow();
        MotivoFallo motivo = null;
        if (reclamo.ficha() > opciones.maxIntentos()) motivo = MotivoFallo.REINTENTOS_AGOTADOS;
        else if (!canal.activo()) motivo = MotivoFallo.CANAL_DESCONECTADO;
        else if (directorio.suspendido(negocioId) || membresias.contarPropietariosActivos(negocioId) == 0) {
            motivo = MotivoFallo.NEGOCIO_NO_OPERATIVO;
        } else if (!ahora.isBefore(mensaje.creadoEn().plus(opciones.antiguedadMaxima()))) {
            motivo = MotivoFallo.MENSAJE_CADUCADO;
        }
        if (motivo != null) { fallar(mensaje, motivo, null, mensaje.inciertos(), ahora); return null; }
        var conversacion = cola.conversacion(mensaje.conversacionId(), negocioId).orElseThrow();
        boolean ventana = conversacion.ultimaEntradaEn() != null && ahora.isBefore(conversacion.ultimaEntradaEn()
                .plus(SalidaWhatsAppPropiedades.VENTANA_ATENCION).minus(opciones.margenVentana()));
        if (mensaje.tipo() != TipoMensaje.PLANTILLA && !ventana) {
            fallar(mensaje, MotivoFallo.VENTANA_CERRADA, null, mensaje.inciertos(), ahora);
            return null;
        }
        var contenido = mapper.readTree(mensaje.contenido());
        TipoMensaje como = mensaje.tipo();
        ObjectNode cuerpo = mapper.createObjectNode().put("messaging_product", "whatsapp")
                .put("recipient_type", "individual").put("to", conversacion.waId())
                .put("biz_opaque_callback_data", "bt:m:" + mensaje.id() + ":" + reclamo.ficha());
        if (mensaje.tipo() == TipoMensaje.PLANTILLA) {
            var plantilla = PlantillaUtility.porNombre(contenido.path("plantilla").asString());
            Map<String, String> parametros = new LinkedHashMap<>();
            plantilla.parametros().forEach(nombre -> parametros.put(nombre, contenido.path("parametros").path(nombre).asString()));
            if (ventana && contenido.path("textoEnVentana").asBoolean(false)) {
                como = TipoMensaje.TEXTO;
                texto(cuerpo, plantilla.textoEnVentana(parametros));
            } else {
                cuerpo.put("type", "template");
                ObjectNode template = cuerpo.putObject("template").put("name", plantilla.nombre());
                template.putObject("language").put("code", plantilla.idioma());
                var valores = template.putArray("components").addObject().put("type", "body").putArray("parameters");
                plantilla.parametros().forEach(nombre -> valores.addObject().put("type", "text")
                        .put("parameter_name", nombre).put("text", parametros.get(nombre)));
            }
        } else if (mensaje.tipo() == TipoMensaje.TEXTO) {
            texto(cuerpo, contenido.path("texto").asString());
        } else {
            cuerpo.put("type", "interactive").set("interactive", contenido.path("interactivo"));
        }
        return cola.preparar(mensaje, como, ahora) ? new Preparado(canal.phoneNumberId(), cuerpo) : null;
    }

    private static void texto(ObjectNode cuerpo, String texto) {
        cuerpo.put("type", "text").putObject("text").put("preview_url", false).put("body", texto);
    }

    private void cerrar(ColaSalidaWhatsApp.Reclamo reclamo, ResultadoEnvio resultado) {
        var actual = cola.bloquear(reclamo);
        if (actual.isEmpty()) return;
        var mensaje = actual.get();
        Instant ahora = reloj.instant();
        switch (resultado) {
            case ResultadoEnvio.Aceptado aceptado -> cola.cambiar(mensaje, EstadoMensaje.ENVIADO,
                    aceptado.wamid(), null, null, mensaje.inciertos(), null, ahora);
            case ResultadoEnvio.Rechazado rechazado -> {
                Integer codigo = rechazado.codigoMeta().isPresent() ? rechazado.codigoMeta().getAsInt() : null;
                if (rechazado.transitorio()) reintentar(mensaje, codigo, ahora);
                else fallar(mensaje, rechazado.motivo(), codigo, mensaje.inciertos(), ahora);
            }
            case ResultadoEnvio.NoEnviado ignorado -> reintentar(mensaje, null, ahora);
            case ResultadoEnvio.Desconocido ignorado -> {
                int inciertos = mensaje.inciertos() + 1;
                if (inciertos >= opciones.maxInciertos()) {
                    fallar(mensaje, MotivoFallo.RESULTADO_DESCONOCIDO, null, inciertos, ahora);
                } else {
                    cola.cambiar(mensaje, EstadoMensaje.PENDIENTE, null, null, null, inciertos,
                            ahora.plus(opciones.esperaConciliacion()), ahora);
                }
            }
        }
    }

    private void reintentar(MensajeWhatsApp mensaje, Integer codigo, Instant ahora) {
        if (mensaje.intentos() >= opciones.maxIntentos()) {
            fallar(mensaje, MotivoFallo.REINTENTOS_AGOTADOS, codigo, mensaje.inciertos(), ahora);
        } else {
            cola.cambiar(mensaje, EstadoMensaje.PENDIENTE, null, null, codigo, mensaje.inciertos(),
                    ahora.plus(PoliticaReintentos.espera(mensaje.intentos(), opciones.esperaBase(), opciones.esperaMaxima())), ahora);
        }
    }

    private void fallar(MensajeWhatsApp mensaje, MotivoFallo motivo, Integer codigo, int inciertos, Instant ahora) {
        if (cola.cambiar(mensaje, EstadoMensaje.FALLIDO, mensaje.wamid(), motivo, codigo, inciertos, null, ahora)) {
            avisos.avisar(mensaje, motivo);
        }
    }

    private record Preparado(String numero, ObjectNode cuerpo) { }
}
