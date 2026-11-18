package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.OptionalLong;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Acceso a la fila de {@code conversacion} para la conversación guiada (13-v2-10 §3.4 y §8): bloqueo,
 * estado y enlace con la ficha. Exige la transacción de la entrega; RLS limita todo al negocio actual.
 * {@code estado} solo guarda el paso y, mientras se espera el consentimiento, la franja pendiente
 * (V2-DA-63); nunca datos de una reserva ya confirmada.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ConversacionesWhatsApp {
    /** Clave de {@code estado} con el identificador codificado de la franja que espera el consentimiento. */
    public static final String PENDIENTE = "pendiente";
    /** Clave informativa de {@code estado} con el último paso mostrado. */
    public static final String PASO = "paso";

    private final EntityManager entidades;
    private final JsonMapper mapper;
    private final Clock reloj;

    /**
     * Recibe sus dependencias de Spring.
     * @param entidades contexto de persistencia de la transacción de la entrega
     * @param mapper conversor JSON de la aplicación
     * @param reloj reloj de la aplicación
     */
    public ConversacionesWhatsApp(EntityManager entidades, JsonMapper mapper, Clock reloj) {
        this.entidades = entidades;
        this.mapper = mapper;
        this.reloj = reloj;
    }

    /**
     * Bloquea la conversación ({@code FOR UPDATE}, primer bloqueo del orden de V2-DA-65) y la lee.
     * @param conversacionId conversación del negocio actual
     * @return conversación bloqueada
     * @throws jakarta.persistence.NoResultException si no existe en el negocio actual
     */
    public EstadoConversacion bloquear(long conversacionId) {
        var fila = (Object[]) entidades.createNativeQuery("""
                SELECT id, wa_id, cliente_id, CAST(estado AS text), modo FROM conversacion WHERE id = :id FOR UPDATE
                """).setParameter("id", conversacionId).getSingleResult();
        var estado = mapper.readTree((String) fila[3]);
        return new EstadoConversacion(((Number) fila[0]).longValue(), (String) fila[1],
                fila[2] == null ? OptionalLong.empty() : OptionalLong.of(((Number) fila[2]).longValue()),
                estado instanceof ObjectNode objeto ? objeto : mapper.createObjectNode(),
                ModoConversacion.valueOf((String) fila[4]));
    }

    /**
     * Sustituye el estado e incrementa {@code version}.
     * @param conversacionId conversación bloqueada
     * @param estado estado nuevo (objeto JSON)
     */
    public void guardar(long conversacionId, ObjectNode estado) {
        entidades.createNativeQuery("""
                UPDATE conversacion SET estado = CAST(:estado AS jsonb), version = version + 1, actualizado_en = :ahora
                WHERE id = :id
                """).setParameter("estado", mapper.writeValueAsString(estado)).setParameter("ahora", reloj.instant())
                .setParameter("id", conversacionId).executeUpdate();
    }

    /**
     * Vacía el estado ({@code {}}): al ejecutarse una operación, al volver al menú y tras la inactividad
     * (04-arquitectura §7.1 punto 4).
     * @param conversacionId conversación bloqueada
     */
    public void vaciar(long conversacionId) {
        guardar(conversacionId, mapper.createObjectNode());
    }

    /**
     * Recupera el resumen interactivo ya presentado sin duplicar datos ni nonce en el estado de navegación.
     * Se usa únicamente al repetir el paso de confirmación: no propone ni ejecuta operaciones.
     * @param conversacionId conversación bloqueada del negocio actual, limitada por RLS
     * @return último interactivo de salida, si su contenido sigue disponible
     */
    public Optional<ObjectNode> ultimoInteractivo(long conversacionId) {
        var filas = entidades.createNativeQuery("""
                SELECT contenido::text FROM mensaje_whatsapp WHERE conversacion_id=:id
                    AND direccion='SALIENTE' AND tipo='INTERACTIVO' ORDER BY id DESC LIMIT 1
                """, String.class).setParameter("id", conversacionId).getResultList();
        if (filas.isEmpty()) return Optional.empty();
        var interactivo = mapper.readTree((String) filas.getFirst()).path("interactivo");
        return interactivo instanceof ObjectNode objeto ? Optional.of(objeto.deepCopy()) : Optional.empty();
    }

    /**
     * Enlaza la ficha si la conversación aún no tiene una. El disparador de V115 impide cambiar una ficha
     * ya fijada o enlazar una ficha de otro {@code wa_id} o negocio.
     * @param conversacionId conversación bloqueada
     * @param fichaId ficha del mismo negocio y {@code wa_id}
     * @return verdadero si la enlazó; falso si ya tenía ficha
     */
    public boolean enlazarFicha(long conversacionId, long fichaId) {
        return entidades.createNativeQuery("""
                UPDATE conversacion SET cliente_id = :ficha, version = version + 1, actualizado_en = :ahora
                WHERE id = :id AND cliente_id IS NULL
                """).setParameter("ficha", fichaId).setParameter("ahora", reloj.instant())
                .setParameter("id", conversacionId).executeUpdate() == 1;
    }

    /**
     * Revisión actual de la conversación (17-v2-12 §3.1): la incrementa un disparador en cada actualización de la
     * fila. Se lee después de las escrituras de la entrega para encolar un turno de IA.
     * @param conversacionId conversación bloqueada del negocio actual
     * @return revisión
     */
    public long revision(long conversacionId) {
        return ((Number) entidades.createNativeQuery("SELECT revision FROM conversacion WHERE id = :id")
                .setParameter("id", conversacionId).getSingleResult()).longValue();
    }

    /**
     * Conversación bloqueada tal como la ve la conversación guiada.
     * @param id identidad de la conversación
     * @param waId remitente verificado por WhatsApp
     * @param clienteId ficha enlazada, vacía hasta aceptar la privacidad
     * @param estado estado guardado (copia modificable)
     * @param modo quién atiende la conversación (V2-11)
     */
    public record EstadoConversacion(long id, String waId, OptionalLong clienteId, ObjectNode estado,
            ModoConversacion modo) {
        /**
         * Conversación atendida por el asistente (la forma de V2-10).
         * @param id identidad de la conversación
         * @param waId remitente verificado por WhatsApp
         * @param clienteId ficha enlazada, vacía hasta aceptar la privacidad
         * @param estado estado guardado (copia modificable)
         */
        public EstadoConversacion(long id, String waId, OptionalLong clienteId, ObjectNode estado) {
            this(id, waId, clienteId, estado, ModoConversacion.ASISTENTE);
        }
    }
}
