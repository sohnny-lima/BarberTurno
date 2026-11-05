package pe.barberturno.conversation.ia;

import java.time.Instant;
import java.util.List;
import pe.barberturno.conversation.MotivoDerivacion;

/**
 * Decisión validada por el servidor durante el bucle, que solo se aplica en la transacción final del turno
 * (17-v2-12 §3.3.5 y §4.3; consulta 127-6/7/8). Un turno aplica como mucho un efecto, con la precedencia
 * derivación &gt; consentimiento &gt; propuesta &gt; franjas.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
public sealed interface EfectoTurno {
    /**
     * Proponer una reserva (la crea {@code OperacionesConversacion.proponerCreacion}).
     * @param servicioId servicio validado
     * @param barberoId profesional validado
     * @param inicio inicio validado
     */
    record ProponerReserva(long servicioId, long barberoId, Instant inicio) implements EfectoTurno { }

    /**
     * Proponer mover una cita de la ficha.
     * @param reservaId cita validada
     * @param inicio nuevo inicio validado
     */
    record ProponerReprogramacion(long reservaId, Instant inicio) implements EfectoTurno { }

    /**
     * Proponer cancelar una cita de la ficha.
     * @param reservaId cita validada
     */
    record ProponerCancelacion(long reservaId) implements EfectoTurno { }

    /**
     * Derivar a una persona (con ficha).
     * @param motivo motivo de la derivación
     */
    record Derivar(MotivoDerivacion motivo) implements EfectoTurno { }

    /**
     * Sin ficha: pedir el consentimiento con la franja elegida como pendiente (V2-10 §7).
     * @param servicioId servicio
     * @param barberoId profesional
     * @param inicio inicio
     */
    record SolicitarConsentimientoReserva(long servicioId, long barberoId, Instant inicio) implements EfectoTurno { }

    /**
     * Sin ficha: pedir el consentimiento con la derivación como pendiente (V2-11).
     * @param motivo motivo de la derivación
     */
    record SolicitarConsentimientoDerivacion(MotivoDerivacion motivo) implements EfectoTurno { }

    /**
     * Mostrar las franjas devueltas en el turno como lista del servidor.
     * @param franjas franjas, en orden
     */
    record MostrarFranjas(List<FranjaOfrecida> franjas) implements EfectoTurno {
        /**
         * Copia la lista.
         * @param franjas franjas, en orden
         */
        public MostrarFranjas {
            franjas = List.copyOf(franjas);
        }
    }
}
