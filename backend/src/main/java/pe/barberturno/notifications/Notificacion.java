package pe.barberturno.notifications;

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
import pe.barberturno.users.Usuario;
import pe.barberturno.reservations.Reserva;
import pe.barberturno.audit.AccionAuditoria;

/**
 * Aviso interno RF-16 de un cambio de reserva; el servicio lo persiste con la auditoría según RN-15.
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 12)
    private AccionAuditoria tipo;

    @Column(name = "mensaje", nullable = false, length = 300)
    private String mensaje;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected Notificacion() {
    }

    /**
     * Prepara un aviso interno no leído RF-16; el servicio selecciona destinatarios RN-15 y lo persiste con la
     * reserva y auditoría.
     * @param usuario destinatario no nulo seleccionado por el servicio según RN-15
     * @param reserva reserva histórica asociada por reserva_id; no nula
     * @param tipo acción que originó el aviso interno, persistida en tipo; no nula
     * @param mensaje texto público del aviso, no nulo, de hasta 300 caracteres y sin secretos
     * @param creadoEn instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo
     * @throws NullPointerException si falta un dato obligatorio.
     */
    public Notificacion(Usuario usuario, Reserva reserva, AccionAuditoria tipo, String mensaje, Instant creadoEn) {
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.reserva = Objects.requireNonNull(reserva, "reserva");
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.mensaje = Objects.requireNonNull(mensaje, "mensaje");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
    }

    /**
     * Registra lectura de forma idempotente; el servicio comprueba previamente propiedad del aviso (RF-16).
     */
    public void marcarLeida() {
        leida = true;
    }

    /**
     * Identificador persistente generado por V1; nulo hasta persistir la entidad.
     * @return identificador persistente generado por V1; nulo hasta persistir la entidad.
     */
    public Long getId() {
        return id;
    }

    /**
     * Destinatario enlazado por usuario_id; no nulo y seleccionado por el servicio según RN-15.
     * @return destinatario del aviso, no nulo; la API limita lectura a su propietario
     */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * Reserva histórica asociada por reserva_id; no nula.
     * @return reserva histórica asociada por reserva_id; no nula.
     */
    public Reserva getReserva() {
        return reserva;
    }

    /**
     * Acción que originó el aviso interno, persistida en tipo; no nula.
     * @return acción que originó el aviso interno, persistida en tipo; no nula.
     */
    public AccionAuditoria getTipo() {
        return tipo;
    }

    /**
     * Texto público del aviso, no nulo, de hasta 300 caracteres y sin secretos.
     * @return texto público del aviso, no nulo, de hasta 300 caracteres y sin secretos.
     */
    public String getMensaje() {
        return mensaje;
    }

    /**
     * Marca persistida de lectura del destinatario; inicialmente false.
     * @return marca persistida de lectura del destinatario; inicialmente false.
     */
    public boolean isLeida() {
        return leida;
    }

    /**
     * Instante absoluto de creación, aportado por el Clock del servicio y persistido como timestamptz; no nulo.
     * @return instante absoluto de creación, aportado por el Clock del servicio y persistido como timestamptz;
     * no nulo.
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }
}
