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
 * Registro de un cambio de reserva.
 *
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

    /** Constructor reservado a JPA. */
    protected AuditoriaReserva() {
    }

    /**
     * Crea registro de un cambio de reserva.
     * @param reserva reserva
     * @param actor actor
     * @param accion accion
     * @param estadoAnterior estado previo o null en la creación
     * @param estadoNuevo estadoNuevo
     * @param datosAnteriores datos previos o null en la creación
     * @param datosNuevos datosNuevos
     * @param motivo motivo opcional
     * @param excepcional excepcional
     * @param creadoEn instante aportado por el servicio
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

    /**
     * Consulta id.
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * Consulta reserva.
     * @return reserva
     */
    public Reserva getReserva() {
        return reserva;
    }

    /**
     * Consulta actor.
     * @return actor
     */
    public Usuario getActor() {
        return actor;
    }

    /**
     * Consulta accion.
     * @return accion
     */
    public AccionAuditoria getAccion() {
        return accion;
    }

    /**
     * Consulta estadoAnterior.
     * @return estadoAnterior
     */
    public EstadoReserva getEstadoAnterior() {
        return estadoAnterior;
    }

    /**
     * Consulta estadoNuevo.
     * @return estadoNuevo
     */
    public EstadoReserva getEstadoNuevo() {
        return estadoNuevo;
    }

    /**
     * Consulta datosAnteriores.
     * @return datosAnteriores
     */
    public Map<String, Object> getDatosAnteriores() {
        return datosAnteriores == null ? null : Collections.unmodifiableMap(datosAnteriores);
    }

    /**
     * Consulta datosNuevos.
     * @return datosNuevos
     */
    public Map<String, Object> getDatosNuevos() {
        return Collections.unmodifiableMap(datosNuevos);
    }

    /**
     * Consulta motivo.
     * @return motivo
     */
    public String getMotivo() {
        return motivo;
    }

    /**
     * Consulta excepcional.
     * @return excepcional
     */
    public boolean isExcepcional() {
        return excepcional;
    }

    /**
     * Consulta creadoEn.
     * @return creadoEn
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }
}
