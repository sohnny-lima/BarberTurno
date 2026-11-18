package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.barberturno.conversation.ConversacionesWhatsApp.EstadoConversacion;

/**
 * Consentimiento de privacidad y ficha de WhatsApp (06-flujos.md §6; 13-v2-10 §7). La ficha solo se crea
 * tras [Aceptar]: con el {@code wa_id} verificado, el nombre del perfil y la fecha de aceptación, sin
 * teléfono (V2-RN-09: un teléfono no se vincula solo a un {@code wa_id}). Exige la transacción de la
 * entrega y la conversación ya bloqueada, que serializa dos aceptaciones del mismo cliente.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ConsentimientoConversacion {
    /** Nombre de la ficha cuando el perfil de WhatsApp no trae uno utilizable. */
    public static final String NOMBRE_POR_DEFECTO = "Cliente de WhatsApp";
    private static final int NOMBRE_MAXIMO = 100;

    private final EntityManager entidades;
    private final ConversacionesWhatsApp conversaciones;
    private final Clock reloj;

    /**
     * Recibe sus dependencias de Spring.
     * @param entidades contexto de persistencia de la transacción de la entrega
     * @param conversaciones acceso a la conversación
     * @param reloj reloj de la aplicación
     */
    public ConsentimientoConversacion(EntityManager entidades, ConversacionesWhatsApp conversaciones, Clock reloj) {
        this.entidades = entidades;
        this.conversaciones = conversaciones;
        this.reloj = reloj;
    }

    /**
     * Registra la aceptación: crea la ficha del {@code wa_id} en el negocio actual o reutiliza la que ya
     * exista con ese {@code wa_id} (completando la fecha de aceptación si faltara), y la enlaza a la
     * conversación. Es idempotente: si la conversación ya tiene ficha, la devuelve sin escribir.
     * @param conversacion conversación bloqueada
     * @param nombrePerfil nombre del perfil de WhatsApp; puede ser nulo o vacío
     * @return ficha enlazada a la conversación
     */
    public long aceptar(EstadoConversacion conversacion, String nombrePerfil) {
        if (conversacion.clienteId().isPresent()) return conversacion.clienteId().getAsLong();
        var ahora = reloj.instant();
        var creada = entidades.createNativeQuery("""
                INSERT INTO cliente (nombre, wa_id, privacidad_aceptada_en, creado_en, actualizado_en)
                VALUES (:nombre, :wa, :ahora, :ahora, :ahora)
                ON CONFLICT (negocio_id, wa_id) WHERE wa_id IS NOT NULL DO NOTHING
                RETURNING id
                """).setParameter("nombre", nombre(nombrePerfil)).setParameter("wa", conversacion.waId())
                .setParameter("ahora", ahora).getResultList();
        long ficha;
        if (creada.isEmpty()) {
            ficha = ((Number) entidades.createNativeQuery("SELECT id FROM cliente WHERE wa_id = :wa")
                    .setParameter("wa", conversacion.waId()).getSingleResult()).longValue();
            entidades.createNativeQuery("""
                    UPDATE cliente SET privacidad_aceptada_en = :ahora, actualizado_en = :ahora
                    WHERE id = :id AND privacidad_aceptada_en IS NULL
                    """).setParameter("ahora", ahora).setParameter("id", ficha).executeUpdate();
        } else {
            ficha = ((Number) creada.getFirst()).longValue();
        }
        conversaciones.enlazarFicha(conversacion.id(), ficha);
        return ficha;
    }

    /**
     * Nombre de la ficha a partir del perfil: sin caracteres de control, con los espacios reducidos y
     * como máximo 100 caracteres (sin partir un par sustituto).
     * @param nombrePerfil nombre del perfil de WhatsApp; puede ser nulo
     * @return nombre utilizable, o {@link #NOMBRE_POR_DEFECTO}
     */
    static String nombre(String nombrePerfil) {
        if (nombrePerfil == null) return NOMBRE_POR_DEFECTO;
        var limpio = nombrePerfil.replaceAll("\\p{Cntrl}|\\p{Cf}", " ").replaceAll("\\s+", " ").strip();
        if (limpio.isEmpty()) return NOMBRE_POR_DEFECTO;
        if (limpio.codePointCount(0, limpio.length()) <= NOMBRE_MAXIMO) return limpio;
        return limpio.substring(0, limpio.offsetByCodePoints(0, NOMBRE_MAXIMO)).strip();
    }
}
