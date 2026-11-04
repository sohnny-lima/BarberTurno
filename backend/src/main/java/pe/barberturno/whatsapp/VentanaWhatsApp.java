package pe.barberturno.whatsapp;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Política temporal única para preparar envíos y admitir respuestas del personal (V2-DA-73).
 * El vencimiento nominal se publica; el efectivo reserva el margen del enviador.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
public class VentanaWhatsApp {
    private final SalidaWhatsAppPropiedades opciones;

    VentanaWhatsApp(SalidaWhatsAppPropiedades opciones) { this.opciones = opciones; }

    /**
     * Evalúa el límite efectivo estricto, incluida la ausencia de entrada.
     * @param ultimaEntrada última entrada verificada, o nula
     * @param ahora instante del reloj de la operación
     * @return si queda tiempo para un envío de sesión con el margen configurado
     */
    public boolean abierta(Instant ultimaEntrada, Instant ahora) {
        return ultimaEntrada != null && ahora.isBefore(nominal(ultimaEntrada).minus(opciones.margenVentana()));
    }

    /**
     * Calcula el vencimiento nominal sin restar el margen interno.
     * @param ultimaEntrada última entrada verificada, o nula
     * @return vencimiento nominal, o nulo si nunca escribió
     */
    public Instant nominal(Instant ultimaEntrada) {
        return ultimaEntrada == null ? null : ultimaEntrada.plus(SalidaWhatsAppPropiedades.VENTANA_ATENCION);
    }
}
