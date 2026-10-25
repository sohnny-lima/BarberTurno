package pe.barberturno.platform;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Acreditación de un solo uso; conserva únicamente el hash del secreto y su ámbito inmutable.
 * @author Sohnny Walter Lima Infanzón
 * @version 2.0
 */
@Entity
@Table(name = "invitacion")
public class Invitacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "negocio_id", nullable = false, updatable = false)
    private long negocioId;
    @Column(nullable = false, length = 254, updatable = false)
    private String correo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12, updatable = false)
    private RolNegocio rol;
    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;
    @Column(nullable = false, length = 10)
    private String estado;
    @Column(name = "expira_en", nullable = false, updatable = false)
    private Instant expiraEn;
    @Column(name = "invitada_por", nullable = false, updatable = false)
    private long invitadaPor;
    @Column(name = "aceptada_por")
    private Long aceptadaPor;
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Hidrata exclusivamente desde JPA sin construir secretos. */
    protected Invitacion() { }

    Invitacion(long negocioId, String correo, RolNegocio rol, byte[] hash, long actor, Instant ahora) {
        this.negocioId = negocioId; this.correo = correo; this.rol = rol; this.tokenHash = hash.clone();
        this.invitadaPor = actor; this.creadoEn = ahora; this.actualizadoEn = ahora;
        this.expiraEn = ahora.plus(java.time.Duration.ofDays(7)); this.estado = "PENDIENTE";
    }

    boolean vigente(Instant ahora) { return "PENDIENTE".equals(estado) && ahora.isBefore(expiraEn); }
    void aceptar(long usuarioId, Instant ahora) { estado = "ACEPTADA"; aceptadaPor = usuarioId; actualizadoEn = ahora; }
    void revocar(Instant ahora) { estado = "REVOCADA"; actualizadoEn = ahora; }
    Long id() { return id; }
    long negocioId() { return negocioId; }
    String correo() { return correo; }
    RolNegocio rol() { return rol; }
    Instant expiraEn() { return expiraEn; }
}
