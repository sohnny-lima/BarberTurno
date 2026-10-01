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
 * Aviso interno de un cambio de reserva.
 *
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

    /** Constructor reservado a JPA. */
    protected Notificacion() {
    }

    /**
     * Crea aviso interno de un cambio de reserva.
     * @param usuario usuario
     * @param reserva reserva
     * @param tipo tipo
     * @param mensaje mensaje
     * @param creadoEn instante aportado por el servicio
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
     * Marca el aviso como leído.
     */
    public void marcarLeida() {
        leida = true;
    }

    /**
     * Consulta id.
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * Consulta usuario.
     * @return usuario
     */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * Consulta reserva.
     * @return reserva
     */
    public Reserva getReserva() {
        return reserva;
    }

    /**
     * Consulta tipo.
     * @return tipo
     */
    public AccionAuditoria getTipo() {
        return tipo;
    }

    /**
     * Consulta mensaje.
     * @return mensaje
     */
    public String getMensaje() {
        return mensaje;
    }

    /**
     * Consulta leida.
     * @return leida
     */
    public boolean isLeida() {
        return leida;
    }

    /**
     * Consulta creadoEn.
     * @return creadoEn
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }
}
