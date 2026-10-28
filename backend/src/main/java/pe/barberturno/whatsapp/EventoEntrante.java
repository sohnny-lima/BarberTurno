package pe.barberturno.whatsapp;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento de la cola de entrada de WhatsApp (tabla de plataforma, sin RLS). Es de solo lectura para JPA:
 * el webhook lo inserta con {@code ON CONFLICT DO NOTHING} y el procesador cambia su estado con sentencias
 * condicionadas a la ficha del reclamo (V2-DA-52), nunca sincronizando la entidad.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Immutable
@Table(name = "evento_whatsapp_entrante")
public class EventoEntrante {
    @Id
    private Long id;
    @Column(name = "clave_idempotencia", nullable = false, length = 160)
    private String claveIdempotencia;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private TipoEvento tipo;
    @Column(name = "phone_number_id", nullable = false, length = 32)
    private String phoneNumberId;
    @Column(name = "negocio_id")
    private Long negocioId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private String cuerpo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private EstadoEvento estado;
    @Enumerated(EnumType.STRING) @Column(length = 24)
    private MotivoEvento motivo;
    @Column(nullable = false)
    private short intentos;
    @Column(name = "proximo_intento", nullable = false)
    private Instant proximoIntento;
    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;
    @Column(name = "procesado_en")
    private Instant procesadoEn;

    /** Hidrata exclusivamente desde JPA. */
    protected EventoEntrante() { }

    Long id() { return id; }
    String claveIdempotencia() { return claveIdempotencia; }
    TipoEvento tipo() { return tipo; }
    String phoneNumberId() { return phoneNumberId; }
    Long negocioId() { return negocioId; }
    /** Cuerpo con el formato de 11-v2-08-whatsapp-entrada.md §4.2: {@code phoneNumberId}, {@code contacto}, {@code dato}. */
    String cuerpo() { return cuerpo; }
    EstadoEvento estado() { return estado; }
    MotivoEvento motivo() { return motivo; }
    short intentos() { return intentos; }
    Instant proximoIntento() { return proximoIntento; }
    Instant recibidoEn() { return recibidoEn; }
    Instant procesadoEn() { return procesadoEn; }
}
