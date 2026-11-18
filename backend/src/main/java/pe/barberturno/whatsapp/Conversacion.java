package pe.barberturno.whatsapp;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Conversación aislada por RLS y de solo lectura para JPA; la ventana se actualiza con SQL acotado.
 * La versión queda reservada a V2-10 y no participa en bloqueo optimista.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Immutable
@Table(name = "conversacion")
public class Conversacion {
    @Id private Long id;
    @Column(name = "negocio_id", nullable = false) private Long negocioId;
    @Column(name = "wa_id", nullable = false, length = 20) private String waId;
    @Column(name = "cliente_id") private Long clienteId;
    @Column(nullable = false, length = 10) private String modo;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String estado;
    @Column(name = "ultima_entrada_en") private Instant ultimaEntradaEn;
    @Column(nullable = false) private int version;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false) private Instant actualizadoEn;

    /** Hidrata exclusivamente desde JPA, sin construir conversaciones nuevas. */
    protected Conversacion() { }

    Long id() { return id; }
    Long negocioId() { return negocioId; }
    String waId() { return waId; }
    Long clienteId() { return clienteId; }
    String modo() { return modo; }
    String estado() { return estado; }
    Instant ultimaEntradaEn() { return ultimaEntradaEn; }
    int version() { return version; }
    Instant creadoEn() { return creadoEn; }
    Instant actualizadoEn() { return actualizadoEn; }
}
