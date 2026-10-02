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
 * Perfil de atención vinculado a una cuenta del personal (RN-26); su estado controla disponibilidad según RN-06.
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

    /**
     * Constructor exclusivo de JPA para hidratar la entidad; no se usa desde el código de aplicación.
     */
    protected Barbero() {
    }

    /**
     * Crea barbero vinculado a un usuario.
     * @param usuario cuenta vinculada por usuario_id; no nula; el perfil de barbero también puede pertenecer a
     * ADMIN
     * @param especialidad texto descriptivo de hasta 100 caracteres; no restringe los servicios que puede
     * atender (C-12)
     * @param creadoEn instante absoluto de creación, aportado por el Clock del servicio y persistido como
     * timestamptz; no nulo
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
     * Retira perfil de nuevas reservas RN-06 y conserva fila histórica RN-16.
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void desactivar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = false;
    }

    /**
     * Habilita perfil para nuevas reservas RN-06; no cambia el estado de la cuenta vinculada.
     * @param actualizadoEn instante absoluto del último cambio, aportado por el Clock del servicio; no nulo
     * @throws NullPointerException si un dato indicado como no nulo está ausente
     */
    public void activar(Instant actualizadoEn) {
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        activo = true;
    }

    /**
     * Identificador persistente generado por V1; nulo hasta persistir la entidad.
     * @return identificador persistente generado por V1; nulo hasta persistir la entidad.
     */
    public Long getId() {
        return id;
    }

    /**
     * Cuenta vinculada por usuario_id; no nula; el perfil de barbero también puede pertenecer a ADMIN.
     * @return cuenta vinculada por usuario_id; no nula; el perfil de barbero también puede pertenecer a ADMIN.
     */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * Texto descriptivo de hasta 100 caracteres; no restringe los servicios que puede atender (C-12).
     * @return texto descriptivo de hasta 100 caracteres; no restringe los servicios que puede atender (C-12).
     */
    public String getEspecialidad() {
        return especialidad;
    }

    /**
     * Estado de habilitación lógica; false conserva la fila histórica según RN-16.
     * @return estado de habilitación lógica; false conserva la fila histórica según RN-16.
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Instante absoluto de creación, aportado por el Clock del servicio y persistido como timestamptz; no nulo.
     * @return instante absoluto de creación, aportado por el Clock del servicio y persistido como timestamptz;
     * no nulo.
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }

    /**
     * Instante absoluto del último cambio, aportado por el Clock del servicio; no nulo.
     * @return instante absoluto del último cambio, aportado por el Clock del servicio; no nulo.
     */
    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
