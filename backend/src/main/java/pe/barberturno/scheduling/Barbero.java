package pe.barberturno.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Barbero vinculado a un usuario.
 *
 * @author Sohnny Walter Lima Infanzón
 * @version 1.0
 */
@Entity
@Table(name = "barbero")
public class Barbero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "especialidad", nullable = false, length = 100)
    private String especialidad;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Constructor reservado a JPA. */
    protected Barbero() {
    }

    /**
     * Crea barbero vinculado a un usuario.
     * @param usuario usuario
     * @param especialidad especialidad
     * @param creadoEn instante aportado por el servicio
     * @throws NullPointerException si falta un dato obligatorio.
     */
    public Barbero(Usuario usuario, String especialidad, Instant creadoEn) {
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.especialidad = Objects.requireNonNull(especialidad, "especialidad");
        this.activo = true;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        this.actualizadoEn = creadoEn;
    }

    /**
     * Desactiva el perfil de barbero.
     * @param actualizadoEn instante del cambio
     */
    public void desactivar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = false;
    }

    /**
     * Activa el perfil de barbero.
     * @param actualizadoEn instante del cambio
     */
    public void activar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = true;
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
     * Consulta especialidad.
     * @return especialidad
     */
    public String getEspecialidad() {
        return especialidad;
    }

    /**
     * Consulta activo.
     * @return activo
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Consulta creadoEn.
     * @return creadoEn
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }

    /**
     * Consulta actualizadoEn.
     * @return actualizadoEn
     */
    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
