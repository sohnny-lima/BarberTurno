package pe.barberturno.whatsapp;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.common.tenancy.DirectorioNegocios;
import pe.barberturno.common.tenancy.ProveedorNegocioActual;
import pe.barberturno.platform.MembresiaRepository;

/**
 * Registra la ventana de atención de la conversación del remitente: no almacena contenido ni crea
 * fichas. La fecha nunca retrocede ni se adelanta al reloj del servidor, incluso con entradas duplicadas o
 * desordenadas (V2-DA-60). Desde V2-10 devuelve además la entrada anterior, que decide el vaciado por
 * inactividad, y deja de ser el receptor de mensajes: lo es la conversación guiada, que lo llama.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.1
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroEntradaWhatsApp {
    private final EntityManager entidades;
    private final ProveedorNegocioActual negocio;
    private final CanalWhatsAppRepository canales;
    private final DirectorioNegocios directorio;
    private final MembresiaRepository membresias;
    private final Clock reloj;

    RegistroEntradaWhatsApp(EntityManager entidades, ProveedorNegocioActual negocio, CanalWhatsAppRepository canales,
            DirectorioNegocios directorio, MembresiaRepository membresias, Clock reloj) {
        this.entidades = entidades;
        this.negocio = negocio;
        this.canales = canales;
        this.directorio = directorio;
        this.membresias = membresias;
        this.reloj = reloj;
    }

    /**
     * Crea o actualiza exclusivamente la ventana de la conversación del remitente verificado.
     * @param mensaje entrada cuyo negocio ya resolvió el procesador a partir del canal
     * @throws IllegalStateException si el negocio no coincide con el contexto de la transacción
     * @throws org.springframework.transaction.IllegalTransactionStateException si falta la transacción de entrada
     */
    public void recibir(MensajeEntrante mensaje) {
        registrar(mensaje);
    }

    /**
     * Crea o actualiza la ventana de la conversación y devuelve la entrada que había antes. La fila de la
     * conversación queda bloqueada hasta el final de la transacción (por el {@code INSERT} o por el
     * {@code ON CONFLICT DO UPDATE}), así que dos mensajes del mismo cliente se serializan aquí.
     * @param mensaje entrada cuyo negocio ya resolvió el procesador a partir del canal
     * @return conversación y {@code ultima_entrada_en} anterior (vacía si la conversación es nueva)
     * @throws IllegalStateException si el negocio no coincide con el contexto de la transacción
     * @throws org.springframework.transaction.IllegalTransactionStateException si falta la transacción de entrada
     */
    public EntradaRegistrada registrar(MensajeEntrante mensaje) {
        if (negocio.negocioActual().orElseThrow() != mensaje.negocioId()) {
            throw new IllegalStateException("La entrada no corresponde al negocio de la transacción.");
        }
        var ahora = reloj.instant();
        var enviado = mensaje.enviadoEn().isAfter(ahora) ? ahora : mensaje.enviadoEn();
        // PostgreSQL 18: RETURNING old.* da la fila previa en ON CONFLICT DO UPDATE y nulo si se insertó.
        var fila = (Object[]) entidades.createNativeQuery("""
                INSERT INTO conversacion(wa_id, ultima_entrada_en, creado_en, actualizado_en)
                VALUES (:wa, :enviado, :ahora, :ahora)
                ON CONFLICT (negocio_id, wa_id) DO UPDATE SET
                    ultima_entrada_en = GREATEST(conversacion.ultima_entrada_en, EXCLUDED.ultima_entrada_en),
                    actualizado_en = :ahora
                RETURNING new.id, old.ultima_entrada_en
                """).setParameter("wa", mensaje.waId()).setParameter("enviado", enviado)
                .setParameter("ahora", ahora).getSingleResult();
        return new EntradaRegistrada(((Number) fila[0]).longValue(), Optional.ofNullable(instante(fila[1])));
    }

    /**
     * Comprueba, dentro de la transacción de la entrega, que el negocio puede atender: canal
     * {@code ACTIVO}, negocio no suspendido y con algún propietario activo (V2-DA-53, V2-DA-65). Cierra la
     * ventana entre la resolución del evento y su entrega.
     * @param negocioId negocio del mensaje
     * @return verdadero si se puede responder y escribir
     */
    public boolean operativo(long negocioId) {
        var canal = canales.findByNegocioId(negocioId);
        return canal.isPresent() && canal.get().activo() && !directorio.suspendido(negocioId)
                && membresias.contarPropietariosActivos(negocioId) > 0;
    }

    private static Instant instante(Object valor) {
        return switch (valor) {
            case null -> null;
            case Instant i -> i;
            case OffsetDateTime o -> o.toInstant();
            case Timestamp t -> t.toInstant();
            default -> throw new IllegalStateException("Tipo de fecha no esperado: " + valor.getClass().getName());
        };
    }
}
