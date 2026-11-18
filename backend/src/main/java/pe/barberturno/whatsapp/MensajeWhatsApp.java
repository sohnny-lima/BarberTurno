package pe.barberturno.whatsapp;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Lectura de la outbox bajo RLS. Cada escritura usa SQL condicionado a estado y ficha, sin sincronización JPA.
 * {@code intentos} cuenta intentos reservados y confirmados al reclamar, aunque no lleguen al HTTP;
 * es también la ficha que invalida cierres antiguos. {@code inciertos} cuenta presunciones confirmadas
 * al preparar: 0 permite reintentos seguros, 1 conserva una posible aceptación y 2 consume además
 * el único intento adicional. El cierre solo retira la primera presunción ante no aceptación probada;
 * la aceptación conserva el contador y acredita {@code ENVIADO}, no la entrega (V2-DA-70).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Immutable
@Table(name = "mensaje_whatsapp")
public class MensajeWhatsApp {
    @Id private Long id;
    @Column(name = "negocio_id", nullable = false) private Long negocioId;
    @Column(name = "conversacion_id", nullable = false) private Long conversacionId;
    @Column(name = "reserva_id") private Long reservaId;
    @Column(nullable = false, length = 8) private String direccion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private OrigenMensaje origen;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private TipoMensaje tipo;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String contenido;
    @Column(length = 128) private String wamid;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private EstadoMensaje estado;
    @Column(nullable = false) private short intentos;
    @Column(nullable = false) private short inciertos;
    @Column(name = "proximo_intento") private Instant proximoIntento;
    @Enumerated(EnumType.STRING) @Column(name = "error_codigo", length = 24) private MotivoFallo errorCodigo;
    @Column(name = "codigo_meta") private Integer codigoMeta;
    @Enumerated(EnumType.STRING) @Column(name = "enviado_como", length = 12) private TipoMensaje enviadoComo;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false) private Instant actualizadoEn;

    /** Hidrata exclusivamente desde JPA; las inserciones corresponden a la cola. */
    protected MensajeWhatsApp() { }

    Long id() { return id; }
    Long negocioId() { return negocioId; }
    Long conversacionId() { return conversacionId; }
    Long reservaId() { return reservaId; }
    String direccion() { return direccion; }
    OrigenMensaje origen() { return origen; }
    TipoMensaje tipo() { return tipo; }
    String contenido() { return contenido; }
    String wamid() { return wamid; }
    EstadoMensaje estado() { return estado; }
    short intentos() { return intentos; }
    short inciertos() { return inciertos; }
    Instant proximoIntento() { return proximoIntento; }
    MotivoFallo errorCodigo() { return errorCodigo; }
    Integer codigoMeta() { return codigoMeta; }
    TipoMensaje enviadoComo() { return enviadoComo; }
    Instant creadoEn() { return creadoEn; }
    Instant actualizadoEn() { return actualizadoEn; }
}
