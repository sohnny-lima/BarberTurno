package pe.barberturno.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.reservations.EstadoReserva;
import pe.barberturno.users.Usuario;

/**
 * Historial de un cambio de reserva con actor, estados y valores JSON anteriores y nuevos (RN-15).
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "auditoria_reserva")
public class AuditoriaReserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private Usuario actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 12)
    private AccionAuditoria accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = true, length = 12)
    private EstadoReserva estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 12)
    private EstadoReserva estadoNuevo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_anteriores", columnDefinition = "jsonb")
    private Map<String, Object> datosAnteriores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_nuevos", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> datosNuevos;

    @Column(name = "motivo", nullable = true, length = 300)
    private String motivo;

    @Column(name = "excepcional", nullable = false)
    private boolean excepcional;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected AuditoriaReserva() {
    }

    /**
     * Prepara el historial RN-15 con copias superficiales de los mapas JSON; el servicio lo persiste con reserva
     * y avisos en la misma transacción.
     * @param reserva reserva histórica asociada por reserva_id; no nula
     * @param actor usuario que ejecutó el cambio auditado, enlazado por actor_id; no nulo
     * @param accion acción histórica persistida que explica el cambio de reserva (RN-15); no nula
     * @param estadoAnterior estado previo o null en la creación
     * @param estadoNuevo estado posterior al cambio auditado; no nulo
     * @param datosAnteriores datos previos o null en la creación
     * @param datosNuevos instantánea JSONB de los valores nuevos; mapa no nulo y no modificable, con copia
     * superficial
     * @param motivo motivo opcional
     * @param excepcional marca de excepción administrativa RN-08, distinguible en la auditoría RN-15
     * @param creadoEn instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo
     * @throws NullPointerException si falta un dato obligatorio.
     */
    public AuditoriaReserva(Reserva reserva, Usuario actor, AccionAuditoria accion, EstadoReserva estadoAnterior, EstadoReserva estadoNuevo, Map<String, Object> datosAnteriores, Map<String, Object> datosNuevos, String motivo, boolean excepcional, Instant creadoEn) {
        this.reserva = Objects.requireNonNull(reserva, "reserva");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.accion = Objects.requireNonNull(accion, "accion");
        this.estadoNuevo = Objects.requireNonNull(estadoNuevo, "estadoNuevo");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        this.estadoAnterior = estadoAnterior;
        this.datosAnteriores = datosAnteriores == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(datosAnteriores));
        this.datosNuevos = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(datosNuevos, "datosNuevos")));
        this.motivo = motivo;
        this.excepcional = excepcional;
    }

    /** {@return identificador persistente generado por V1; nulo hasta persistir la entidad} */
    public Long getId() {
        return id;
    }

    /** {@return reserva histórica asociada por reserva_id; no nula} */
    public Reserva getReserva() {
        return reserva;
    }

    /** {@return usuario que ejecutó el cambio auditado, enlazado por actor_id; no nulo} */
    public Usuario getActor() {
        return actor;
    }

    /** {@return acción histórica persistida que explica el cambio de reserva (RN-15); no nula} */
    public AccionAuditoria getAccion() {
        return accion;
    }

    /** {@return estado anterior al cambio; nulo en la creación de una reserva} */
    public EstadoReserva getEstadoAnterior() {
        return estadoAnterior;
    }

    /** {@return estado posterior al cambio auditado; no nulo} */
    public EstadoReserva getEstadoNuevo() {
        return estadoNuevo;
    }

    /** {@return instantánea JSONB de los valores anteriores; mapa no modificable, nulo en la creación} */
    public Map<String, Object> getDatosAnteriores() {
        return datosAnteriores == null ? null : Collections.unmodifiableMap(datosAnteriores);
    }

    /**
     * {@return instantánea JSONB de los valores nuevos; mapa no nulo y no modificable, con copia
     * superficial}
     */
    public Map<String, Object> getDatosNuevos() {
        return Collections.unmodifiableMap(datosNuevos);
    }

    /**
     * {@return explicación del cambio; puede ser nula salvo cuando la política exige justificar la
     * excepción}
     */
    public String getMotivo() {
        return motivo;
    }

    /** {@return marca de excepción administrativa RN-08, distinguible en la auditoría RN-15} */
    public boolean isExcepcional() {
        return excepcional;
    }

    /**
     * {@return instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo}
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }
}
