package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;

/**
 * Registra solo la ventana de atención: no almacena contenido ni crea fichas. La fecha nunca retrocede
 * ni se adelanta al reloj del servidor, incluso con entradas duplicadas o desordenadas (V2-DA-60).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroEntradaWhatsApp implements ReceptorMensajesWhatsApp {
    private final EntityManager entidades;
    private final ProveedorNegocioActual negocio;
    private final Clock reloj;

    RegistroEntradaWhatsApp(EntityManager entidades, ProveedorNegocioActual negocio, Clock reloj) {
        this.entidades = entidades;
        this.negocio = negocio;
        this.reloj = reloj;
    }

    /**
     * Crea o actualiza exclusivamente la ventana de la conversación del remitente verificado.
     * @param mensaje entrada cuyo negocio ya resolvió el procesador a partir del canal
     * @throws IllegalStateException si el negocio no coincide con el contexto de la transacción
     * @throws org.springframework.transaction.IllegalTransactionStateException si falta la transacción de entrada
     */
    @Override
    public void recibir(MensajeEntrante mensaje) {
        if (negocio.negocioActual().orElseThrow() != mensaje.negocioId()) {
            throw new IllegalStateException("La entrada no corresponde al negocio de la transacción.");
        }
        var ahora = reloj.instant();
        var enviado = mensaje.enviadoEn().isAfter(ahora) ? ahora : mensaje.enviadoEn();
        entidades.createNativeQuery("""
                INSERT INTO conversacion(wa_id, ultima_entrada_en, creado_en, actualizado_en)
                VALUES (:wa, :enviado, :ahora, :ahora)
                ON CONFLICT (negocio_id, wa_id) DO UPDATE SET
                    ultima_entrada_en = GREATEST(conversacion.ultima_entrada_en, EXCLUDED.ultima_entrada_en),
                    actualizado_en = :ahora
                """).setParameter("wa", mensaje.waId()).setParameter("enviado", enviado)
                .setParameter("ahora", ahora).executeUpdate();
    }
}
