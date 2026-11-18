package pe.barberturno.conversation;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/**
 * SQL acotado a los permisos de V115, sin escrituras JPA sobre columnas inmutables ni datos en registros.
 * El servicio controla la transacción y el orden conversación → operación → dominio.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Repository
public class OperacionPendienteRepository {
    private final EntityManager entidades;

    /**
     * Recibe la sesión sometida a la guarda de contexto y a RLS.
     * @param entidades sesión transaccional de la entrega
     */
    public OperacionPendienteRepository(EntityManager entidades) { this.entidades = entidades; }

    boolean bloquearConversacion(long conversacionId, Long fichaId) {
        var filas = entidades.createNativeQuery("select cliente_id from conversacion where id=:id for update", Long.class)
                .setParameter("id", conversacionId).getResultList();
        return !filas.isEmpty() && (fichaId == null || fichaId.equals(filas.getFirst()));
    }

    Optional<OperacionPendiente> bloquear(long id) {
        var filas = entidades.createNativeQuery("""
                select id, conversacion_id, cliente_id, tipo, datos::text, nonce, estado, expira_en, resultado::text
                from operacion_pendiente where id=:id for update
                """, Object[].class).setParameter("id", id).getResultList();
        if (filas.isEmpty()) return Optional.empty();
        var f = (Object[]) filas.getFirst();
        return Optional.of(new OperacionPendiente(((Number) f[0]).longValue(), ((Number) f[1]).longValue(),
                ((Number) f[2]).longValue(), TipoOperacion.valueOf((String) f[3]), (String) f[4], (byte[]) f[5],
                (String) f[6], f[7] instanceof Instant i ? i : ((OffsetDateTime) f[7]).toInstant(), (String) f[8]));
    }

    Optional<OperacionPendiente> vigente(long conversacionId) {
        var ids = entidades.createNativeQuery("""
                select id from operacion_pendiente where conversacion_id=:id and estado='VIGENTE' for update
                """, Long.class).setParameter("id", conversacionId).getResultList();
        return ids.isEmpty() ? Optional.empty() : bloquear(((Number) ids.getFirst()).longValue());
    }

    long insertar(long conversacionId, long fichaId, TipoOperacion tipo, String datos, byte[] nonce,
            Instant ahora, Instant expira) {
        return ((Number) entidades.createNativeQuery("""
                insert into operacion_pendiente(conversacion_id, cliente_id, tipo, datos, nonce, expira_en, creado_en, actualizado_en)
                values (:conversacion, :ficha, :tipo, cast(:datos as jsonb), :nonce, :expira, :ahora, :ahora) returning id
                """, Long.class).setParameter("conversacion", conversacionId).setParameter("ficha", fichaId)
                .setParameter("tipo", tipo.name()).setParameter("datos", datos).setParameter("nonce", nonce)
                .setParameter("expira", expira).setParameter("ahora", ahora).getSingleResult()).longValue();
    }

    void terminar(long id, String estado, String resultado, Instant ahora) {
        entidades.createNativeQuery("""
                update operacion_pendiente set estado=:estado, resultado=cast(:resultado as jsonb), actualizado_en=:ahora
                where id=:id
                """).setParameter("id", id).setParameter("estado", estado).setParameter("resultado", resultado)
                .setParameter("ahora", ahora).executeUpdate();
    }

    void vaciarConversacion(long id, Instant ahora) {
        entidades.createNativeQuery("""
                update conversacion set estado='{}', version=version+1, actualizado_en=:ahora where id=:id
                """).setParameter("id", id).setParameter("ahora", ahora).executeUpdate();
    }
}

/**
 * Fila interna sin toString que pueda divulgar el nonce.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
final class OperacionPendiente {
    final long id;
    final long conversacionId;
    final long fichaId;
    final TipoOperacion tipo;
    final String datos;
    final byte[] nonce;
    final String estado;
    final Instant expira;
    final String resultado;

    OperacionPendiente(long id, long conversacionId, long fichaId, TipoOperacion tipo, String datos,
            byte[] nonce, String estado, Instant expira, String resultado) {
        this.id = id;
        this.conversacionId = conversacionId;
        this.fichaId = fichaId;
        this.tipo = tipo;
        this.datos = datos;
        this.nonce = nonce;
        this.estado = estado;
        this.expira = expira;
        this.resultado = resultado;
    }
}
