package pe.barberturno.whatsapp;

import java.time.Clock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;

/**
 * Aplica estados monotónicos dentro de la transacción de entrada. Concilia resultados desconocidos por
 * el dato opaco del intento, comprobando negocio y destinatario, sin sobrescribir un wamid previo (§4.4).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ActualizadorEstadosWhatsApp implements ReceptorEstadosWhatsApp {
    private static final Logger LOG = LoggerFactory.getLogger(ActualizadorEstadosWhatsApp.class);
    private static final Pattern CONCILIACION = Pattern.compile("bt:m:([1-9][0-9]*):([1-9][0-9]*)");
    private final ColaSalidaWhatsApp cola;
    private final AvisoFalloWhatsApp avisos;
    private final ProveedorNegocioActual negocio;
    private final Clock reloj;

    ActualizadorEstadosWhatsApp(ColaSalidaWhatsApp cola, AvisoFalloWhatsApp avisos,
            ProveedorNegocioActual negocio, Clock reloj) {
        this.cola = cola;
        this.avisos = avisos;
        this.negocio = negocio;
        this.reloj = reloj;
    }

    /**
     * Concilia el mensaje local y aplica solo avances o fallos admitidos por V2-DA-59.
     * @param entrada aviso atribuido al negocio del canal por el procesador de entrada
     * @throws IllegalStateException si el contexto no coincide o aún no se localiza el mensaje; V2-08 reintenta
     * @throws org.springframework.transaction.IllegalTransactionStateException si falta la transacción de entrada
     */
    @Override
    public void recibir(EstadoEntrante entrada) {
        if (negocio.negocioActual().orElseThrow() != entrada.negocioId()) {
            throw new IllegalStateException("El estado no corresponde al negocio de la transacción.");
        }
        EstadoMensaje destino = switch (entrada.estado()) {
            case "sent" -> EstadoMensaje.ENVIADO;
            case "delivered" -> EstadoMensaje.ENTREGADO;
            case "read" -> EstadoMensaje.LEIDO;
            case "failed" -> EstadoMensaje.FALLIDO;
            default -> null;
        };
        if (destino == null) return;
        var actual = cola.bloquearPorWamid(entrada.wamid());
        if (actual.isEmpty()) {
            var dato = CONCILIACION.matcher(entrada.dato().path("biz_opaque_callback_data").asString(""));
            if (dato.matches()) {
                try {
                    long id = Long.parseLong(dato.group(1));
                    long ficha = Long.parseLong(dato.group(2));
                    actual = cola.bloquearPorConciliacion(id, entrada.waId());
                    if (actual.isPresent() && actual.get().wamid() != null
                            && !actual.get().wamid().equals(entrada.wamid())) {
                        LOG.warn("Mensaje WhatsApp {}: segundo identificador, ficha {}.", id, ficha);
                        return;
                    }
                } catch (NumberFormatException invalido) {
                    // Un dato opaco fuera de rango no localiza un mensaje.
                }
            }
        }
        if (actual.isEmpty()) throw new IllegalStateException("El mensaje del estado aún no está localizado.");
        var mensaje = actual.get();
        if (mensaje.estado() == EstadoMensaje.FALLIDO && mensaje.errorCodigo() != MotivoFallo.RESULTADO_DESCONOCIDO) return;
        if (destino == EstadoMensaje.FALLIDO) {
            if (mensaje.estado() != EstadoMensaje.PENDIENTE && mensaje.estado() != EstadoMensaje.ENVIADO
                    && mensaje.estado() != EstadoMensaje.FALLIDO) return;
            var error = entrada.dato().path("errors").path(0).path("code");
            Integer codigo = error.isIntegralNumber() && error.canConvertToInt() ? error.asInt() : null;
            if (cola.cambiar(mensaje, destino, entrada.wamid(), MotivoFallo.FALLO_ENTREGA, codigo,
                    mensaje.inciertos(), null, reloj.instant())) avisos.avisar(mensaje, MotivoFallo.FALLO_ENTREGA);
        } else if (orden(destino) > orden(mensaje.estado())) {
            cola.cambiar(mensaje, destino, entrada.wamid(), null, null, mensaje.inciertos(), null, reloj.instant());
        }
    }

    private static int orden(EstadoMensaje estado) {
        return switch (estado) {
            case ENVIADO -> 1;
            case ENTREGADO -> 2;
            case LEIDO -> 3;
            default -> 0;
        };
    }
}
