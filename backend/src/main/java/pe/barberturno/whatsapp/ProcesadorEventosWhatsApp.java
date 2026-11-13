package pe.barberturno.whatsapp;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.EjecutorEnNegocio;
import pe.barberturno.customers.ClienteRepository;
import pe.barberturno.platform.MembresiaRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reclama eventos durables y los entrega por el negocio del número receptor. La ficha impide que
 * un trabajador vencido termine un reclamo posterior; el puerto comparte la transacción del evento.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
public class ProcesadorEventosWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(ProcesadorEventosWhatsApp.class);
    private final ColaEventosWhatsApp cola;
    private final CanalWhatsAppRepository canales;
    private final DirectorioNegocios directorio;
    private final MembresiaRepository membresias;
    private final EjecutorEnNegocio ejecutor;
    private final ClienteRepository clientes;
    private final ReceptorMensajesWhatsApp mensajes;
    private final ReceptorEstadosWhatsApp estados;
    private final JsonMapper mapper;
    private final Clock reloj;
    private final WhatsAppPropiedades.Procesador opciones;
    private final TransactionTemplate transaccion;

    ProcesadorEventosWhatsApp(ColaEventosWhatsApp cola, CanalWhatsAppRepository canales,
            DirectorioNegocios directorio, MembresiaRepository membresias, EjecutorEnNegocio ejecutor,
            ClienteRepository clientes, ReceptorMensajesWhatsApp mensajes, ReceptorEstadosWhatsApp estados,
            JsonMapper mapper, Clock reloj, WhatsAppPropiedades propiedades, PlatformTransactionManager gestor) {
        this.cola = cola;
        this.canales = canales;
        this.directorio = directorio;
        this.membresias = membresias;
        this.ejecutor = ejecutor;
        this.clientes = clientes;
        this.mensajes = mensajes;
        this.estados = estados;
        this.mapper = mapper;
        this.reloj = reloj;
        this.opciones = propiedades.procesador();
        this.transaccion = new TransactionTemplate(gestor);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reclama como máximo el lote configurado, resuelve los descartes sin abrir contexto de negocio
     * y entrega cada evento en su propia transacción. Los fallos se reprograman después del rollback.
     * @return eventos terminados por esta invocación: procesados, descartados o con intentos agotados
     */
    public int procesarLote() {
        Instant ahora = reloj.instant();
        var eventos = transaccion.execute(tx -> cola.reclamar(ahora, ahora.plus(opciones.reclamo()), opciones.lote()));
        int terminados = 0;
        for (var evento : eventos) {
            try {
                var resolucion = transaccion.execute(tx -> resolver(evento));
                boolean terminado = resolucion.negocioId() == null ? resolucion.terminado()
                        : ejecutor.ejecutar(resolucion.negocioId(), () -> transaccion.execute(tx ->
                                entregar(evento, resolucion.negocioId())));
                if (terminado) terminados++;
            } catch (RuntimeException fallo) {
                Instant falloEn = reloj.instant();
                boolean agotado = evento.intentos() >= opciones.maxIntentos();
                Instant siguiente = falloEn.plus(PoliticaReintentos.espera(evento.intentos(),
                        opciones.esperaBase(), opciones.esperaMaxima()));
                boolean cambiado = transaccion.execute(tx -> cola.fallar(evento.id(), evento.intentos(),
                        siguiente, falloEn, agotado));
                if (cambiado && agotado) {
                    LOG.warn("Evento WhatsApp {}: FALLO_PROCESAMIENTO.", evento.id());
                    terminados++;
                }
            }
        }
        return terminados;
    }

    private Resolucion resolver(EventoEntrante evento) {
        var canal = canales.findByPhoneNumberId(evento.phoneNumberId());
        if (canal.isEmpty()) return descartar(evento, null, MotivoEvento.NUMERO_NO_REGISTRADO);
        long negocioId = canal.get().negocioId();
        if (evento.tipo() == TipoEvento.MENSAJE && !canal.get().activo()) {
            return descartar(evento, negocioId, MotivoEvento.CANAL_DESCONECTADO);
        }
        if (evento.tipo() == TipoEvento.MENSAJE
                && (directorio.suspendido(negocioId) || membresias.contarPropietariosActivos(negocioId) == 0)) {
            return descartar(evento, negocioId, MotivoEvento.NEGOCIO_NO_OPERATIVO);
        }
        if (!waId(evento, mapper.readTree(evento.cuerpo()).path("dato")).matches("^[0-9]{1,20}$")) {
            return descartar(evento, negocioId, MotivoEvento.REMITENTE_INVALIDO);
        }
        return new Resolucion(negocioId, false);
    }

    private Resolucion descartar(EventoEntrante evento, Long negocioId, MotivoEvento motivo) {
        boolean terminado = cola.descartar(evento.id(), evento.intentos(), negocioId, motivo, reloj.instant());
        return new Resolucion(null, terminado);
    }

    private boolean entregar(EventoEntrante reclamo, long negocioId) {
        var actual = cola.bloquear(reclamo.id(), reclamo.intentos());
        if (actual.isEmpty()) return false;
        var evento = actual.get();
        var cuerpo = mapper.readTree(evento.cuerpo());
        var dato = cuerpo.path("dato");
        String waId = waId(evento, dato);
        Instant fecha = fecha(dato.path("timestamp"), evento.recibidoEn());
        if (evento.tipo() == TipoEvento.MENSAJE) {
            var ficha = clientes.buscarIdPorWaId(negocioId, waId);
            var clienteId = ficha.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ficha.get());
            mensajes.recibir(new MensajeEntrante(evento.id(), negocioId, waId, clienteId,
                    cuerpo.path("contacto").path("nombre").asString(null), dato.path("id").asString(),
                    dato.path("type").asString(), fecha, dato));
        } else {
            estados.recibir(new EstadoEntrante(evento.id(), negocioId, dato.path("id").asString(),
                    dato.path("status").asString(), waId, fecha, dato));
        }
        return cola.procesar(evento.id(), evento.intentos(), negocioId, reloj.instant());
    }

    private static String waId(EventoEntrante evento, JsonNode dato) {
        return dato.path(evento.tipo() == TipoEvento.MENSAJE ? "from" : "recipient_id").asString("");
    }

    private static Instant fecha(JsonNode valor, Instant recibidoEn) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(valor.asString("")));
        } catch (NumberFormatException | DateTimeException invalido) {
            return recibidoEn;
        }
    }

    private record Resolucion(Long negocioId, boolean terminado) { }
}
